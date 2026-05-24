package com.amply.mobile.domain

enum class RepeatMode {
    Off,
    One,
    All,
}

enum class PlaylistType {
    Smart,
    Custom,
}

data class Song(
    val id: Long,
    val contentUri: String,
    val filename: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val manualGenre: String?,
    val durationMs: Long,
    val track: Int,
    val year: Int?,
    val albumId: Long?,
    val artworkUri: String?,
    val addedAtSec: Long,
    val lastModifiedSec: Long,
    val playCount: Int,
    val skipCount: Int,
    val totalPlayMs: Long,
    val lastPlayedAtSec: Long?,
    val favorite: Boolean,
) {
    val effectiveGenre: String = manualGenre?.takeIf { it.isNotBlank() } ?: genre
}

data class Album(
    val name: String,
    val artist: String,
    val artworkUri: String?,
    val songCount: Int,
)

data class Artist(
    val name: String,
    val songCount: Int,
    val artworkUri: String?,
)

data class Playlist(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val description: String,
    val songIds: List<Long>,
    val artworkUri: String?,
    val updatedAtSec: Long,
)

data class LyricsCandidate(
    val id: String,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationMs: Long?,
    val synced: Boolean,
    val raw: String,
    val preview: String,
)

data class CachedLyrics(
    val songId: Long,
    val raw: String,
    val synced: Boolean,
    val source: String,
    val candidateId: String?,
    val edited: Boolean,
    val updatedAtSec: Long,
)

data class ArtistInfo(
    val artist: String,
    val summary: String,
    val sourceUrl: String?,
    val imageUrl: String?,
    val fetchedAtSec: Long,
)

data class PlaybackQueue(
    val songIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: RepeatMode,
)

data class PlaybackStats(
    val playCount: Int,
    val skipCount: Int,
    val totalPlayMs: Long,
    val lastPlayedAtSec: Long?,
)

data class AppSettings(
    val metadataFetchPaused: Boolean = false,
    val discoveryIntensity: Float = 0.35f,
    val randomnessIntensity: Float = 0.30f,
    val preferSyncedLyrics: Boolean = true,
    val gaplessPlayback: Boolean = true,
    val crossfadeSeconds: Float = 0f,
    val equalizerEnabled: Boolean = false,
    val eqBass: Float = 0.50f,
    val eqMid: Float = 0.50f,
    val eqTreble: Float = 0.50f,
)

data class AmplyPlaybackState(
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
)
