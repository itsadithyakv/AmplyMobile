package com.amply.mobile.playback

import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

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
        })

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setId("amply_media_session")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

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
}
