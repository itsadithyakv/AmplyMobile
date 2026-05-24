package com.amply.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index("artist"),
        Index("album"),
        Index("genre"),
        Index("title"),
    ],
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val contentUri: String,
    val filename: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val manualGenre: String? = null,
    val durationMs: Long,
    val track: Int,
    val year: Int?,
    val albumId: Long?,
    val artworkUri: String?,
    val addedAtSec: Long,
    val lastModifiedSec: Long,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val totalPlayMs: Long = 0,
    val lastPlayedAtSec: Long? = null,
    val favorite: Boolean = false,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val description: String,
    val artworkUri: String?,
    val updatedAtSec: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId"), Index(value = ["playlistId", "sortOrder"])],
)
data class PlaylistSongEntity(
    val playlistId: String,
    val songId: Long,
    val sortOrder: Int,
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val raw: String,
    val synced: Boolean,
    val source: String,
    val candidateId: String?,
    val edited: Boolean,
    val updatedAtSec: Long,
)

@Entity(tableName = "genre_cache")
data class GenreCacheEntity(
    @PrimaryKey val songId: Long,
    val genre: String,
    val source: String,
    val fetchedAtSec: Long,
)

@Entity(tableName = "artist_info")
data class ArtistInfoEntity(
    @PrimaryKey val artist: String,
    val summary: String,
    val sourceUrl: String?,
    val imageUrl: String?,
    val fetchedAtSec: Long,
)

@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 1,
    val songIdsCsv: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: String,
    val updatedAtSec: Long,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
