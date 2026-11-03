package com.amply.mobile.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRulesTest {
    @Test
    fun listenCountsAsPlayAfterThirtySecondsOrHalfTheSong() {
        assertEquals(ListenOutcome.Play, classifyListen(30_000L, 240_000L, userSkipped = true))
        // A 40 s interlude counts after 20 s.
        assertEquals(ListenOutcome.Play, classifyListen(20_000L, 40_000L, userSkipped = false))
        assertEquals(ListenOutcome.Ignore, classifyListen(19_000L, 40_000L, userSkipped = false))
    }

    @Test
    fun earlyNextIsASkipButAReplacedQueueIsNot() {
        assertEquals(ListenOutcome.Skip, classifyListen(4_000L, 200_000L, userSkipped = true))
        assertEquals(ListenOutcome.Ignore, classifyListen(4_000L, 200_000L, userSkipped = false))
        assertEquals(ListenOutcome.Skip, classifyListen(0L, 0L, userSkipped = true))
    }

    @Test
    fun trackerOnlyCountsTimeSpentPlaying() {
        val tracker = ListenTracker()
        tracker.start(songId = 7L, durationMs = 180_000L, nowMs = 1_000L, playing = true)
        tracker.onPlayingChanged(false, nowMs = 11_000L) // 10 s played
        tracker.onPlayingChanged(true, nowMs = 60_000L) // paused for 49 s
        assertEquals(15_000L, tracker.listenedMs(nowMs = 65_000L))
        assertEquals(7L, tracker.songId)
    }

    @Test
    fun trackerStartedPausedDoesNotCountUntilPlay() {
        val tracker = ListenTracker()
        tracker.start(songId = 1L, durationMs = 100_000L, nowMs = 0L, playing = false)
        assertEquals(0L, tracker.listenedMs(nowMs = 50_000L))
        tracker.onPlayingChanged(true, nowMs = 50_000L)
        assertEquals(5_000L, tracker.listenedMs(nowMs = 55_000L))
    }

    @Test
    fun fadeRampsOutAtTheEndAndInAtTheStart() {
        val fade = 4_000L
        assertEquals(1f, transitionFadeVolume(100_000L, 200_000L, fade, fadeInActive = false, fadeOutActive = true), 0.0001f)
        assertEquals(0.5f, transitionFadeVolume(198_000L, 200_000L, fade, fadeInActive = false, fadeOutActive = true), 0.0001f)
        assertEquals(0.25f, transitionFadeVolume(1_000L, 200_000L, fade, fadeInActive = true, fadeOutActive = true), 0.0001f)
        // Last song in the queue: nothing follows, so it must not fade to silence.
        assertEquals(1f, transitionFadeVolume(199_000L, 200_000L, fade, fadeInActive = false, fadeOutActive = false), 0.0001f)
    }

    @Test
    fun fadeIsOffWhenDisabledAndCappedForShortSongs() {
        assertEquals(1f, transitionFadeVolume(199_000L, 200_000L, 0L, fadeInActive = true, fadeOutActive = true), 0.0001f)
        assertEquals(0L, effectiveFadeMs(0L, 200_000L))
        assertEquals(6_000L, effectiveFadeMs(6_000L, 200_000L))
        assertEquals(3_000L, effectiveFadeMs(12_000L, 9_000L))
    }

    @Test
    fun sleepTimerFadesOverTheLastSeconds() {
        assertEquals(1f, sleepFadeVolume(60_000L), 0.0001f)
        assertEquals(0.5f, sleepFadeVolume(SLEEP_FADE_OUT_MS / 2), 0.0001f)
        assertEquals(0f, sleepFadeVolume(0L), 0.0001f)
        assertEquals(30_000L, SleepTimerState.At(endsAtElapsedMs = 100_000L, totalMs = 60_000L).remainingMs(70_000L))
        assertEquals(0L, SleepTimerState.At(endsAtElapsedMs = 100_000L, totalMs = 60_000L).remainingMs(120_000L))
    }
}
