package com.amply.mobile

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amply.mobile.data.local.AmplyDatabase
import com.amply.mobile.data.repository.LibraryRepository
import com.amply.mobile.data.repository.PlaylistRepository
import com.amply.mobile.data.repository.SettingsRepository
import com.amply.mobile.lyrics.LyricsRepository
import com.amply.mobile.metadata.MetadataRepository
import com.amply.mobile.playback.PlaybackConnection
import com.amply.mobile.playback.QueueStore
import com.amply.mobile.pro.ProStore
import com.amply.mobile.playlist.PlaylistEngine
import com.amply.mobile.scan.MusicScanner

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AmplyDatabase = Room.databaseBuilder(
        appContext,
        AmplyDatabase::class.java,
        "amply.db",
    ).addMigrations(MIGRATION_2_3, MIGRATION_3_4).build()

    private val scanner = MusicScanner(appContext, database)
    private val playlistEngine = PlaylistEngine()

    val libraryRepository = LibraryRepository(database.songDao(), scanner)
    val playlistRepository = PlaylistRepository(database, playlistEngine)
    val lyricsRepository = LyricsRepository(database.lyricsDao())
    val metadataRepository = MetadataRepository(database.metadataDao(), database.songDao(), database.artistInfoDao())
    val settingsRepository = SettingsRepository(appContext, database.settingsDao())
    val queueStore = QueueStore(database.queueDao(), database.songDao())
    val proStore = ProStore(appContext)

    // Lazy: building it binds the playback service, which workers and widget broadcasts that
    // start the process must not do just by touching the container.
    val playbackConnection by lazy { PlaybackConnection(appContext, queueStore) }

    private companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artist_info ADD COLUMN imageUrl TEXT")
            }
        }

        /** Adds the listening log and rule playlists; existing libraries are kept as they are. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`songId` INTEGER NOT NULL, `atMs` INTEGER NOT NULL, `listenedMs` INTEGER NOT NULL, `skipped` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_atMs` ON `play_events` (`atMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_songId` ON `play_events` (`songId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rule_playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `rulesJson` TEXT NOT NULL, " +
                        "`matchAll` INTEGER NOT NULL, `limit` INTEGER NOT NULL, `sort` TEXT NOT NULL, `updatedAtSec` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
