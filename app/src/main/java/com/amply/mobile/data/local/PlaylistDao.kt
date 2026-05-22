package com.amply.mobile.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY type DESC, updatedAtSec DESC, name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun playlistById(playlistId: String): PlaylistEntity?

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder")
    suspend fun songIdsForPlaylist(playlistId: String): List<Long>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun observeSongIdsForPlaylist(playlistId: String): Flow<List<Long>>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertPlaylistSongs(songs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId AND type = 'custom'")
    suspend fun deleteCustomPlaylist(playlistId: String)

    @Query("DELETE FROM playlists WHERE type = 'smart'")
    suspend fun deleteSmartPlaylists()
}
