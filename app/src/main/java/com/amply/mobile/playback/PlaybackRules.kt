package com.amply.mobile.playback

/** What one stretch of listening to a song counts as in the library stats. */
enum class ListenOutcome { Play, Skip, Ignore }

/**
 * A listen counts as a play once it reaches 30 seconds or half the song, whichever comes first
 * (the usual scrobbling rule). Leaving earlier by pressing next counts as a skip; anything else,
 * such as the queue being replaced, is ignored. Smart playlists rank on these numbers, so a song
 * the user skipped after two seconds must not look like a song they played.
 */
fun classifyListen(listenedMs: Long, durationMs: Long, userSkipped: Boolean): ListenOutcome {
    val threshold = if (durationMs > 0L) minOf(PLAY_THRESHOLD_MS, durationMs / 2) else PLAY_THRESHOLD_MS
    return when {
        listenedMs >= threshold && listenedMs > 0L -> ListenOutcome.Play
        userSkipped -> ListenOutcome.Skip
        else -> ListenOutcome.Ignore
    }
}

const val PLAY_THRESHOLD_MS = 30_000L

/** Adds up time actually spent playing one song, across pauses. Main thread only. */
class ListenTracker {
    var songId: Long? = null
        private set
    var durationMs: Long = 0L
        private set
    private var accumulatedMs = 0L
    private var playingSinceMs: Long? = null

    fun start(songId: Long?, durationMs: Long, nowMs: Long, playing: Boolean) {
        this.songId = songId
        this.durationMs = durationMs
        accumulatedMs = 0L
        playingSinceMs = if (playing && songId != null) nowMs else null
    }

    fun setDuration(durationMs: Long) {
        if (durationMs > 0L) this.durationMs = durationMs
    }

    fun onPlayingChanged(playing: Boolean, nowMs: Long) {
        if (playing) {
            if (playingSinceMs == null && songId != null) playingSinceMs = nowMs
        } else {
            playingSinceMs?.let { accumulatedMs += nowMs - it }
            playingSinceMs = null
        }
    }

    fun listenedMs(nowMs: Long): Long = accumulatedMs + (playingSinceMs?.let { nowMs - it } ?: 0L)
}

/**
 * Volume for "fade between songs": the last [fadeMs] of a song fades out and the first [fadeMs] of
 * the next fades in. This is a fade, not an overlapping crossfade; a single player cannot play two
 * songs at once.
 */
fun transitionFadeVolume(positionMs: Long, durationMs: Long, fadeMs: Long, fadeInActive: Boolean, fadeOutActive: Boolean): Float {
    if (fadeMs <= 0L) return 1f
    val fadeIn = if (fadeInActive) (positionMs.toFloat() / fadeMs).coerceIn(0f, 1f) else 1f
    val remaining = durationMs - positionMs
    val fadeOut = if (fadeOutActive && durationMs > 0L) (remaining.toFloat() / fadeMs).coerceIn(0f, 1f) else 1f
    return minOf(fadeIn, fadeOut)
}

/** Fades are capped at a third of the song so very short tracks are never silent. */
fun effectiveFadeMs(settingMs: Long, durationMs: Long): Long =
    if (settingMs <= 0L || durationMs <= 0L) 0L else minOf(settingMs, durationMs / 3)
