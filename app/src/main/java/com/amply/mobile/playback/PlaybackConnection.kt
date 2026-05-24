package com.amply.mobile.playback

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amply.mobile.data.local.QueueDao
import com.amply.mobile.data.local.QueueStateEntity
import com.amply.mobile.data.local.SongDao
import com.amply.mobile.data.local.SongEntity
import com.amply.mobile.data.local.toRepeatMode
import com.amply.mobile.data.local.toStorageValue
import com.amply.mobile.domain.AmplyPlaybackState
import com.amply.mobile.domain.RepeatMode
import com.amply.mobile.domain.Song
import com.amply.mobile.util.nowSec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackConnection(
    private val context: Context,
    private val queueDao: QueueDao,
    private val songDao: SongDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var lastQueueIds: List<Long> = emptyList()
    private val prefs: SharedPreferences = context.getSharedPreferences("amply_playback_settings", Context.MODE_PRIVATE)
    private var transitionInProgress = false
    private var lastTransitionMediaId: String? = null

    private val _state = MutableStateFlow(AmplyPlaybackState())
    val state: StateFlow<AmplyPlaybackState> = _state

    init {
        scope.launch {
            connect()
            while (isActive) {
                controller?.takeIf { it.isPlaying }?.let {
                    refreshState(it)
                    maybeRunCrossfadeTransition(it)
                }
                delay(250L)
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun connect(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, AmplyPlaybackService::class.java))
        val mediaController = MediaController.Builder(context, token).buildAsync().await()
        mediaController.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refreshState(player)
                persistQueue()
            }
        })
        controller = mediaController
        queueDao.queue()?.let { restore ->
            mediaController.shuffleModeEnabled = restore.shuffle
            mediaController.repeatMode = restore.repeatMode.toPlayerRepeatMode()
            restoreQueueIfEmpty(mediaController, restore)
            _state.value = _state.value.copy(
                shuffle = restore.shuffle,
                repeatMode = restore.repeatMode.toRepeatMode(),
                positionMs = restore.positionMs,
            )
        }
        refreshState(mediaController)
        return mediaController
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        scope.launch {
            val mediaController = connect()
            cancelCrossfade(mediaController)
            lastQueueIds = songs.map { it.id }
            mediaController.shuffleModeEnabled = shuffle
            mediaController.setMediaItems(
                songs.map { it.toMediaItem() },
                startIndex.coerceIn(0, songs.lastIndex),
                0L,
            )
            mediaController.prepare()
            mediaController.play()
            persistQueue()
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val mediaController = connect()
            restoreQueueIfEmpty(mediaController)
            if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
            persistQueue()
        }
    }

    fun next() {
        scope.launch {
            val mediaController = connect()
            cancelCrossfade(mediaController)
            restoreQueueIfEmpty(mediaController)
            mediaController.seekToNextMediaItem()
            persistQueue()
        }
    }

    fun previous() {
        scope.launch {
            val mediaController = connect()
            cancelCrossfade(mediaController)
            restoreQueueIfEmpty(mediaController)
            mediaController.seekToPreviousMediaItem()
            persistQueue()
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            val mediaController = connect()
            restoreQueueIfEmpty(mediaController)
            mediaController.seekTo(positionMs.coerceAtLeast(0L))
            persistQueue()
        }
    }

    fun setShuffle(enabled: Boolean) {
        scope.launch {
            val mediaController = connect()
            mediaController.shuffleModeEnabled = enabled
            refreshState(mediaController)
            persistQueue()
        }
    }

    fun cycleRepeatMode() {
        scope.launch {
            val mediaController = connect()
            mediaController.repeatMode = when (mediaController.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            refreshState(mediaController)
            persistQueue()
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        scope.launch {
            val mediaController = connect()
            mediaController.repeatMode = mode.toPlayerRepeatMode()
            refreshState(mediaController)
            persistQueue()
        }
    }

    private fun refreshState(player: Player) {
        _state.value = AmplyPlaybackState(
            currentSongId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode.toRepeatMode(),
        )
    }

    private fun maybeRunCrossfadeTransition(player: MediaController) {
        val crossfadeMs = (prefs.getFloat("crossfadeSeconds", 0f) * 1000f).toLong()
        if (crossfadeMs <= 100L || transitionInProgress || !player.hasNextMediaItem()) return
        val duration = player.duration.takeIf { it > 0L } ?: return
        val remaining = duration - player.currentPosition
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (remaining > crossfadeMs || lastTransitionMediaId == mediaId) return
        transitionInProgress = true
        lastTransitionMediaId = mediaId
        scope.launch {
            val originalVolume = player.volume
            try {
                val fadeMs = crossfadeMs.coerceIn(600L, 3_000L)
                val steps = 8
                repeat(steps) { index ->
                    player.volume = originalVolume * (1f - ((index + 1).toFloat() / steps))
                    delay(fadeMs / steps)
                }
                player.seekToNextMediaItem()
                player.volume = 0f
                repeat(steps) { index ->
                    player.volume = originalVolume * ((index + 1).toFloat() / steps)
                    delay(fadeMs / steps)
                }
            } finally {
                player.volume = originalVolume
                transitionInProgress = false
            }
        }
    }

    private suspend fun restoreQueueIfEmpty(
        mediaController: MediaController,
        restore: QueueStateEntity? = null,
    ) {
        if (mediaController.mediaItemCount > 0) return
        val queue = restore ?: queueDao.queue() ?: return
        val ids = queue.songIdsCsv.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return
        val songsById = withContext(Dispatchers.IO) {
            songDao.songsByIds(ids).associateBy { it.id }
        }
        val songs = ids.mapNotNull { songsById[it] }
        if (songs.isEmpty()) return
        lastQueueIds = songs.map { it.id }
        mediaController.shuffleModeEnabled = queue.shuffle
        mediaController.repeatMode = queue.repeatMode.toPlayerRepeatMode()
        mediaController.setMediaItems(
            songs.map { it.toMediaItem() },
            queue.currentIndex.coerceIn(0, songs.lastIndex),
            queue.positionMs.coerceAtLeast(0L),
        )
        mediaController.prepare()
    }

    private fun cancelCrossfade(player: MediaController) {
        transitionInProgress = false
        lastTransitionMediaId = null
        player.volume = 1f
    }

    private fun persistQueue() {
        val mediaController = controller ?: return
        val queueIds = if (lastQueueIds.isNotEmpty()) {
            lastQueueIds
        } else {
            (0 until mediaController.mediaItemCount).mapNotNull {
                mediaController.getMediaItemAt(it).mediaId.toLongOrNull()
            }
        }
        if (queueIds.isEmpty()) return
        val currentIndex = mediaController.currentMediaItemIndex.coerceAtLeast(0)
        val positionMs = mediaController.currentPosition.coerceAtLeast(0L)
        val shuffle = mediaController.shuffleModeEnabled
        val repeatMode = mediaController.repeatMode.toRepeatMode().toStorageValue()
        scope.launch(Dispatchers.IO) {
            queueDao.upsert(
                QueueStateEntity(
                    songIdsCsv = queueIds.joinToString(","),
                    currentIndex = currentIndex,
                    positionMs = positionMs,
                    shuffle = shuffle,
                    repeatMode = repeatMode,
                    updatedAtSec = nowSec(),
                ),
            )
        }
    }
}

private fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(contentUri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .build(),
        )
        .build()

private fun SongEntity.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(contentUri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .build(),
        )
        .build()

private fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    else -> RepeatMode.Off
}

private fun String.toPlayerRepeatMode(): Int = when (this) {
    "one" -> Player.REPEAT_MODE_ONE
    "all" -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}

private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.One -> Player.REPEAT_MODE_ONE
    RepeatMode.All -> Player.REPEAT_MODE_ALL
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
}
