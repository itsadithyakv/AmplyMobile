package com.amply.mobile.data.repository

import android.net.Uri
import com.amply.mobile.data.local.SongDao
import com.amply.mobile.data.local.toDomain
import com.amply.mobile.domain.Song
import com.amply.mobile.scan.MusicScanner
import com.amply.mobile.util.nowSec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(
    private val songDao: SongDao,
    private val scanner: MusicScanner,
) {
    val songs: Flow<List<Song>> = songDao.observeSongs().map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Song>> =
        if (query.isBlank()) songs else songDao.searchSongs(query.trim()).map { list -> list.map { it.toDomain() } }

    suspend fun song(songId: Long): Song? = songDao.songById(songId)?.toDomain()

    suspend fun allSongs(): List<Song> = songDao.allSongs().map { it.toDomain() }

    suspend fun songsByIds(songIds: List<Long>): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        val byId = songDao.songsByIds(songIds).associateBy { it.id }
        return songIds.mapNotNull { byId[it]?.toDomain() }
    }

    suspend fun scanLibrary(): Int = scanner.scan()

    suspend fun scanFolder(uri: Uri): Int = scanner.scanTree(uri)

    suspend fun toggleFavorite(song: Song) {
        songDao.setFavorite(song.id, !song.favorite)
    }

    suspend fun recordPlay(songId: Long, listenedMs: Long) {
        songDao.recordPlay(songId, nowSec(), listenedMs.coerceAtLeast(0L))
    }

    suspend fun recordSkip(songId: Long) {
        songDao.recordSkip(songId)
    }

    suspend fun setManualGenre(songId: Long, genre: String?) {
        songDao.setManualGenre(songId, genre?.trim()?.takeIf { it.isNotBlank() })
    }
}
