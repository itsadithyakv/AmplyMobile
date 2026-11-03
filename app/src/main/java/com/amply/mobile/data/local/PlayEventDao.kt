package com.amply.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity)

    @Query("SELECT * FROM play_events WHERE atMs >= :fromMs AND atMs < :toMs ORDER BY atMs")
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<PlayEventEntity>>

    @Query("SELECT * FROM play_events WHERE atMs >= :fromMs AND atMs < :toMs ORDER BY atMs")
    suspend fun between(fromMs: Long, toMs: Long): List<PlayEventEntity>

    @Query("SELECT COUNT(*) FROM play_events WHERE skipped = 0 AND atMs >= :fromMs AND atMs < :toMs")
    fun observePlaysBetween(fromMs: Long, toMs: Long): Flow<Int>
}

@Dao
interface RulePlaylistDao {
    @Query("SELECT * FROM rule_playlists ORDER BY updatedAtSec DESC")
    fun observeAll(): Flow<List<RulePlaylistEntity>>

    @androidx.room.Upsert
    suspend fun upsert(playlist: RulePlaylistEntity)

    @Query("DELETE FROM rule_playlists WHERE id = :id")
    suspend fun delete(id: String)
}
