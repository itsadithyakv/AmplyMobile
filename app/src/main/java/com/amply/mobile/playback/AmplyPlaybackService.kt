package com.amply.mobile.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.amply.mobile.AmplyApplication
import com.amply.mobile.MainActivity
import com.amply.mobile.R
import com.amply.mobile.data.local.PlayEventEntity
import com.amply.mobile.widget.AmplyWidgetProvider
import com.amply.mobile.widget.NowPlayingSnapshot
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the player for the whole app. Everything that has to keep working with the screen off or
 * the UI closed lives here rather than in the activity: the media notification and lock-screen
 * controls (Media3 builds both from the session), headset/Bluetooth/car buttons, audio focus,
 * queue persistence, play/skip stats, fades, the sleep timer and the home-screen widget.
 *
 * It is a [MediaLibraryService] so Android Auto, the Assistant and the system's "resume playback"
 * card can browse the library and start music without the app being open.
 */
@UnstableApi
class AmplyPlaybackService : MediaLibraryService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var queueStore: QueueStore
    private lateinit var tree: LibraryTree
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listens = ListenTracker()
    private var equalizer: Equalizer? = null
    private var equalizerSessionId = C.AUDIO_SESSION_ID_UNSET
    private var tickJob: Job? = null
    private var favoriteJob: Job? = null
    private var currentFavorite = false
    private var consecutiveErrors = 0

    /** True while the song that just started should fade in (it followed a faded-out song). */
    private var fadeInActive = false

    private val container get() = (application as AmplyApplication).container

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PLAYBACK_PREFS, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        queueStore = container.queueStore
        tree = LibraryTree(container.database)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Let ExoPlayer manage audio focus: pause for calls and other players, duck for
                // navigation prompts and notifications, resume when focus comes back.
                true,
            )
            // Pause when headphones are unplugged or a Bluetooth headset disconnects.
            .setHandleAudioBecomingNoisy(true)
            // Keep the CPU awake while playing with the screen off. Without this the playback
            // thread can be starved on some devices and music stutters or stops in the pocket.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PlayerEvents())

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_stat_amply) },
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity())
            .setMediaButtonPreferences(mediaButtons())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        persistQueue()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        equalizer?.release()
        equalizer = null
        SleepTimer.set(SleepTimerState.Off)
        publishWidget(forcePaused = true)
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null || key.startsWith("eq") || key == "equalizerEnabled") {
            player.audioSessionId.takeIf { it != C.AUDIO_SESSION_ID_UNSET }?.let(::configureEqualizer)
        }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listens.onPlayingChanged(isPlaying, SystemClock.elapsedRealtime())
            if (isPlaying) {
                consecutiveErrors = 0
                startTicker()
            } else {
                tickJob?.cancel()
                persistQueue()
            }
            publishWidget()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val now = SystemClock.elapsedRealtime()
            finishListen(userSkipped = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, nowMs = now)
            // Only fade in after an automatic transition; a song the user picked starts at full volume.
            fadeInActive = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && fadeSettingMs() > 0L
            listens.start(mediaItem?.mediaId?.toLongOrNull(), player.duration.coerceAtLeast(0L), now, player.isPlaying)
            applyVolume()
            watchFavorite(mediaItem?.mediaId?.toLongOrNull())
            persistQueue()
            publishWidget()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                SleepTimer.state.value == SleepTimerState.EndOfSong
            ) {
                player.pauseAtEndOfMediaItems = false
                SleepTimer.set(SleepTimerState.Off)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) listens.setDuration(player.duration)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) publishWidget()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) persistQueue()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            refreshMediaButtons()
            persistQueue()
        }

        override fun onRepeatModeChanged(repeatMode: Int) = persistQueue()

        override fun onAudioSessionIdChanged(audioSessionId: Int) = configureEqualizer(audioSessionId)

        override fun onPlayerError(error: PlaybackException) {
            // A missing or unreadable file should not stop the whole queue, but a queue where
            // nothing can be played must not loop through itself forever either.
            consecutiveErrors++
            if (player.hasNextMediaItem() && consecutiveErrors < player.mediaItemCount) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                player.pause()
                consecutiveErrors = 0
            }
            publishWidget()
        }
    }

    /**
     * One loop while playing: fades, the sleep timer, periodic queue saves and widget progress.
     * It ticks fast only inside a fade window and once a second otherwise.
     */
    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var sinceSave = 0L
            var sinceWidget = 0L
            while (isActive) {
                val step = if (inFadeWindow()) FAST_TICK_MS else SLOW_TICK_MS
                delay(step)
                if (!player.isPlaying) continue
                val timer = SleepTimer.state.value
                if (timer is SleepTimerState.At && timer.remainingMs(SystemClock.elapsedRealtime()) <= 0L) {
                    player.pause()
                    SleepTimer.set(SleepTimerState.Off)
                }
                applyVolume()
                sinceSave += step
                sinceWidget += step
                if (sinceSave >= SAVE_EVERY_MS) {
                    sinceSave = 0L
                    persistQueue()
                }
                if (sinceWidget >= WIDGET_EVERY_MS) {
                    sinceWidget = 0L
                    publishWidget()
                }
            }
        }
    }

    private fun fadeSettingMs(): Long = (prefs.getFloat("crossfadeSeconds", 0f) * 1000f).toLong()

    private fun knownDuration(): Long = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

    private fun inFadeWindow(): Boolean {
        val timer = SleepTimer.state.value
        if (timer is SleepTimerState.At && timer.remainingMs(SystemClock.elapsedRealtime()) < SLEEP_FADE_OUT_MS + SLOW_TICK_MS) return true
        val duration = knownDuration()
        val fade = effectiveFadeMs(fadeSettingMs(), duration)
        if (fade <= 0L) return false
        val position = player.currentPosition
        return (fadeInActive && position < fade) || (duration - position < fade + SLOW_TICK_MS)
    }

    private fun applyVolume() {
        val duration = knownDuration()
        val fade = effectiveFadeMs(fadeSettingMs(), duration)
        // Fading out only makes sense when another song follows straight away.
        val fadeOut = player.hasNextMediaItem() && player.repeatMode != Player.REPEAT_MODE_ONE && !player.pauseAtEndOfMediaItems
        var volume = transitionFadeVolume(player.currentPosition, duration, fade, fadeInActive, fadeOut)
        val timer = SleepTimer.state.value
        if (timer is SleepTimerState.At) volume *= sleepFadeVolume(timer.remainingMs(SystemClock.elapsedRealtime()))
        if (player.volume != volume) player.volume = volume
    }

    private fun finishListen(userSkipped: Boolean, nowMs: Long) {
        val songId = listens.songId ?: return
        val listened = listens.listenedMs(nowMs)
        val outcome = classifyListen(listened, listens.durationMs, userSkipped)
        if (outcome == ListenOutcome.Ignore) return
        val at = System.currentTimeMillis()
        scope.launch {
            if (outcome == ListenOutcome.Play) {
                container.libraryRepository.recordPlay(songId, listened)
            } else {
                container.libraryRepository.recordSkip(songId)
            }
            // The dated log behind Stats and Encore; recorded on every plan.
            container.database.playEventDao().insert(
                PlayEventEntity(songId = songId, atMs = at, listenedMs = listened, skipped = outcome == ListenOutcome.Skip),
            )
        }
    }

    private fun persistQueue() {
        val count = player.mediaItemCount
        if (count == 0) return
        val ids = (0 until count).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        // Files opened from another app are not in the library and cannot be restored later.
        if (ids.size != count) return
        val snapshot = QueueSnapshot(
            songIds = ids,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> "one"
                Player.REPEAT_MODE_ALL -> "all"
                else -> "off"
            },
        )
        scope.launch { queueStore.save(snapshot) }
    }

    private fun watchFavorite(songId: Long?) {
        favoriteJob?.cancel()
        currentFavorite = false
        refreshMediaButtons()
        if (songId == null) return
        favoriteJob = scope.launch {
            container.database.songDao().observeSong(songId)
                .map { it?.favorite == true }
                .distinctUntilChanged()
                .collect { favorite ->
                    currentFavorite = favorite
                    refreshMediaButtons()
                }
        }
    }

    /** Heart and shuffle buttons for the notification, lock screen, system media controls and cars. */
    private fun mediaButtons(): List<CommandButton> {
        val shuffle = ::player.isInitialized && player.shuffleModeEnabled
        return listOf(
            CommandButton.Builder(if (currentFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                .setDisplayName(getString(if (currentFavorite) R.string.media_unlike else R.string.media_like))
                .setSessionCommand(PlaybackCommands.toggleFavorite)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName(getString(if (shuffle) R.string.media_shuffle_off else R.string.media_shuffle_on))
                .setSessionCommand(PlaybackCommands.toggleShuffle)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    private fun refreshMediaButtons() {
        session?.setMediaButtonPreferences(mediaButtons())
    }

    private fun setSleepTimer(minutes: Int) {
        when {
            minutes == PlaybackCommands.SLEEP_END_OF_SONG -> {
                player.pauseAtEndOfMediaItems = true
                SleepTimer.set(SleepTimerState.EndOfSong)
            }
            minutes > 0 -> {
                player.pauseAtEndOfMediaItems = false
                val total = minutes * 60_000L
                SleepTimer.set(SleepTimerState.At(SystemClock.elapsedRealtime() + total, total))
                if (player.isPlaying) startTicker()
            }
            else -> {
                player.pauseAtEndOfMediaItems = false
                SleepTimer.set(SleepTimerState.Off)
            }
        }
        applyVolume()
    }

    private fun toggleFavorite() {
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val favorite = !currentFavorite
        scope.launch { container.database.songDao().setFavorite(songId, favorite) }
    }

    private fun publishWidget(forcePaused: Boolean = false) {
        val item = player.currentMediaItem
        val metadata = player.mediaMetadata
        val snapshot = item?.let {
            NowPlayingSnapshot(
                songId = it.mediaId.toLongOrNull(),
                title = (metadata.title ?: it.mediaMetadata.title)?.toString().orEmpty(),
                artist = (metadata.artist ?: it.mediaMetadata.artist)?.toString().orEmpty(),
                artworkUri = (metadata.artworkUri ?: it.mediaMetadata.artworkUri)?.toString(),
                artworkData = metadata.artworkData,
                audioUri = it.localConfiguration?.uri?.toString(),
                isPlaying = !forcePaused && player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = knownDuration(),
            )
        }
        AmplyWidgetProvider.publish(applicationContext, snapshot)
    }

    private fun configureEqualizer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        val enabled = prefs.getBoolean("equalizerEnabled", false)
        runCatching {
            // An Equalizer is bound to one audio session; make a new one when the session changes
            // instead of keeping one attached to a session that no longer exists.
            val eq = equalizer?.takeIf { equalizerSessionId == audioSessionId } ?: run {
                equalizer?.release()
                Equalizer(0, audioSessionId).also {
                    equalizer = it
                    equalizerSessionId = audioSessionId
                }
            }
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            val bands = eq.numberOfBands.toInt().coerceAtLeast(1)
            val bass = prefs.getFloat("eqBass", 0.5f)
            val mid = prefs.getFloat("eqMid", 0.5f)
            val treble = prefs.getFloat("eqTreble", 0.5f)
            for (band in 0 until bands) {
                val value = when {
                    band < bands / 3 -> bass
                    band < (bands * 2) / 3 -> mid
                    else -> treble
                }
                eq.setBandLevel(band.toShort(), (min + ((max - min) * value)).toInt().toShort())
            }
            eq.enabled = enabled
        }
    }

    /** Tapping the notification or lock-screen controls opens Now Playing, not the home tab. */
    private fun sessionActivity(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .apply { PlaybackCommands.all.forEach { add(it) } }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(mediaButtons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.ACTION_TOGGLE_FAVORITE -> toggleFavorite()
                PlaybackCommands.ACTION_TOGGLE_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                PlaybackCommands.ACTION_SET_SLEEP_TIMER -> setSleepTimer(args.getInt(PlaybackCommands.EXTRA_SLEEP_MINUTES, 0))
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // The system resumption UI asks for a "recent" root after a reboot; answer with the last song.
            val root = if (params?.isRecent == true) tree.recentRootItem() else tree.rootItem()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            LibraryResult.ofItemList(tree.children(parentId, page, pageSize), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            session.notifySearchResultChanged(browser, query, tree.search(query).size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            LibraryResult.ofItemList(tree.search(query).map { it.toMediaItem() }.page(page, pageSize), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.flatMap { tree.resolve(it) }.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val expanded = mediaItems.singleOrNull()?.let { tree.expandSelection(it) }
            if (expanded != null) {
                MediaSession.MediaItemsWithStartPosition(expanded.first, expanded.second, C.TIME_UNSET)
            } else {
                val resolved = mediaItems.flatMap { tree.resolve(it) }
                val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
                MediaSession.MediaItemsWithStartPosition(resolved, index, startPositionMs)
            }
        }

        /**
         * Called when something presses play with nothing loaded: a headset or car button after the
         * app was killed, the system "resume" card, the widget, or our own UI after a restart.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val restored = queueStore.restore() ?: throw UnsupportedOperationException("Nothing to resume")
            player.shuffleModeEnabled = restored.shuffle
            player.repeatMode = when (restored.repeatMode) {
                "one" -> Player.REPEAT_MODE_ONE
                "all" -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            MediaSession.MediaItemsWithStartPosition(
                restored.songs.map { it.toMediaItem() },
                restored.startIndex,
                restored.positionMs,
            )
        }
    }

    companion object {
        const val PLAYBACK_PREFS = "amply_playback_settings"
        private const val SESSION_ID = "amply_media_session"
        private const val FAST_TICK_MS = 100L
        private const val SLOW_TICK_MS = 1_000L
        private const val SAVE_EVERY_MS = 15_000L
        private const val WIDGET_EVERY_MS = 10_000L
    }
}
