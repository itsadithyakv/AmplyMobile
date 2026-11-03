package com.amply.mobile.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Pause when `SystemClock.elapsedRealtime()` reaches [endsAtElapsedMs]. */
    data class At(val endsAtElapsedMs: Long, val totalMs: Long) : SleepTimerState {
        fun remainingMs(nowElapsedMs: Long): Long = (endsAtElapsedMs - nowElapsedMs).coerceAtLeast(0L)
    }

    /** Pause once the song that is playing now finishes. */
    data object EndOfSong : SleepTimerState
}

/**
 * Sleep timer state lives in the playback service; this holder lets the UI, which shares the
 * process, show it without a round trip. Only the service writes to it.
 */
object SleepTimer {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state

    internal fun set(state: SleepTimerState) {
        _state.value = state
    }
}

/** A countdown fades out over its last few seconds instead of cutting the song off. */
const val SLEEP_FADE_OUT_MS = 8_000L

/** Volume multiplier while a timer runs down: 1 until the last [SLEEP_FADE_OUT_MS], then a linear fade. */
fun sleepFadeVolume(remainingMs: Long): Float =
    if (remainingMs >= SLEEP_FADE_OUT_MS) 1f else (remainingMs.toFloat() / SLEEP_FADE_OUT_MS).coerceIn(0f, 1f)
