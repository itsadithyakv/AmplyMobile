package com.amply.mobile.playlist

import com.amply.mobile.domain.Song
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistEngineTest {
    @Test
    fun createsExpectedSmartPlaylists() {
        val songs = (1L..40L).map { index ->
            song(
                id = index,
                title = "Song $index",
                artist = if (index % 2L == 0L) "Artist A" else "Artist B",
                genre = if (index % 3L == 0L) "Hip Hop" else "Pop",
                favorite = index % 5L == 0L,
                playCount = index.toInt() % 8,
            )
        }

        val playlists = PlaylistEngine().generateSmartPlaylists(songs, seed = 42L)

        assertTrue(playlists.any { it.id == "smart_daily_mix" })
        assertTrue(playlists.any { it.id == "smart_road_mix" })
        assertTrue(playlists.any { it.id.startsWith("smart_genre_") })
        assertTrue(playlists.all { it.songIds.isNotEmpty() })
    }

    private fun song(
        id: Long,
        title: String,
        artist: String,
        genre: String,
        favorite: Boolean,
        playCount: Int,
    ): Song = Song(
        id = id,
        contentUri = "content://songs/$id",
        filename = "$title.mp3",
        title = title,
        artist = artist,
        album = "Album",
        genre = genre,
        manualGenre = null,
        durationMs = 200_000L,
        track = id.toInt(),
        year = 2024,
        albumId = 1,
        artworkUri = null,
        addedAtSec = id,
        lastModifiedSec = id,
        playCount = playCount,
        skipCount = 0,
        totalPlayMs = 0,
        lastPlayedAtSec = null,
        favorite = favorite,
    )
}
