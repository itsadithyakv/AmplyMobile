package com.amply.mobile.data.repository

import androidx.room.withTransaction
import com.amply.mobile.data.local.AmplyDatabase
import com.amply.mobile.data.local.PlaylistEntity
import com.amply.mobile.data.local.PlaylistSongEntity
import com.amply.mobile.data.local.toDomain
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.Song
import com.amply.mobile.playlist.PlaylistEngine
import com.amply.mobile.util.nowSec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class PlaylistRepository(
    private val database: AmplyDatabase,
    private val engine: PlaylistEngine,
) {
    private val playlistDao = database.playlistDao()
    private val songDao = database.songDao()

    val playlists: Flow<List<Playlist>> = playlistDao.observePlaylists().map { entities ->
        entities.map { entity -> entity.toDomain(playlistDao.songIdsForPlaylist(entity.id)) }
    }

    fun songsForPlaylist(playlistId: String): Flow<List<Song>> =
        combine(playlistDao.observeSongIdsForPlaylist(playlistId), songDao.observeSongs()) { ids, songs ->
            val byId = songs.associateBy { it.id }
            ids.mapNotNull { byId[it]?.toDomain() }
        }

    suspend fun regenerateSmartPlaylists(songs: List<Song>) {
        val generated = engine.generateSmartPlaylists(songs)
        database.withTransaction {
            playlistDao.deleteSmartPlaylists()
            generated.forEach { playlist ->
                playlistDao.upsertPlaylist(playlist.toEntity())
                playlistDao.clearPlaylistSongs(playlist.id)
                playlistDao.upsertPlaylistSongs(
                    playlist.songIds.mapIndexed { index, songId ->
                        PlaylistSongEntity(playlist.id, songId, index)
                    },
                )
            }
        }
    }

    suspend fun createCustomPlaylist(name: String, songIds: List<Long> = emptyList()): Playlist {
        val playlist = Playlist(
            id = "custom_${UUID.randomUUID()}",
            name = name.trim().ifBlank { "New Playlist" },
            type = PlaylistType.Custom,
            description = "Custom playlist",
            songIds = songIds.distinct(),
            artworkUri = null,
            updatedAtSec = nowSec(),
        )
        replacePlaylist(playlist)
        return playlist
    }

    suspend fun replacePlaylist(playlist: Playlist) {
        database.withTransaction {
            playlistDao.upsertPlaylist(playlist.toEntity())
            playlistDao.clearPlaylistSongs(playlist.id)
            playlistDao.upsertPlaylistSongs(
                playlist.songIds.distinct().mapIndexed { index, songId ->
                    PlaylistSongEntity(playlist.id, songId, index)
                },
            )
        }
    }

    suspend fun addSongToPlaylist(playlist: Playlist, songId: Long) {
        if (playlist.type != PlaylistType.Custom || playlist.songIds.contains(songId)) return
        replacePlaylist(playlist.copy(songIds = playlist.songIds + songId, updatedAtSec = nowSec()))
    }

    suspend fun deleteCustomPlaylist(playlistId: String) {
        database.withTransaction {
            playlistDao.clearPlaylistSongs(playlistId)
            playlistDao.deleteCustomPlaylist(playlistId)
        }
    }

    private fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
        id = id,
        name = name,
        type = if (type == PlaylistType.Custom) "custom" else "smart",
        description = description,
        artworkUri = artworkUri,
        updatedAtSec = updatedAtSec,
    )
}
