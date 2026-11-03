package com.amply.mobile.playback

import com.amply.mobile.artwork.sampleSize
import com.amply.mobile.data.local.staleMediaStoreIds
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueAndBrowseTest {
    @Test
    fun queueIdsRoundTripAndIgnoreJunk() {
        assertEquals("3,-42,9", encodeQueueIds(listOf(3L, -42L, 9L)))
        assertEquals(listOf(3L, -42L, 9L), decodeQueueIds("3, -42,,x,9"))
        assertEquals(emptyList<Long>(), decodeQueueIds(""))
    }

    @Test
    fun restoreStartsAtSavedSongOrTheNextSurvivor() {
        val saved = listOf(10L, 20L, 30L, 40L)
        assertEquals(2, resolveRestoreIndex(saved, savedIndex = 2, availableIds = listOf(10L, 20L, 30L, 40L)))
        // Song 30 was deleted: continue with 40, which is now at index 2.
        assertEquals(2, resolveRestoreIndex(saved, savedIndex = 2, availableIds = listOf(10L, 20L, 40L)))
        // Everything after the saved song is gone: fall back to the last one left.
        assertEquals(1, resolveRestoreIndex(saved, savedIndex = 2, availableIds = listOf(10L, 20L)))
        assertEquals(0, resolveRestoreIndex(saved, savedIndex = 0, availableIds = emptyList()))
    }

    @Test
    fun mediaStoreRescanNeverDeletesFolderSongs() {
        val existing = listOf(5L, 6L, 7L, -1234L, -99L)
        assertEquals(listOf(6L), staleMediaStoreIds(existing, scannedIds = setOf(5L, 7L)))
        // An empty MediaStore result clears MediaStore songs only.
        assertEquals(listOf(5L, 6L, 7L), staleMediaStoreIds(existing, scannedIds = emptySet()))
    }

    @Test
    fun browseIdsParseBackToTheirNodes() {
        assertEquals(MediaNode.Root, MediaIds.parse(MediaIds.ROOT))
        assertEquals(MediaNode.ShuffleAll, MediaIds.parse(MediaIds.SHUFFLE_ALL))
        assertEquals(MediaNode.PlaylistNode("smart_daily_mix"), MediaIds.parse(MediaIds.playlist("smart_daily_mix")))
        assertEquals(MediaNode.Leaf("playlist:custom_ab-12", -77L), MediaIds.parse(MediaIds.leaf(MediaIds.playlist("custom_ab-12"), -77L)))
        assertEquals(MediaNode.Leaf(MediaIds.LIKED, 5L), MediaIds.parse(MediaIds.leaf(MediaIds.LIKED, 5L)))
        assertEquals(MediaNode.SongItem(123L), MediaIds.parse("123"))
        assertEquals(MediaNode.Unknown, MediaIds.parse("play|liked|notanumber"))
        assertEquals(MediaNode.Unknown, MediaIds.parse("external:content://x"))
    }

    @Test
    fun pagingFollowsMedia3Conventions() {
        val items = (1..25).toList()
        assertEquals((11..20).toList(), items.page(page = 1, pageSize = 10))
        assertEquals((21..25).toList(), items.page(page = 2, pageSize = 10))
        assertEquals(emptyList<Int>(), items.page(page = 3, pageSize = 10))
        assertEquals(items, items.page(page = 0, pageSize = Int.MAX_VALUE))
    }

    @Test
    fun artworkIsDecodedNearTheDrawnSize() {
        assertEquals(1, sampleSize(300, 300, 256))
        assertEquals(8, sampleSize(3000, 3000, 256))
        assertEquals(4, sampleSize(1200, 1600, 256))
        assertEquals(1, sampleSize(0, 0, 256))
    }
}
