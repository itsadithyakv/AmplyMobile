package com.amply.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amply.mobile.AmplyApplication
import com.amply.mobile.MainActivity
import com.amply.mobile.R
import com.amply.mobile.artwork.ArtworkLoader
import com.amply.mobile.data.local.songsByIdsChunked
import com.amply.mobile.playback.AmplyPlaybackService
import com.amply.mobile.playback.toMediaItem
import com.amply.mobile.pro.isProMix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the playback service last reported; the widget draws from this instead of binding a controller. */
data class NowPlayingSnapshot(
    val songId: Long?,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    /** Cover embedded in the file, which ExoPlayer extracts; used when there is no album-art uri. */
    val artworkData: ByteArray?,
    val audioUri: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * Home-screen widget in two sizes. The playback service pushes state with [publish] on every
 * play/pause, song change and seek, and every few seconds while playing for the progress bar; with
 * no service running the widget shows the last saved song, and pressing play resumes it.
 *
 * Artwork is decoded here and sent as a bitmap: the launcher draws the widget in its own process
 * and has no permission to open Amply's album-art content uris, so `setImageViewUri` left it blank.
 */
open class AmplyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in controlActions) return
        val pendingResult = goAsync()
        scope.launch {
            try {
                // Broadcasts get ~10 s; never hold one open on a service that does not answer.
                withTimeoutOrNull(8_000L) {
                    when (action) {
                        ACTION_PLAY_PAUSE -> control(context) { if (it.isPlaying) it.pause() else it.play() }
                        ACTION_PREVIOUS -> control(context) { it.seekToPrevious() }
                        ACTION_NEXT -> control(context) { it.seekToNextMediaItem() }
                        ACTION_PLAY_PLAYLIST -> playPlaylist(context, intent.getStringExtra(EXTRA_PLAYLIST_ID))
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.amply.mobile.widget.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.amply.mobile.widget.PREVIOUS"
        const val ACTION_NEXT = "com.amply.mobile.widget.NEXT"
        const val ACTION_PLAY_PLAYLIST = "com.amply.mobile.widget.PLAY_PLAYLIST"
        private const val EXTRA_PLAYLIST_ID = "playlist_id"
        private val controlActions = setOf(ACTION_PLAY_PAUSE, ACTION_PREVIOUS, ACTION_NEXT, ACTION_PLAY_PLAYLIST)

        private const val ART_PX = 256
        private const val SHORTCUT_ART_PX = 96
        private const val PROGRESS_MAX = 1000

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val providers = listOf(AmplyWidgetProvider::class.java, AmplyLargeWidgetProvider::class.java)

        /** Latest service state; null when nothing is loaded. Only touched on the main thread. */
        private var latest: NowPlayingSnapshot? = null
        private var hasServiceState = false
        private var renderJob: Job? = null

        /** Called by the playback service whenever something the widget shows has changed. */
        fun publish(context: Context, snapshot: NowPlayingSnapshot?) {
            latest = snapshot
            hasServiceState = true
            refresh(context)
        }

        fun refresh(context: Context) {
            val appContext = context.applicationContext
            renderJob?.cancel()
            renderJob = scope.launch { render(appContext) }
        }

        private suspend fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = providers.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
            if (ids.isEmpty()) return
            val state = loadState(context)
            ids.forEach { id ->
                val large = manager.getAppWidgetInfo(id)?.provider?.className == AmplyLargeWidgetProvider::class.java.name
                manager.updateAppWidget(id, buildViews(context, state, manager.getAppWidgetOptions(id), large))
            }
        }

        private suspend fun loadState(context: Context): WidgetState = withContext(Dispatchers.IO) {
            val database = (context.applicationContext as AmplyApplication).container.database
            val live = latest.takeIf { hasServiceState }
            val song = if (live == null) {
                // Service not running: show the song the saved queue would resume.
                (context.applicationContext as AmplyApplication).container.queueStore.currentSongId()
                    ?.let { database.songDao().songById(it) }
            } else {
                null
            }
            val title = live?.title ?: song?.title
            val artist = live?.artist ?: song?.artist
            val artworkUri = live?.artworkUri ?: song?.artworkUri
            val audioUri = live?.audioUri ?: song?.contentUri
            val art = when {
                title == null -> null
                live?.artworkData != null && artworkUri == null -> ArtworkLoader.decodeBytes(live.artworkData, ART_PX)
                else -> ArtworkLoader.load(context, artworkUri, ART_PX, audioUri)
            }
            WidgetState(
                title = title ?: context.getString(R.string.widget_idle_title),
                artist = artist ?: context.getString(R.string.widget_idle_subtitle),
                art = art,
                hasSong = title != null,
                isPlaying = live?.isPlaying == true,
                progress = live?.takeIf { it.durationMs > 0L }
                    ?.let { (it.positionMs * PROGRESS_MAX / it.durationMs).toInt().coerceIn(0, PROGRESS_MAX) }
                    ?: 0,
                playlists = playlistShortcuts(context),
            )
        }

        /**
         * Up to four playlists with songs, in the same stable order every time. The old widget
         * shuffled these on every refresh, so the buttons moved under the user's thumb.
         */
        private suspend fun playlistShortcuts(context: Context): List<PlaylistShortcut> {
            val container = (context.applicationContext as AmplyApplication).container
            val database = container.database
            val debugPro = database.settingsDao().valueFor("debugPro")?.toBooleanStrictOrNull() ?: false
            val isPro = AmplyApplication.isPro(container.proStore.state.value.owned, debugPro)
            // Genre and mood mixes are Pro; the free widget only offers the core mixes.
            return database.playlistDao().playablePlaylists(16).filter { isPro || !isProMix(it.id) }.take(4).map { playlist ->
                val firstId = database.playlistDao().songIdsForPlaylist(playlist.id).firstOrNull()
                val first = firstId?.let { database.songDao().songById(it) }
                PlaylistShortcut(
                    id = playlist.id,
                    name = playlist.name.widgetPlaylistLabel(),
                    art = first?.let { ArtworkLoader.load(context, it.artworkUri, SHORTCUT_ART_PX, it.contentUri) },
                )
            }
        }

        private fun buildViews(context: Context, state: WidgetState, options: Bundle, largeProvider: Boolean): RemoteViews {
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val large = largeProvider || minHeight >= 140
            val layout = if (large) R.layout.widget_amply_player else R.layout.widget_amply_player_compact
            return RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.widget_title, state.title)
                setTextViewText(R.id.widget_artist, state.artist)
                setImageViewResource(R.id.widget_play_pause, if (state.isPlaying) R.drawable.ic_amply_pause else R.drawable.ic_amply_play)
                setContentDescription(
                    R.id.widget_play_pause,
                    context.getString(if (state.isPlaying) R.string.widget_pause else R.string.widget_play),
                )
                setOnClickPendingIntent(R.id.widget_root, activityIntent(context))
                setOnClickPendingIntent(R.id.widget_previous, controlIntent(context, ACTION_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_play_pause, controlIntent(context, ACTION_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, controlIntent(context, ACTION_NEXT))

                val art = state.art
                if (art != null) {
                    setImageViewBitmap(R.id.widget_artwork, art.rounded(context.dp(if (large) 16 else 12).toFloat()))
                } else {
                    setImageViewResource(R.id.widget_artwork, R.drawable.ic_launcher_foreground)
                }

                if (large) {
                    setViewVisibility(R.id.widget_progress, if (state.hasSong) View.VISIBLE else View.GONE)
                    setProgressBar(R.id.widget_progress, PROGRESS_MAX, state.progress, false)
                    bindPlaylists(context, state.playlists)
                }
            }
        }

        private fun RemoteViews.bindPlaylists(context: Context, playlists: List<PlaylistShortcut>) {
            val rows = listOf(R.id.widget_playlist_1, R.id.widget_playlist_2, R.id.widget_playlist_3, R.id.widget_playlist_4)
            val arts = listOf(R.id.widget_playlist_art_1, R.id.widget_playlist_art_2, R.id.widget_playlist_art_3, R.id.widget_playlist_art_4)
            val labels = listOf(R.id.widget_playlist_label_1, R.id.widget_playlist_label_2, R.id.widget_playlist_label_3, R.id.widget_playlist_label_4)
            rows.forEachIndexed { index, row ->
                val playlist = playlists.getOrNull(index)
                if (playlist == null) {
                    setViewVisibility(row, View.INVISIBLE)
                    return@forEachIndexed
                }
                setViewVisibility(row, View.VISIBLE)
                setTextViewText(labels[index], playlist.name)
                setContentDescription(row, context.getString(R.string.widget_play_playlist, playlist.name))
                val art = playlist.art
                if (art != null) {
                    setImageViewBitmap(arts[index], art.rounded(context.dp(8).toFloat()))
                } else {
                    setImageViewResource(arts[index], R.drawable.ic_launcher_foreground)
                }
                setOnClickPendingIntent(row, playlistIntent(context, playlist.id))
            }
        }

        private suspend fun control(context: Context, action: (MediaController) -> Unit) {
            val controller = controller(context)
            try {
                // On an empty player play() makes the service restore the saved queue
                // (MediaSession.Callback.onPlaybackResumption), so no restore logic is needed here.
                action(controller)
            } finally {
                controller.release()
            }
        }

        private suspend fun playPlaylist(context: Context, playlistId: String?) {
            if (playlistId.isNullOrBlank()) return
            val database = (context.applicationContext as AmplyApplication).container.database
            val songs = withContext(Dispatchers.IO) {
                database.songDao().songsByIdsChunked(database.playlistDao().songIdsForPlaylist(playlistId))
            }
            if (songs.isEmpty()) return
            control(context) { controller ->
                controller.setMediaItems(songs.map { it.toMediaItem() }, 0, 0L)
                controller.prepare()
                controller.play()
            }
        }

        @androidx.annotation.OptIn(UnstableApi::class)
        private suspend fun controller(context: Context): MediaController {
            // The Context handed to a BroadcastReceiver may not bind services (it throws
            // ReceiverCallNotAllowedException), and building a MediaController binds the playback
            // service. Always connect through the application context.
            val app = context.applicationContext
            val token = SessionToken(app, ComponentName(app, AmplyPlaybackService::class.java))
            return MediaController.Builder(app, token).buildAsync().await()
        }

        private fun controlIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(context, AmplyWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun playlistIntent(context: Context, playlistId: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                playlistId.hashCode(),
                Intent(context, AmplyWidgetProvider::class.java)
                    .setAction(ACTION_PLAY_PLAYLIST)
                    .putExtra(EXTRA_PLAYLIST_ID, playlistId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun activityIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

class AmplyLargeWidgetProvider : AmplyWidgetProvider()

private data class WidgetState(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val hasSong: Boolean,
    val isPlaying: Boolean,
    val progress: Int,
    val playlists: List<PlaylistShortcut>,
)

private data class PlaylistShortcut(val id: String, val name: String, val art: Bitmap?)

private fun String.widgetPlaylistLabel(): String =
    replace("Daily ", "")
        .replace(" Mix", "")
        .ifBlank { "Mix" }

/** RemoteViews cannot clip images before Android 12, so shape the bitmap itself. */
private fun Bitmap.rounded(radiusPx: Float): Bitmap = clipped { canvas, paint, rect -> canvas.drawRoundRect(rect, radiusPx, radiusPx, paint) }

private inline fun Bitmap.clipped(draw: (Canvas, Paint, RectF) -> Unit): Bitmap {
    val side = minOf(width, height)
    val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(this@clipped, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-(width - side) / 2f, -(height - side) / 2f) })
        }
    }
    draw(Canvas(output), paint, RectF(0f, 0f, side.toFloat(), side.toFloat()))
    return output
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
