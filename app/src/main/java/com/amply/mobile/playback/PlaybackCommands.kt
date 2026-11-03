package com.amply.mobile.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/** Custom session commands understood by [AmplyPlaybackService]. */
object PlaybackCommands {
    const val ACTION_TOGGLE_FAVORITE = "com.amply.mobile.TOGGLE_FAVORITE"
    const val ACTION_TOGGLE_SHUFFLE = "com.amply.mobile.TOGGLE_SHUFFLE"

    /** Args: [EXTRA_SLEEP_MINUTES] > 0 for a countdown, [SLEEP_END_OF_SONG], or 0 to cancel. */
    const val ACTION_SET_SLEEP_TIMER = "com.amply.mobile.SET_SLEEP_TIMER"
    const val EXTRA_SLEEP_MINUTES = "minutes"
    const val SLEEP_END_OF_SONG = -1

    val toggleFavorite = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    val toggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val setSleepTimer = SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY)

    val all = listOf(toggleFavorite, toggleShuffle, setSleepTimer)

    fun sleepArgs(minutes: Int): Bundle = Bundle().apply { putInt(EXTRA_SLEEP_MINUTES, minutes) }
}
