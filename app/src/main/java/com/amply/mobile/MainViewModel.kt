package com.amply.mobile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amply.mobile.worker.MetadataEnrichmentWorker
import com.amply.mobile.domain.CachedLyrics
import com.amply.mobile.domain.ArtistInfo
import com.amply.mobile.domain.LyricsCandidate
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.RepeatMode
import com.amply.mobile.domain.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    Home,
    Search,
    Library,
    Playlists,
    NowPlaying,
    Settings,
}

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

    val currentSong = combine(songs, playbackState) { allSongs, state ->
        allSongs.firstOrNull { it.id == state.currentSongId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedTab = MutableStateFlow(AppTab.Home)
    val selectedTab: StateFlow<AppTab> = _selectedTab

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId

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
            currentSong.collect { song ->
                if (song != null) {
                    loadCachedLyrics(song)
                    autoFetchLyrics(song)
                    fetchArtistInfo(song)
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedPlaylistId.value = null
        _selectedTab.value = tab
    }

    fun openPlaylist(playlistId: String) {
        _selectedPlaylistId.value = playlistId
        _selectedTab.value = AppTab.Playlists
    }

    fun closePlaylist() {
        _selectedPlaylistId.value = null
    }

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
                _selectedTab.value = AppTab.Library
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

    fun playSong(song: Song, source: List<Song> = songs.value) {
        val queue = source.ifEmpty { listOf(song) }
        val index = queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        playback.playQueue(queue, index)
        viewModelScope.launch { library.recordPlay(song.id, 0L) }
    }

    fun playSingleThenDaily(song: Song) {
        val queue = if (playbackState.value.repeatMode == RepeatMode.One) {
            listOf(song)
        } else {
            dailyMixQueue(song, playlists.value, songs.value)
        }
        playback.playQueue(queue, 0)
        viewModelScope.launch { library.recordPlay(song.id, 0L) }
    }

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val queue = library.songsByIds(playlist.songIds)
            playback.playQueue(queue)
        }
    }

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun setShuffle(enabled: Boolean) = playback.setShuffle(enabled)
    fun cycleRepeatMode() = playback.cycleRepeatMode()
    fun setRepeatMode(mode: RepeatMode) = playback.setRepeatMode(mode)

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { library.toggleFavorite(song) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistsRepository.createCustomPlaylist(name)
            _message.value = "Playlist created"
        }
    }

    fun addCurrentSongToPlaylist(playlist: Playlist) {
        val song = currentSong.value ?: return
        viewModelScope.launch { playlistsRepository.addSongToPlaylist(playlist, song.id) }
    }

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
            if (lyricsRepository.cachedLyrics(song.id) == null) {
                _lyrics.value = lyricsRepository.loadOrFetch(song)
            }
        }
    }

    fun fetchArtistInfo(song: Song) {
        viewModelScope.launch {
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

    fun setMetadataPaused(paused: Boolean) {
        viewModelScope.launch { settingsRepository.setMetadataFetchPaused(paused) }
    }

    fun setDiscovery(value: Float) {
        viewModelScope.launch { settingsRepository.setDiscoveryIntensity(value) }
    }

    fun setRandomness(value: Float) {
        viewModelScope.launch { settingsRepository.setRandomnessIntensity(value) }
    }

    fun setGapless(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGaplessPlayback(enabled) }
    }

    fun setCrossfadeSeconds(value: Float) {
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
