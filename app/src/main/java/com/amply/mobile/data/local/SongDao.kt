package com.amply.mobile.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    suspend fun allSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun songById(songId: Long): SongEntity?

    @Query(
        """
        SELECT * FROM songs
        WHERE id != :excludeSongId
        ORDER BY
            CASE WHEN lastPlayedAtSec IS NULL THEN 0 ELSE 1 END DESC,
            lastPlayedAtSec DESC,
            addedAtSec DESC
        LIMIT :limit
        """,
    )
    suspend fun recentSongsForWidget(excludeSongId: Long, limit: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun songsByIds(songIds: List<Long>): List<SongEntity>

    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%'
            OR artist LIKE '%' || :query || '%'
            OR album LIKE '%' || :query || '%'
            OR genre LIKE '%' || :query || '%'
            OR manualGenre LIKE '%' || :query || '%'
        ORDER BY playCount DESC, title COLLATE NOCASE
        """,
    )
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id NOT IN (:keepIds)")
    suspend fun deleteSongsNotIn(keepIds: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Query("UPDATE songs SET favorite = :favorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, favorite: Boolean)

    @Query(
        """
        UPDATE songs
        SET playCount = playCount + 1,
            lastPlayedAtSec = :playedAtSec,
            totalPlayMs = totalPlayMs + :listenedMs
        WHERE id = :songId
        """,
    )
    suspend fun recordPlay(songId: Long, playedAtSec: Long, listenedMs: Long)

    @Query("UPDATE songs SET skipCount = skipCount + 1 WHERE id = :songId")
    suspend fun recordSkip(songId: Long)

    @Query("UPDATE songs SET manualGenre = :genre WHERE id = :songId")
    suspend fun setManualGenre(songId: Long, genre: String?)
}
