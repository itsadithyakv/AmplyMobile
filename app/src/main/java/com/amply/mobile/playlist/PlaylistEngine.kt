package com.amply.mobile.playlist

import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.Song
import com.amply.mobile.metadata.normalizeGenreBucket
import com.amply.mobile.util.nowSec
import kotlin.math.abs
import kotlin.math.sqrt

class PlaylistEngine {
    fun generateSmartPlaylists(
        songs: List<Song>,
        discovery: Float = 0.35f,
        randomness: Float = 0.30f,
        seed: Long = nowSec() / 86_400L,
    ): List<Playlist> {
        if (songs.isEmpty()) return emptyList()

        val targetCount = songs.size.coerceIn(18, 80)
        val now = nowSec()
        val playlists = mutableListOf<Playlist>()

        playlists += smart(
            id = "smart_daily_mix",
            name = "Daily Mix",
            description = "A balanced local mix refreshed from your library.",
            songs = spreadByGenre(
                weightedPick(songs, targetCount, seed, "daily") { song ->
                    tasteScore(song, now, discovery, randomness) + if (song.favorite) 1.2f else 0f
                },
            ),
        )

        playlists += smart(
            id = "smart_on_repeat",
            name = "On Repeat",
            description = "Songs you come back to most often.",
            songs = weightedPick(
                songs.filter { it.playCount > 0 }.ifEmpty { songs },
                targetCount,
                seed,
                "repeat",
            ) { song -> song.playCount * 2f + recentBoost(song, now) },
        )

        playlists += smart(
            id = "smart_road_mix",
            name = "Road Mix",
            description = "Upbeat tracks for drives and long walks.",
            songs = weightedPick(songs, targetCount, seed, "road") { song ->
                tasteScore(song, now, discovery, randomness) + energyScore(song)
            },
        )

        playlists += smart(
            id = "smart_recently_added",
            name = "Recently Added",
            description = "Newer songs from your phone.",
            songs = songs.sortedByDescending { it.addedAtSec }.take(targetCount),
        )

        playlists += smart(
            id = "smart_favorites",
            name = "Favorites",
            description = "Songs you marked as favorites.",
            songs = shuffleStable(songs.filter { it.favorite }, seed, "favorites").take(targetCount),
        )

        playlists += smart(
            id = "smart_rediscover",
            name = "Rediscover",
            description = "Local tracks you have not played recently.",
            songs = songs.sortedBy { it.lastPlayedAtSec ?: 0L }.take(targetCount),
        )

        playlists += smart(
            id = "smart_quick_hits",
            name = "Quick Hits",
            description = "Short tracks under three minutes.",
            songs = shuffleStable(songs.filter { it.durationMs in 1..180_000L }, seed, "quick").take(targetCount),
        )

        val genreMixes = songs
            .groupBy { normalizeGenreBucket(it.effectiveGenre) }
            .filterKeys { it != "Unknown" }
            .entries
            .sortedByDescending { it.value.size + it.value.sumOf { song -> song.playCount } }
            .take(6)
            .map { (genre, genreSongs) ->
                smart(
                    id = "smart_genre_${genre.slug()}",
                    name = "$genre Mix",
                    description = "A mix based on your $genre tracks.",
                    songs = weightedPick(genreSongs, targetCount, seed, "genre:$genre") { tasteScore(it, now, discovery, randomness) },
                )
            }
        playlists += genreMixes

        val artistMixes = songs
            .groupBy { primaryArtistName(it.artist) }
            .filterValues { it.size >= 3 }
            .entries
            .sortedByDescending { it.value.size + it.value.sumOf { song -> song.playCount } }
            .take(6)
            .map { (artist, artistSongs) ->
                smart(
                    id = "smart_artist_${artist.slug()}",
                    name = "$artist Radio",
                    description = "Songs from $artist in your library.",
                    songs = shuffleStable(artistSongs, seed, "artist:$artist").take(targetCount),
                )
            }
        playlists += artistMixes

        return playlists.filter { it.songIds.isNotEmpty() }.distinctBy { it.id }
    }

