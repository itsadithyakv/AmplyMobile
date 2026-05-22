package com.amply.mobile.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    fun observeLyrics(songId: Long): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun lyricsFor(songId: Long): LyricsEntity?

    @Upsert
    suspend fun upsertLyrics(entity: LyricsEntity)
}
