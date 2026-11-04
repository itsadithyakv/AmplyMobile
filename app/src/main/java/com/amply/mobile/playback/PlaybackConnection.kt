package com.amply.mobile.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amply.mobile.domain.AmplyPlaybackState
import com.amply.mobile.domain.RepeatMode
import com.amply.mobile.domain.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The UI's handle on [AmplyPlaybackService]. It only sends commands and mirrors player state into
 * [state]; persistence, stats, fades and the sleep timer run in the service so they keep working
 * when nothing in the UI is alive.
 */
class PlaybackConnection(
    private val context: Context,
    private val queueStore: QueueStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectLock = Mutex()
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(AmplyPlaybackState())

    /** Changes on player events only (song, play/pause, queue, modes), never on position ticks. */
    val state: StateFlow<AmplyPlaybackState> = _state

    private val _position = MutableStateFlow(0L)

    /**
     * Live position, polled while playing and someone is watching. Kept apart from [state] so the
     * 250 ms tick only recomposes the progress bars that draw it, not the whole app.
     */
    val position: StateFlow<Long> = _position

    val sleepTimer: StateFlow<SleepTimerState> = SleepTimer.state

    init {
        scope.launch {
            val mediaController = connect()
            // Position is not pushed by the player, so poll it, but only while someone is watching.
            while (isActive) {
                if (mediaController.isPlaying && _position.subscriptionCount.value > 0) {
                    _position.value = mediaController.currentPosition.coerceAtLeast(0L)
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun connect(): MediaController = connectLock.withLock {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, AmplyPlaybackService::class.java))
        val mediaController = MediaController.Builder(context, token).buildAsync().await()
        mediaController.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refreshState(player)
        })
        controller = mediaController
        // Show the last session in the UI straight away, paused, like other players do.
        if (mediaController.mediaItemCount == 0) {
            queueStore.restore()?.let { restored ->
                mediaController.shuffleModeEnabled = restored.shuffle
                mediaController.repeatMode = restored.repeatMode.toPlayerRepeatMode()
                mediaController.setMediaItems(restored.songs.map { it.toMediaItem() }, restored.startIndex, restored.positionMs)
                mediaController.prepare()
            }
        }
        refreshState(mediaController)
        mediaController
    }

    private fun command(block: (MediaController) -> Unit) {
        scope.launch { block(connect()) }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        command { controller ->
            controller.shuffleModeEnabled = shuffle
            controller.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, songs.lastIndex), 0L)
            controller.prepare()
            controller.play()
        }
    }

    /** Plays a file another app handed us; it is not added to the library. */
    fun playUri(uri: Uri, title: String, artist: String?) = command { controller ->
        controller.setMediaItem(externalMediaItem(uri, title, artist))
        controller.prepare()
        controller.play()
    }

    /** Play/pause. With nothing loaded, play() makes the service resume the saved queue. */
    fun togglePlayPause() = command { if (it.isPlaying) it.pause() else it.play() }

    fun next() = command { it.seekToNextMediaItem() }

    /** Restarts the song if it is a few seconds in, otherwise goes back, like every other player. */
    fun previous() = command { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = command {
        it.seekTo(positionMs.coerceAtLeast(0L))
        refreshState(it)
    }

    fun setShuffle(enabled: Boolean) = command { it.shuffleModeEnabled = enabled }

    fun cycleRepeatMode() = command {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setRepeatMode(mode: RepeatMode) = command { it.repeatMode = mode.toPlayerRepeatMode() }

    /** Inserts [song] right after the current one. */
    fun playNext(song: Song) = command { controller ->
        if (controller.mediaItemCount == 0) {
            controller.setMediaItem(song.toMediaItem())
            controller.prepare()
            controller.play()
        } else {
            controller.addMediaItem(controller.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun addToQueue(song: Song) = command { controller ->
        controller.addMediaItem(song.toMediaItem())
        if (controller.mediaItemCount == 1) controller.prepare()
    }

    /** [queuePosition] indexes [AmplyPlaybackState.queue], i.e. play order. */
    fun skipToQueuePosition(queuePosition: Int) = command { controller ->
        _state.value.queueIndices.getOrNull(queuePosition)?.let {
            controller.seekToDefaultPosition(it)
            controller.play()
        }
    }

    fun removeFromQueue(queuePosition: Int) = command { controller ->
        _state.value.queueIndices.getOrNull(queuePosition)
            ?.takeIf { it != controller.currentMediaItemIndex }
            ?.let(controller::removeMediaItem)
    }

    /** Minutes > 0 starts a countdown, [PlaybackCommands.SLEEP_END_OF_SONG] waits for the song to end, 0 cancels. */
    fun setSleepTimer(minutes: Int) = command { controller ->
        controller.sendCustomCommand(PlaybackCommands.setSleepTimer, PlaybackCommands.sleepArgs(minutes))
    }

    private fun refreshState(player: Player) {
        val item = player.currentMediaItem
        val external = item?.takeIf { it.mediaId.startsWith(EXTERNAL_MEDIA_PREFIX) }
        val order = playOrder(player)
        _position.value = player.currentPosition.coerceAtLeast(0L)
        _state.value = AmplyPlaybackState(
            currentSongId = item?.mediaId?.toLongOrNull(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode.toRepeatMode(),
            queue = order.map { player.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L },
            queueIndices = order,
            queuePosition = order.indexOf(player.currentMediaItemIndex),
            externalTitle = external?.mediaMetadata?.title?.toString(),
            externalArtist = external?.mediaMetadata?.artist?.toString(),
        )
    }

    /** Playlist indices in the order they will play, which differs from list order when shuffled. */
    private fun playOrder(player: Player): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val shuffle = player.shuffleModeEnabled
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        return order
    }

    private companion object {
        const val POSITION_POLL_MS = 250L
    }
}

private fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    else -> RepeatMode.Off
}

internal fun String.toPlayerRepeatMode(): Int = when (this) {
    "one" -> Player.REPEAT_MODE_ONE
    "all" -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}

private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.One -> Player.REPEAT_MODE_ONE
    RepeatMode.All -> Player.REPEAT_MODE_ALL
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
}
