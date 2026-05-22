package com.amply.mobile.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_state WHERE id = 1")
    fun observeQueue(): Flow<QueueStateEntity?>

    @Query("SELECT * FROM queue_state WHERE id = 1")
    suspend fun queue(): QueueStateEntity?

    @Upsert
    suspend fun upsert(queue: QueueStateEntity)
}
