package com.amply.mobile.scan

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.amply.mobile.data.local.AmplyDatabase
import com.amply.mobile.data.local.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class MusicScanner(
    private val context: Context,
    private val database: AmplyDatabase,
) {
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        rescanPublicMusicDirectory()
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = listOfNotNull(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.Audio.Media.GENRE else null,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
        ).toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        val songs = mutableListOf<SongEntity>()

        resolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val filenameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val genreIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            } else {
                -1
            }
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val albumId = cursor.getLong(albumIdIndex).takeIf { it > 0L }
                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                val artworkUri = albumId?.let {
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), it).toString()
                }
                songs += SongEntity(
                    id = id,
                    contentUri = contentUri,
                    filename = cursor.getString(filenameIndex).orUnknown("Unknown file"),
                    title = cursor.getString(titleIndex).orUnknown("Unknown Title"),
                    artist = cursor.getString(artistIndex).orUnknown("Unknown Artist"),
                    album = cursor.getString(albumIndex).orUnknown("Unknown Album"),
                    albumId = albumId,
                    genre = if (genreIndex >= 0) cursor.getString(genreIndex).orUnknown("Unknown") else "Unknown",
                    durationMs = cursor.getLong(durationIndex).coerceAtLeast(0L),
                    track = cursor.getInt(trackIndex).coerceAtLeast(0),
                    year = cursor.getInt(yearIndex).takeIf { it > 0 },
                    artworkUri = artworkUri,
                    addedAtSec = cursor.getLong(addedIndex).coerceAtLeast(0L),
                    lastModifiedSec = cursor.getLong(modifiedIndex).coerceAtLeast(0L),
                )
            }
        }

        database.withTransaction {
            if (songs.isEmpty() && canTrustEmptyMediaStoreScan()) {
                database.songDao().deleteAllSongs()
            } else if (songs.isNotEmpty()) {
                database.songDao().upsertSongs(songs)
                database.songDao().deleteSongsNotIn(songs.map { it.id })
            }
        }
        songs.size
    }

    private fun canTrustEmptyMediaStoreScan(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        val songs = mutableListOf<SongEntity>()
        root.walkAudioFiles { file ->
            val entity = file.toSongEntity()
            if (entity != null) songs += entity
        }
        if (songs.isNotEmpty()) {
            database.songDao().upsertSongs(songs)
        }
        songs.size
    }

    private suspend fun rescanPublicMusicDirectory() {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val paths = musicDir
            ?.takeIf { it.exists() }
            ?.walkTopDown()
            ?.filter { it.isFile && it.extension.lowercase() in audioExtensions }
            ?.map(File::getAbsolutePath)
            ?.take(3_000)
            ?.toList()
            .orEmpty()
        if (paths.isEmpty()) return
        suspendCancellableCoroutine<Unit> { continuation ->
            val remaining = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (remaining.decrementAndGet() == 0 && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    private fun DocumentFile.walkAudioFiles(onFile: (DocumentFile) -> Unit) {
        listFiles().forEach { child ->
            when {
                child.isDirectory -> child.walkAudioFiles(onFile)
                child.isFile && child.isAudioFile -> onFile(child)
            }
        }
    }

    private val DocumentFile.isAudioFile: Boolean
        get() {
            val type = type.orEmpty().lowercase()
            val extension = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
            return type.startsWith("audio/") || extension in audioExtensions
        }

    private fun DocumentFile.toSongEntity(): SongEntity? {
        val uri = uri
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractLong(MediaMetadataRetriever.METADATA_KEY_DURATION).coerceAtLeast(0L)
            if (duration <= 10_000L) return@runCatching null
            val title = retriever.extractString(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: name?.substringBeforeLast('.')
                ?: "Unknown Title"
            SongEntity(
                id = stableSongId(uri),
                contentUri = uri.toString(),
                filename = name.orUnknown("Unknown file"),
                title = title.orUnknown("Unknown Title"),
                artist = retriever.extractString(MediaMetadataRetriever.METADATA_KEY_ARTIST).orUnknown("Unknown Artist"),
                album = retriever.extractString(MediaMetadataRetriever.METADATA_KEY_ALBUM).orUnknown("Unknown Album"),
                albumId = null,
                genre = retriever.extractString(MediaMetadataRetriever.METADATA_KEY_GENRE).orUnknown("Unknown"),
                durationMs = duration,
                track = retriever.extractInt(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                year = retriever.extractInt(MediaMetadataRetriever.METADATA_KEY_YEAR).takeIf { it > 0 },
                artworkUri = null,
                addedAtSec = System.currentTimeMillis() / 1000L,
                lastModifiedSec = lastModified().takeIf { it > 0L }?.div(1000L) ?: System.currentTimeMillis() / 1000L,
            )
        }.getOrNull().also {
            retriever.release()
        }
    }

    private fun stableSongId(uri: Uri): Long {
        var hash = 1125899906842597L
        uri.toString().forEach { hash = 31L * hash + it.code }
        return -kotlin.math.abs(hash.takeIf { it != Long.MIN_VALUE } ?: Long.MAX_VALUE)
    }

    private fun MediaMetadataRetriever.extractString(key: Int): String? =
        extractMetadata(key)?.trim()?.takeIf { it.isNotBlank() }

    private fun MediaMetadataRetriever.extractLong(key: Int): Long =
        extractString(key)?.toLongOrNull() ?: 0L

    private fun MediaMetadataRetriever.extractInt(key: Int): Int =
        extractString(key)
            ?.substringBefore('/')
            ?.trim()
            ?.toIntOrNull()
            ?: 0

    private companion object {
        val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
    }
}

private fun String?.orUnknown(fallback: String): String = this?.trim()?.takeIf { it.isNotBlank() } ?: fallback
