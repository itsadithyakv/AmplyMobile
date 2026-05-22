package com.amply.mobile.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ArtistInfoDao {
    @Query("SELECT * FROM artist_info WHERE artist = :artist")
    suspend fun infoFor(artist: String): ArtistInfoEntity?

    @Upsert
    suspend fun upsert(info: ArtistInfoEntity)
}
