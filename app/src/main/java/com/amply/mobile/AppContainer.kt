package com.amply.mobile

import android.content.Context
import androidx.room.Room
import com.amply.mobile.data.local.AmplyDatabase
import com.amply.mobile.data.repository.LibraryRepository
import com.amply.mobile.data.repository.PlaylistRepository
import com.amply.mobile.data.repository.SettingsRepository
import com.amply.mobile.lyrics.LyricsRepository
import com.amply.mobile.metadata.MetadataRepository
import com.amply.mobile.playback.PlaybackConnection
import com.amply.mobile.playlist.PlaylistEngine
import com.amply.mobile.scan.MusicScanner

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AmplyDatabase = Room.databaseBuilder(
        appContext,
        AmplyDatabase::class.java,
        "amply.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    private val scanner = MusicScanner(appContext, database)
    private val playlistEngine = PlaylistEngine()

    val libraryRepository = LibraryRepository(database.songDao(), scanner)
    val playlistRepository = PlaylistRepository(database, playlistEngine)
    val lyricsRepository = LyricsRepository(database.lyricsDao())
    val metadataRepository = MetadataRepository(database.metadataDao(), database.songDao(), database.artistInfoDao())
    val settingsRepository = SettingsRepository(appContext, database.settingsDao())
    val playbackConnection = PlaybackConnection(appContext, database.queueDao())
}
