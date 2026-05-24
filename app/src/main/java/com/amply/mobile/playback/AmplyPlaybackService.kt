package com.amply.mobile.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.amply.mobile.MainActivity
import com.amply.mobile.widget.AmplyWidgetProvider

@UnstableApi
class AmplyPlaybackService : MediaSessionService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("amply_playback_settings", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                configureEqualizer(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AmplyWidgetProvider.updateAll(this@AmplyPlaybackService)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                AmplyWidgetProvider.updateAll(this@AmplyPlaybackService)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (exoPlayer.hasNextMediaItem()) {
                    exoPlayer.seekToNextMediaItem()
                    exoPlayer.prepare()
                    exoPlayer.play()
                } else {
                    exoPlayer.pause()
                    exoPlayer.clearMediaItems()
                }
                AmplyWidgetProvider.updateAll(this@AmplyPlaybackService)
            }
        })

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setId("amply_media_session")
            .setSessionActivity(sessionActivity())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player ?: return
        if (!currentPlayer.playWhenReady ||
            currentPlayer.mediaItemCount == 0 ||
            currentPlayer.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        player?.audioSessionId?.takeIf { it != C.AUDIO_SESSION_ID_UNSET }?.let(::configureEqualizer)
    }

    private fun configureEqualizer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        val enabled = prefs.getBoolean("equalizerEnabled", false)
        runCatching {
            val eq = equalizer ?: Equalizer(0, audioSessionId).also { equalizer = it }
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

    private fun sessionActivity(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
