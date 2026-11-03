package com.amply.mobile

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amply.mobile.data.local.toDomain
import com.amply.mobile.domain.ArtistInfo
import com.amply.mobile.domain.CachedLyrics
import com.amply.mobile.domain.LyricsCandidate
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.RepeatMode
import com.amply.mobile.domain.Song
import com.amply.mobile.playback.SleepTimerState
import com.amply.mobile.playlist.RulePlaylist
import com.amply.mobile.playlist.evaluateRules
import com.amply.mobile.playlist.toEntity
import com.amply.mobile.playlist.toRulePlaylist
import com.amply.mobile.pro.ProFeature
import com.amply.mobile.pro.ProState
import com.amply.mobile.pro.limitSettings
import com.amply.mobile.report.BugReporter
import com.amply.mobile.report.ReportDraft
import com.amply.mobile.report.ReportResult
import com.amply.mobile.stats.Encore
import com.amply.mobile.stats.ListeningStats
import com.amply.mobile.stats.MIN_ENCORE_PLAYS
import com.amply.mobile.stats.StatsRange
import com.amply.mobile.stats.computeEncore
import com.amply.mobile.stats.computeListeningStats
import com.amply.mobile.worker.MetadataEnrichmentWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab {
    Home,
    Search,
    Library,
    Playlists,
    NowPlaying,
    Settings,
    Stats,
}

