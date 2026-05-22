package com.amply.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        LyricsEntity::class,
        GenreCacheEntity::class,
        ArtistInfoEntity::class,
        QueueStateEntity::class,
        SettingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AmplyDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun metadataDao(): MetadataDao
    abstract fun artistInfoDao(): ArtistInfoDao
    abstract fun settingsDao(): SettingsDao
    abstract fun queueDao(): QueueDao
}
