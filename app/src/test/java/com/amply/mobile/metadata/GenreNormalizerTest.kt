package com.amply.mobile.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreNormalizerTest {
    @Test
    fun mapsCommonGenresToBuckets() {
        assertEquals("Hip-Hop", normalizeGenreBucket("Trap Rap"))
        assertEquals("Rock", normalizeGenreBucket("Punk Rock"))
        assertEquals("Alternative", normalizeGenreBucket("Alternative"))
        assertEquals("Unknown", normalizeGenreBucket("n/a"))
    }
}