    private fun smart(id: String, name: String, description: String, songs: List<Song>): Playlist =
        Playlist(
            id = id,
            name = name,
            type = PlaylistType.Smart,
            description = description,
            songIds = songs.map { it.id }.distinct(),
            artworkUri = songs.firstOrNull { it.artworkUri != null }?.artworkUri,
            updatedAtSec = nowSec(),
        )

    private fun tasteScore(song: Song, now: Long, discovery: Float, randomness: Float): Float {
        val play = sqrt(song.playCount.toFloat().coerceAtLeast(0f)) * (1.2f - randomness * 0.5f)
        val favorite = if (song.favorite) 1.5f else 0f
        val skipped = song.skipCount * 0.6f
        val recentPenalty = song.lastPlayedAtSec?.let { ((now - it) / 86_400f).coerceIn(0f, 5f) } ?: 5f
        val explore = if (song.playCount <= 2) discovery * 2f else 0f
        return 1f + play + favorite + recentPenalty * 0.15f + explore - skipped
    }

    private fun recentBoost(song: Song, now: Long): Float {
        val lastPlayed = song.lastPlayedAtSec ?: return 0f
        val days = abs(now - lastPlayed) / 86_400f
        return (8f - days).coerceAtLeast(0f)
    }

    private fun energyScore(song: Song): Float {
        val genre = normalizeGenreBucket(song.effectiveGenre).lowercase()
        var score = 0f
        if (genre in setOf("pop", "hip-hop", "rap", "rock", "alternative", "electronic", "latin")) score += 2.5f
        if (song.durationMs in 120_000L..300_000L) score += 1f
        return score
    }

    private fun weightedPick(
        pool: List<Song>,
        count: Int,
        seed: Long,
        salt: String,
        score: (Song) -> Float,
    ): List<Song> {
        val random = java.util.Random(hash("$salt:$seed").toLong())
        val remaining = pool.toMutableList()
        val picked = mutableListOf<Song>()
        while (remaining.isNotEmpty() && picked.size < count) {
            val weights = remaining.map { score(it).coerceAtLeast(0.05f) }
            val total = weights.sum()
            var marker = random.nextFloat() * total
            var index = 0
            while (index < weights.lastIndex && marker > weights[index]) {
                marker -= weights[index]
                index += 1
            }
            picked += remaining.removeAt(index)
        }
        return spreadByArtist(picked)
    }

    private fun spreadByGenre(songs: List<Song>): List<Song> = spreadByKey(songs) { normalizeGenreBucket(it.effectiveGenre) }

    private fun spreadByArtist(songs: List<Song>): List<Song> = spreadByKey(songs) { primaryArtistName(it.artist) }

    private fun spreadByKey(songs: List<Song>, keyFor: (Song) -> String): List<Song> {
        val buckets = songs.groupBy(keyFor).mapValues { it.value.toMutableList() }.toMutableMap()
        val result = mutableListOf<Song>()
        var lastKey: String? = null
        while (buckets.isNotEmpty()) {
            val next = buckets.entries
                .filter { it.key != lastKey }
                .maxByOrNull { it.value.size }
                ?: buckets.entries.maxByOrNull { it.value.size }
                ?: break
            result += next.value.removeAt(0)
            lastKey = next.key
            if (next.value.isEmpty()) buckets.remove(next.key)
        }
        return result
    }

    private fun shuffleStable(songs: List<Song>, seed: Long, salt: String): List<Song> =
        songs.shuffled(java.util.Random(hash("$salt:$seed").toLong()))

    private fun hash(value: String): Int {
        var h = 0
        value.forEach { h = 31 * h + it.code }
        return abs(h)
    }
}

fun primaryArtistName(value: String): String =
    value.split(" feat. ", " ft. ", " featuring ", ",", " & ", " x ", " / ")
        .firstOrNull()
        ?.trim()
        .orEmpty()
        .ifBlank { "Unknown Artist" }

private fun String.slug(): String =
    lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-').ifBlank { "mix" }
