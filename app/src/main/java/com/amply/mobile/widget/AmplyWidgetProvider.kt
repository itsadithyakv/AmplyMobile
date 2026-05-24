package com.amply.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amply.mobile.AmplyApplication
import com.amply.mobile.MainActivity
import com.amply.mobile.R
import com.amply.mobile.data.local.SongEntity
import com.amply.mobile.data.local.QueueStateEntity
import com.amply.mobile.playback.AmplyPlaybackService
import com.amply.mobile.util.nowSec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class AmplyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_PLAY_PAUSE -> control(context) { controller ->
                        if (controller.isPlaying) controller.pause() else controller.play()
                    }
                    ACTION_PREVIOUS -> control(context) { it.seekToPreviousMediaItem() }
                    ACTION_NEXT -> control(context) { it.seekToNextMediaItem() }
                    ACTION_PLAY_PLAYLIST -> playPlaylist(context, intent.getStringExtra(EXTRA_PLAYLIST_ID))
                    ACTION_REFRESH -> updateAllNow(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun control(context: Context, action: (MediaController) -> Unit) {
        val controller = controller(context)
        ensureRestoredQueue(context, controller)
        action(controller)
        controller.release()
        updateAllNow(context)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.amply.mobile.widget.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.amply.mobile.widget.PREVIOUS"
        const val ACTION_NEXT = "com.amply.mobile.widget.NEXT"
        const val ACTION_REFRESH = "com.amply.mobile.widget.REFRESH"
        const val ACTION_PLAY_PLAYLIST = "com.amply.mobile.widget.PLAY_PLAYLIST"
        private const val EXTRA_PLAYLIST_ID = "playlist_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val widgetProviders = listOf(
            AmplyWidgetProvider::class.java,
            AmplyLargeWidgetProvider::class.java,
        )

        fun updateAll(context: Context) {
            scope.launch {
                updateAllNow(context)
            }
        }

        private suspend fun updateAllNow(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = widgetProviders.flatMap { provider ->
                manager.getAppWidgetIds(ComponentName(appContext, provider)).toList()
            }
            if (ids.isEmpty()) return
            val state = loadWidgetState(appContext)
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val isLarge = manager.getAppWidgetInfo(id)?.provider?.className == AmplyLargeWidgetProvider::class.java.name
                manager.updateAppWidget(id, buildViews(appContext, state, options, isLarge))
            }
        }

        private suspend fun loadWidgetState(context: Context): WidgetState {
            val controller = runCatching { controller(context) }.getOrNull()
            val sessionSongId = controller?.currentMediaItem?.mediaId?.toLongOrNull()
            val isPlaying = controller?.isPlaying == true
            val app = context.applicationContext as? AmplyApplication
            val database = app?.container?.database
            val state = withContext(Dispatchers.IO) {
                val id = sessionSongId ?: database?.queueDao()?.queue()?.let { queue ->
                    queue.songIdsCsv
                        .split(",")
                        .mapNotNull { it.toLongOrNull() }
                        .getOrNull(queue.currentIndex)
                }
                val current = id?.let { database?.songDao()?.songById(it) }
                val extra = database?.songDao()
                    ?.recentSongsForWidget(current?.id ?: Long.MIN_VALUE, 5)
                    .orEmpty()
                val songs = listOfNotNull(current) + extra
                val playlists = database?.playlistDao()?.playablePlaylists(8)
                    ?.mapNotNull { playlist ->
                        val songIds = database.playlistDao().songIdsForPlaylist(playlist.id)
                        val songsById = database.songDao().songsByIds(songIds).associateBy { it.id }
                        val firstSong = songIds.asSequence().mapNotNull { songsById[it] }.firstOrNull()
                        if (firstSong == null) {
                            null
                        } else {
                            PlaylistShortcut(
                                id = playlist.id,
                                name = playlist.name.widgetPlaylistLabel(),
                                artworkUri = firstSong.artworkUri,
                            )
                        }
                    }
                    ?.shuffled()
                    ?.take(4)
                    .orEmpty()
                songs to playlists
            }
            val song = state.first.firstOrNull()
            controller?.release()
            val hasSong = song != null
            return WidgetState(
                title = song?.title ?: musicQuote(),
                artist = song?.artist.orEmpty(),
                artworkUri = song?.artworkUri,
                playlists = state.second,
                isPlaying = isPlaying,
                hasSong = hasSong,
            )
        }

        private fun buildViews(context: Context, state: WidgetState, options: Bundle, isLargeProvider: Boolean): RemoteViews {
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val layout = if (isLargeProvider || minHeight >= 140) R.layout.widget_amply_player else R.layout.widget_amply_player_compact
            return RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.widget_title, state.title)
                setTextViewText(R.id.widget_artist, state.artist)
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (state.isPlaying) R.drawable.ic_amply_pause else R.drawable.ic_amply_play,
                )
                setOnClickPendingIntent(R.id.widget_root, activityIntent(context))
                setOnClickPendingIntent(R.id.widget_previous, widgetIntent(context, ACTION_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_play_pause, widgetIntent(context, ACTION_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, widgetIntent(context, ACTION_NEXT))
                if (layout == R.layout.widget_amply_player) {
                    setViewVisibility(R.id.widget_artwork, if (state.hasSong) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.widget_progress, if (state.hasSong) View.VISIBLE else View.GONE)
                    if (state.hasSong) {
                        setViewPadding(R.id.widget_info_card, context.dp(140), context.dp(18), context.dp(24), context.dp(34))
                    } else {
                        setViewPadding(R.id.widget_info_card, context.dp(24), context.dp(18), context.dp(24), context.dp(18))
                    }
                    if (state.artworkUri != null) {
                        setImageViewUri(R.id.widget_artwork, Uri.parse(state.artworkUri))
                    } else {
                        setImageViewResource(R.id.widget_artwork, R.drawable.ic_launcher_foreground)
                    }
                    if (state.hasSong) {
                        setFloat(
                            R.id.widget_artwork,
                            "setRotation",
                            if (state.isPlaying) ((System.currentTimeMillis() / 90L) % 360L).toFloat() else 0f,
                        )
                    }
                    val playlistIds = listOf(
                        R.id.widget_playlist_1,
                        R.id.widget_playlist_2,
                        R.id.widget_playlist_3,
                        R.id.widget_playlist_4,
                    )
                    val playlistArtIds = listOf(
                        R.id.widget_playlist_art_1,
                        R.id.widget_playlist_art_2,
                        R.id.widget_playlist_art_3,
                        R.id.widget_playlist_art_4,
                    )
                    val playlistLabelIds = listOf(
                        R.id.widget_playlist_label_1,
                        R.id.widget_playlist_label_2,
                        R.id.widget_playlist_label_3,
                        R.id.widget_playlist_label_4,
                    )
                    playlistIds.forEachIndexed { index, viewId ->
                        val playlist = state.playlists.getOrNull(index)
                        if (playlist != null) {
                            setViewVisibility(viewId, View.VISIBLE)
                            setTextViewText(playlistLabelIds[index], playlist.name)
                            if (playlist.artworkUri != null) {
                                setImageViewUri(playlistArtIds[index], Uri.parse(playlist.artworkUri))
                            } else {
                                setImageViewResource(playlistArtIds[index], R.drawable.ic_launcher_foreground)
                            }
                            setOnClickPendingIntent(viewId, playlistIntent(context, playlist.id))
                        } else {
                            setViewVisibility(viewId, View.GONE)
                        }
                    }
                }
            }
        }

        private fun widgetIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, AmplyWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )
        }

        private fun playlistIntent(context: Context, playlistId: String): PendingIntent {
            val intent = Intent(context, AmplyWidgetProvider::class.java)
                .setAction(ACTION_PLAY_PLAYLIST)
                .putExtra(EXTRA_PLAYLIST_ID, playlistId)
            return PendingIntent.getBroadcast(
                context,
                playlistId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )
        }

        private fun activityIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
            )

        @androidx.annotation.OptIn(UnstableApi::class)
        private suspend fun controller(context: Context): MediaController {
            val token = SessionToken(context, ComponentName(context, AmplyPlaybackService::class.java))
            return MediaController.Builder(context, token).buildAsync().await()
        }

        private suspend fun playPlaylist(context: Context, playlistId: String?) {
            if (playlistId.isNullOrBlank()) return
            val app = context.applicationContext as? AmplyApplication ?: return
            val database = app.container.database
            val songs = withContext(Dispatchers.IO) {
                val songIds = database.playlistDao().songIdsForPlaylist(playlistId)
                database.songDao().songsByIds(songIds)
                    .associateBy { it.id }
                    .let { byId -> songIds.mapNotNull { byId[it] } }
            }
            if (songs.isEmpty()) return
            val mediaController = controller(context)
            mediaController.setMediaItems(songs.map { it.toMediaItem() }, 0, 0L)
            mediaController.prepare()
            mediaController.play()
            val shuffle = mediaController.shuffleModeEnabled
            withContext(Dispatchers.IO) {
                database.queueDao().upsert(
                    QueueStateEntity(
                        songIdsCsv = songs.joinToString(",") { it.id.toString() },
                        currentIndex = 0,
                        positionMs = 0L,
                        shuffle = shuffle,
                        repeatMode = "off",
                        updatedAtSec = nowSec(),
                    ),
                )
            }
            mediaController.release()
            updateAllNow(context)
        }

        private suspend fun ensureRestoredQueue(context: Context, mediaController: MediaController) {
            if (mediaController.mediaItemCount > 0) return
            val app = context.applicationContext as? AmplyApplication ?: return
            val database = app.container.database
            val restored = withContext(Dispatchers.IO) {
                val queue = database.queueDao().queue() ?: return@withContext null
                val ids = queue.songIdsCsv.split(",").mapNotNull { it.toLongOrNull() }
                if (ids.isEmpty()) return@withContext null
                val songsById = database.songDao().songsByIds(ids).associateBy { it.id }
                val songs = ids.mapNotNull { songsById[it] }
                if (songs.isEmpty()) null else queue to songs
            } ?: return
            val (queue, songs) = restored
            mediaController.shuffleModeEnabled = queue.shuffle
            mediaController.repeatMode = queue.repeatMode.toPlayerRepeatMode()
            mediaController.setMediaItems(
                songs.map { it.toMediaItem() },
                queue.currentIndex.coerceIn(0, songs.lastIndex),
                queue.positionMs.coerceAtLeast(0L),
            )
            mediaController.prepare()
        }

        private fun immutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}

class AmplyLargeWidgetProvider : AmplyWidgetProvider()

private data class WidgetState(
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val playlists: List<PlaylistShortcut>,
    val isPlaying: Boolean,
    val hasSong: Boolean,
)

private data class PlaylistShortcut(
    val id: String,
    val name: String,
    val artworkUri: String?,
)

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

private fun String.widgetPlaylistLabel(): String =
    replace("Daily ", "")
        .replace(" Mix", "")
        .ifBlank { "Mix" }

private fun musicQuote(): String {
    val quotes = listOf(
        "Let the music breathe",
        "Find your next rhythm",
        "Press play, drift away",
        "Sound waits for you",
        "Spin something beautiful",
    )
    val index = ((System.currentTimeMillis() / 3_600_000L) % quotes.size).toInt()
    return quotes[index]
}

private fun String.toPlayerRepeatMode(): Int = when (this) {
    "one" -> Player.REPEAT_MODE_ONE
    "all" -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
