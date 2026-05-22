package com.amply.mobile.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesTimedLines() {
        val lines = parseLrc("[00:10.50]First\n[01:02.003]Second")

        assertEquals(2, lines.size)
        assertEquals(10_500L, lines[0].timeMs)
        assertEquals("First", lines[0].text)
        assertEquals(62_003L, lines[1].timeMs)
    }

    @Test
    fun validatesReasonableSyncedLyrics() {
        val raw = "[00:01.00]One\n[00:02.00]Two\n[00:03.00]Three"

        assertTrue(validateLyricsQuality(raw))
    }
}