/** One step of in-app navigation, so the system back gesture can walk back through it. */
data class NavEntry(val tab: AppTab, val playlistId: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AmplyApplication).container
    private val library = container.libraryRepository
    private val playlistsRepository = container.playlistRepository
    private val lyricsRepository = container.lyricsRepository
    private val metadataRepository = container.metadataRepository
    private val settingsRepository = container.settingsRepository
    private val playback = container.playbackConnection

    val songs: StateFlow<List<Song>> = library.songs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val songsById: StateFlow<Map<Long, Song>> = songs
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val playlists: StateFlow<List<Playlist>> = playlistsRepository.playlists.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.amply.mobile.domain.AppSettings(),
    )

    val playbackState = playback.state

    // Amply Pro -----------------------------------------------------------------------------

    val proState: StateFlow<ProState> = container.proStore.state

    val isPro: StateFlow<Boolean> = combine(proState, settings) { pro, s -> AmplyApplication.isPro(pro.owned, s.debugPro) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** What the app runs on: the saved settings, limited to what the plan allows. */
    val effectiveSettings: StateFlow<com.amply.mobile.domain.AppSettings> = combine(settings, isPro) { s, pro -> limitSettings(s, pro) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.amply.mobile.domain.AppSettings())

    private val _paywall = MutableStateFlow<ProFeature?>(null)

    /** Non-null while the Pro sheet is open; the feature that opened it is listed first. */
    val paywall: StateFlow<ProFeature?> = _paywall

    fun showPaywall(feature: ProFeature) {
        _paywall.value = feature
    }

    fun dismissPaywall() {
        _paywall.value = null
    }

    fun buyPro(activity: android.app.Activity) = container.proStore.buy(activity)
    fun restorePro() = container.proStore.refresh(userAsked = true)
    fun clearProMessage() = container.proStore.clearMessage()

    /** Runs [action] on Pro, otherwise opens the Pro sheet for [feature]. */
    fun requirePro(feature: ProFeature, action: () -> Unit) {
        if (isPro.value) action() else showPaywall(feature)
    }

    fun setAccent(key: String) = requirePro(ProFeature.Themes) { viewModelScope.launch { settingsRepository.setAccent(key) } }

    fun setTrueBlack(enabled: Boolean) = requirePro(ProFeature.Themes) { viewModelScope.launch { settingsRepository.setTrueBlack(enabled) } }

    fun setDebugPro(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDebugPro(enabled) }
    }

    fun setEqPreset(name: String?) {
        viewModelScope.launch { settingsRepository.setEqPreset(name) }
    }

    // Stats and Encore ------------------------------------------------------------------------

    private val _statsRange = MutableStateFlow(StatsRange.Week)
    val statsRange: StateFlow<StatsRange> = _statsRange

    fun setStatsRange(range: StatsRange) {
        if (range == StatsRange.Week) _statsRange.value = range else requirePro(ProFeature.FullStats) { _statsRange.value = range }
    }

    private val recentEvents = container.database.playEventDao()
        .observeBetween(System.currentTimeMillis() - 31L * 86_400_000L, Long.MAX_VALUE)

    val stats: StateFlow<ListeningStats?> = combine(recentEvents, songsById, _statsRange) { events, byId, range ->
        computeListeningStats(events, byId, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val yearStartMs = java.time.LocalDate.now().withDayOfYear(1)
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** This year's plays so far, for the Encore teaser. */
    val yearPlays: StateFlow<Int> = container.database.playEventDao().observePlaysBetween(yearStartMs, Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _encore = MutableStateFlow<Encore?>(null)
    val encore: StateFlow<Encore?> = _encore

    fun openEncore() = requirePro(ProFeature.Encore) {
        viewModelScope.launch {
            val events = container.database.playEventDao().between(yearStartMs, Long.MAX_VALUE)
            val result = computeEncore(java.time.LocalDate.now().year, events, songsById.value)
            _encore.value = result
            if (result == null) {
                _message.value = "Play ${(MIN_ENCORE_PLAYS - yearPlays.value).coerceAtLeast(1)} more songs to unlock your Encore."
            }
        }
    }

    fun closeEncore() {
        _encore.value = null
    }

    fun playEncoreMix(encore: Encore) {
        val byId = songsById.value
        val queue = encore.mixSongIds.mapNotNull(byId::get)
        if (queue.isNotEmpty()) playback.playQueue(queue)
        _message.value = "Playing your Encore mix"
    }

    // Smart (rule) playlists ------------------------------------------------------------------

    val rulePlaylists: StateFlow<List<RulePlaylist>> = container.database.rulePlaylistDao().observeAll()
        .map { list -> list.mapNotNull { it.toRulePlaylist() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Each rule playlist with its songs, recomputed whenever the library or play counts change. */
    val ruleResults: StateFlow<List<Pair<RulePlaylist, List<Song>>>> = combine(rulePlaylists, songs) { rules, all ->
        rules.map { it to evaluateRules(it, all) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveRulePlaylist(playlist: RulePlaylist) = requirePro(ProFeature.SmartRules) {
        viewModelScope.launch {
            container.database.rulePlaylistDao().upsert(playlist.toEntity())
            _message.value = "Saved ${playlist.name}"
        }
    }

    fun deleteRulePlaylist(id: String) {
        viewModelScope.launch { container.database.rulePlaylistDao().delete(id) }
    }

    fun playRuleSongs(songs: List<Song>, shuffle: Boolean = false) {
        if (songs.isNotEmpty()) playback.playQueue(songs, shuffle = shuffle)
    }

    // Bug reports -----------------------------------------------------------------------------

    suspend fun sendReport(draft: ReportDraft): ReportResult =
        BugReporter.send(getApplication(), draft, plan = if (isPro.value) "Pro" else "Free", librarySize = songs.value.size)

    val sleepTimer: StateFlow<SleepTimerState> = playback.sleepTimer

    /**
     * The library song that is playing, or a stand-in for a file opened from another app so the
     * mini player and Now Playing still show something.
     */
    val currentSong: StateFlow<Song?> = combine(songsById, playbackState) { byId, state ->
        state.currentSongId?.let(byId::get) ?: state.externalTitle?.let { externalSong(it, state.externalArtist) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Upcoming songs in play order, starting after the current one. */
    val upNext: StateFlow<List<QueueEntry>> = combine(songsById, playbackState) { byId, state ->
        state.queue.mapIndexedNotNull { position, id ->
            if (position <= state.queuePosition) null else byId[id]?.let { QueueEntry(position, it) }
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTab = MutableStateFlow(AppTab.Home)
    val selectedTab: StateFlow<AppTab> = _selectedTab

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId

    private val history = ArrayDeque<NavEntry>()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<Song>> = _searchQuery
        .flatMapLatest { library.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lyrics = MutableStateFlow<CachedLyrics?>(null)
    val lyrics: StateFlow<CachedLyrics?> = _lyrics

    private val _lyricsCandidates = MutableStateFlow<List<LyricsCandidate>>(emptyList())
    val lyricsCandidates: StateFlow<List<LyricsCandidate>> = _lyricsCandidates

    private val _artistInfo = MutableStateFlow<ArtistInfo?>(null)
    val artistInfo: StateFlow<ArtistInfo?> = _artistInfo

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            currentSong.map { it?.id }.distinctUntilChanged().collect {
                val song = currentSong.value
                if (song != null && !song.isExternal) {
                    loadCachedLyrics(song)
                    autoFetchLyrics(song)
                    fetchArtistInfo(song)
                }
            }
        }
    }

    // Navigation -------------------------------------------------------------------------------

    /** Bottom-bar tabs are roots: back from any of them goes to Home, then leaves the app. */
    fun selectTab(tab: AppTab) {
        history.clear()
        if (tab != AppTab.Home) history.addLast(NavEntry(AppTab.Home))
        navigate(NavEntry(tab))
    }

    /** Screens opened on top of the current one (playlist, settings, now playing) go on the back stack. */
    fun open(tab: AppTab) {
        if (_selectedTab.value == tab && _selectedPlaylistId.value == null) return
        history.addLast(current())
        navigate(NavEntry(tab))
    }

    fun openPlaylist(playlistId: String) {
        history.addLast(current())
        navigate(NavEntry(AppTab.Playlists, playlistId))
    }

    fun closePlaylist() {
        goBack()
    }

    /** Returns false when there is nowhere left to go back to, so the system can close the app. */
    fun goBack(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        navigate(previous)
        return true
    }

    private fun current() = NavEntry(_selectedTab.value, _selectedPlaylistId.value)

    private fun navigate(entry: NavEntry) {
        _selectedTab.value = entry.tab
        _selectedPlaylistId.value = entry.playlistId
        _canGoBack.value = history.isNotEmpty()
    }

    // Intents from outside the app ------------------------------------------------------------

    fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            MainActivity.ACTION_OPEN_NOW_PLAYING -> open(AppTab.NowPlaying)
            Intent.ACTION_VIEW -> intent.data?.let(::playExternal)
            android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ->
                playFromSearch(intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty())
        }
    }

    /**
     * Plays an audio file another app asked us to open. If it is already in the library we play the
     * library song (so stats and favourites work); otherwise it plays on its own, untracked.
     */
    private fun playExternal(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val known = withContext(Dispatchers.IO) { container.database.songDao().songByContentUri(uri.toString())?.toDomain() }
            if (known != null) {
                playback.playQueue(listOf(known))
            } else {
                val (title, artist) = withContext(Dispatchers.IO) { describeExternal(uri) }
                playback.playUri(uri, title, artist)
            }
            open(AppTab.NowPlaying)
        }
    }

    private fun describeExternal(uri: Uri): Pair<String, String?> {
        val app = getApplication<Application>()
        val retriever = MediaMetadataRetriever()
        val tagged = try {
            retriever.setDataSource(app, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) to
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } catch (_: Exception) {
            null to null
        } finally {
            runCatching { retriever.release() }
        }
        val displayName = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
        val title = tagged.first?.takeIf { it.isNotBlank() } ?: displayName?.substringBeforeLast('.') ?: "Audio file"
        return title to tagged.second?.takeIf { it.isNotBlank() }
    }

    private fun playFromSearch(query: String) {
        viewModelScope.launch {
            val all = songs.value.ifEmpty { library.allSongs() }
            val results = if (query.isBlank()) {
                all.shuffled()
            } else {
                val q = query.trim()
                all.filter {
                    it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) || it.effectiveGenre.contains(q, true)
                }
            }
            if (results.isEmpty()) {
                _message.value = "Nothing in your library matches \"$query\""
            } else {
                playback.playQueue(results)
                open(AppTab.NowPlaying)
            }
        }
    }

    // Library ---------------------------------------------------------------------------------

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _busy.value = true
            val count = runCatching { library.scanLibrary() }.getOrDefault(0)
            playlistsRepository.regenerateSmartPlaylists(library.allSongs())
            _busy.value = false
            _message.value = "Library scanned: $count songs"
        }
    }

    fun addMusicFolder(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val count = runCatching { library.scanFolder(uri) }
                .onFailure { _message.value = "Could not scan folder: ${it.message.orEmpty()}" }
                .getOrDefault(0)
            playlistsRepository.regenerateSmartPlaylists(library.allSongs())
            _busy.value = false
            if (count > 0) {
                selectTab(AppTab.Library)
                _message.value = "Added $count songs"
            } else {
                _message.value = "No supported audio files found"
            }
        }
    }

    fun regenerateSmartPlaylists() {
        viewModelScope.launch {
            playlistsRepository.regenerateSmartPlaylists(songs.value)
            _message.value = "Smart playlists refreshed"
        }
    }

    // Playback --------------------------------------------------------------------------------
    // Plays and skips are counted by the playback service once a song has actually been heard,
    // not here when a row is tapped.

    fun playSong(song: Song, source: List<Song> = songs.value) {
        val queue = source.ifEmpty { listOf(song) }
        val index = queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        playback.playQueue(queue, index)
    }

    fun playSingleThenDaily(song: Song) {
        val queue = if (playbackState.value.repeatMode == RepeatMode.One) {
            listOf(song)
        } else {
            dailyMixQueue(song, playlists.value, songs.value)
        }
        playback.playQueue(queue, 0)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        viewModelScope.launch {
            val queue = library.songsByIds(playlist.songIds)
            playback.playQueue(queue, shuffle = shuffle)
        }
    }

    fun shuffleAll() {
        val all = songs.value
        if (all.isNotEmpty()) playback.playQueue(all, startIndex = all.indices.random(), shuffle = true)
    }

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun setShuffle(enabled: Boolean) = playback.setShuffle(enabled)
    fun cycleRepeatMode() = playback.cycleRepeatMode()
    fun setRepeatMode(mode: RepeatMode) = playback.setRepeatMode(mode)

    fun playNext(song: Song) {
        playback.playNext(song)
        _message.value = "\"${song.title}\" plays next"
    }

    fun addToQueue(song: Song) {
        playback.addToQueue(song)
        _message.value = "Added \"${song.title}\" to the queue"
    }

    fun skipToQueuePosition(position: Int) = playback.skipToQueuePosition(position)
    fun removeFromQueue(position: Int) = playback.removeFromQueue(position)

    fun setSleepTimer(minutes: Int) = playback.setSleepTimer(minutes)

    fun toggleFavorite(song: Song) {
        if (song.isExternal) return
        viewModelScope.launch { library.toggleFavorite(song) }
    }

    // Playlists -------------------------------------------------------------------------------

    val customPlaylists: StateFlow<List<Playlist>> = playlists
        .map { list -> list.filter { it.type == PlaylistType.Custom } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPlaylist(name: String, firstSong: Song? = null) {
        viewModelScope.launch {
            val playlist = playlistsRepository.createCustomPlaylist(name, listOfNotNull(firstSong?.id))
            _message.value = if (firstSong != null) "Added to ${playlist.name}" else "Playlist created"
        }
    }

    fun addSongToPlaylist(playlist: Playlist, song: Song) {
        viewModelScope.launch {
            if (song.id in playlist.songIds) {
                _message.value = "Already in ${playlist.name}"
            } else {
                playlistsRepository.addSongToPlaylist(playlist, song.id)
                _message.value = "Added to ${playlist.name}"
            }
        }
    }

    fun removeSongFromPlaylist(playlist: Playlist, song: Song) {
        if (playlist.type != PlaylistType.Custom) return
        viewModelScope.launch {
            playlistsRepository.replacePlaylist(playlist.copy(songIds = playlist.songIds - song.id))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        if (playlist.type != PlaylistType.Custom) return
        viewModelScope.launch {
            playlistsRepository.deleteCustomPlaylist(playlist.id)
            goBack()
            _message.value = "Deleted ${playlist.name}"
        }
    }

    fun addCurrentSongToPlaylist(playlist: Playlist) {
        val song = currentSong.value ?: return
        addSongToPlaylist(playlist, song)
    }

    // Lyrics and metadata ---------------------------------------------------------------------

    fun loadCachedLyrics(song: Song) {
        viewModelScope.launch {
            _lyrics.value = lyricsRepository.cachedLyrics(song.id)
            _lyricsCandidates.value = emptyList()
        }
    }

    fun fetchLyrics(song: Song) {
        viewModelScope.launch {
            _busy.value = true
            _lyrics.value = lyricsRepository.loadOrFetch(song)
            _lyricsCandidates.value = lyricsRepository.findCandidates(song)
            _busy.value = false
        }
    }

    private fun autoFetchLyrics(song: Song) {
        viewModelScope.launch {
            if (settingsRepository.metadataFetchPaused()) return@launch
            if (lyricsRepository.cachedLyrics(song.id) == null) {
                _lyrics.value = lyricsRepository.loadOrFetch(song)
            }
        }
    }

    fun fetchArtistInfo(song: Song) {
        viewModelScope.launch {
            if (settingsRepository.metadataFetchPaused()) return@launch
            _artistInfo.value = metadataRepository.artistInfo(song)
        }
    }

    fun selectLyrics(song: Song, candidate: LyricsCandidate) {
        viewModelScope.launch {
            _lyrics.value = lyricsRepository.saveCandidate(song, candidate)
            _lyricsCandidates.value = emptyList()
        }
    }

    fun saveEditedLyrics(songId: Long, raw: String) {
        viewModelScope.launch {
            _lyrics.value = lyricsRepository.saveEditedLyrics(songId, raw)
            _message.value = "Lyrics saved"
        }
    }

    fun enrichUnknownGenres() {
        viewModelScope.launch {
            _busy.value = true
            songs.value
                .filter { it.effectiveGenre.equals("Unknown", true) || it.effectiveGenre.equals("Unknown Genre", true) }
                .take(20)
                .forEach { metadataRepository.enrichGenre(it) }
            _busy.value = false
            _message.value = "Genre cache updated"
        }
    }

    fun fetchAllMetadata() {
        _message.value = "Fetching metadata..."
        MetadataEnrichmentWorker.enqueueOneTime(getApplication())
    }

    // Settings --------------------------------------------------------------------------------
    // Sliders call these on every drag frame; each call is a Room upsert plus a SharedPreferences
    // write that the service listens to, so callers should commit on onValueChangeFinished.

    fun setMetadataPaused(paused: Boolean) {
        viewModelScope.launch { settingsRepository.setMetadataFetchPaused(paused) }
    }

    fun setDiscovery(value: Float) = requirePro(ProFeature.MixTuning) {
        viewModelScope.launch { settingsRepository.setDiscoveryIntensity(value) }
    }

    fun setRandomness(value: Float) = requirePro(ProFeature.MixTuning) {
        viewModelScope.launch { settingsRepository.setRandomnessIntensity(value) }
    }

    fun setGapless(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGaplessPlayback(enabled) }
    }

    fun setCrossfadeSeconds(value: Float) {
        if (value > 0.1f && !isPro.value) {
            showPaywall(ProFeature.Fade)
            return
        }
        viewModelScope.launch { settingsRepository.setCrossfadeSeconds(value) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEqualizerEnabled(enabled) }
    }

    fun setEqBass(value: Float) {
        viewModelScope.launch { settingsRepository.setEqBand("eqBass", value) }
    }

    fun setEqMid(value: Float) {
        viewModelScope.launch { settingsRepository.setEqBand("eqMid", value) }
    }

    fun setEqTreble(value: Float) {
        viewModelScope.launch { settingsRepository.setEqBand("eqTreble", value) }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun dailyMixQueue(song: Song, playlists: List<Playlist>, songs: List<Song>): List<Song> {
        val dailyMix = playlists.firstOrNull { playlist ->
            val text = "${playlist.id} ${playlist.name}".lowercase()
            "smart_daily_mix" in text || "daily mix" in text
        }
        val songsById = songs.associateBy { it.id }
        val dailySongs = dailyMix
            ?.songIds
            ?.mapNotNull { songsById[it] }
            .orEmpty()
            .filterNot { it.id == song.id }
        return listOf(song) + dailySongs
    }
}

data class QueueEntry(val position: Int, val song: Song)

/** Id used for the stand-in [Song] of a file opened from another app. */
const val EXTERNAL_SONG_ID = -1L

val Song.isExternal: Boolean get() = id == EXTERNAL_SONG_ID

private fun externalSong(title: String, artist: String?): Song = Song(
    id = EXTERNAL_SONG_ID,
    contentUri = "",
    filename = title,
    title = title,
    artist = artist ?: "Opened file",
    album = "",
    genre = "",
    manualGenre = null,
    durationMs = 0L,
    track = 0,
    year = null,
    albumId = null,
    artworkUri = null,
    addedAtSec = 0L,
    lastModifiedSec = 0L,
    playCount = 0,
    skipCount = 0,
    totalPlayMs = 0L,
    lastPlayedAtSec = null,
    favorite = false,
)
