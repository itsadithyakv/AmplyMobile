package com.amply.mobile.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MetadataDao {
    @Query("SELECT * FROM genre_cache WHERE songId = :songId")
    suspend fun genreFor(songId: Long): GenreCacheEntity?

    @Upsert
    suspend fun upsertGenre(entity: GenreCacheEntity)
}
