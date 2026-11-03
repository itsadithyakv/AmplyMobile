package com.amply.mobile.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.amply.mobile.data.local.AmplyDatabase
import com.amply.mobile.data.local.SongEntity
import com.amply.mobile.data.local.songsByIdsChunked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Browse tree served to media browsers: Android Auto, the Assistant ("play X on Amply") and the
 * system media resumption carousel. The layout is deliberately shallow (car screens allow only a
 * few levels and short lists):
 *
 * ```
 * root
 *  |- Shuffle all            (playable)
 *  |- Recently played        -> songs
 *  |- Liked songs            -> songs
 *  |- Playlists              -> playlist:<id> -> songs
 *  `- All songs              -> songs (paged)
 * ```
 */
class LibraryTree(private val database: AmplyDatabase) {
    private val songDao get() = database.songDao()
    private val playlistDao get() = database.playlistDao()

    fun rootItem(): MediaItem = folder(MediaIds.ROOT, "Amply", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    fun recentRootItem(): MediaItem = folder(MediaIds.RECENT_ROOT, "Recent", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    suspend fun item(mediaId: String): MediaItem? = withContext(Dispatchers.IO) {
        when (val node = MediaIds.parse(mediaId)) {
            MediaNode.Root -> rootItem()
            MediaNode.RecentRoot -> recentRootItem()
            MediaNode.Playlists, MediaNode.Liked, MediaNode.Recent, MediaNode.AllSongs, MediaNode.ShuffleAll ->
                rootChildren().firstOrNull { it.mediaId == mediaId }
            is MediaNode.PlaylistNode -> playlistDao.playlistById(node.playlistId)?.let {
                folder(mediaId, it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }
            is MediaNode.Leaf -> songDao.songById(node.songId)?.toMediaItem()
            is MediaNode.SongItem -> songDao.songById(node.songId)?.toMediaItem()
            MediaNode.Unknown -> null
        }
    }

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val all = when (val node = MediaIds.parse(parentId)) {
            MediaNode.Root -> rootChildren()
            MediaNode.RecentRoot -> recentChildren()
            MediaNode.Playlists -> playlistDao.playablePlaylists(MAX_PLAYLISTS).map {
                folder(MediaIds.playlist(it.id), it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }
            MediaNode.Liked, MediaNode.Recent, MediaNode.AllSongs, is MediaNode.PlaylistNode ->
                songsFor(parentId).map { it.toLeaf(parentId) }
            else -> emptyList()
        }
        all.page(page, pageSize)
    }

    suspend fun search(query: String, limit: Int = MAX_SEARCH_RESULTS): List<SongEntity> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) songDao.recentlyPlayed(limit) else songDao.searchSongsOnce(trimmed, limit)
    }

    /**
     * Turns what a controller asked to play into playable queue items. Items that already carry a
     * uri (our own UI and widget) pass through untouched; Auto and Assistant only send media ids or
     * a search query.
     */
    suspend fun resolve(item: MediaItem): List<MediaItem> = withContext(Dispatchers.IO) {
        if (item.localConfiguration != null) return@withContext listOf(item)
        item.requestMetadata.searchQuery?.let { query ->
            return@withContext search(query).map { it.toMediaItem() }
        }
        when (val node = MediaIds.parse(item.mediaId)) {
            is MediaNode.SongItem -> listOfNotNull(songDao.songById(node.songId)?.toMediaItem())
            is MediaNode.Leaf -> listOfNotNull(songDao.songById(node.songId)?.toMediaItem())
            MediaNode.ShuffleAll -> songDao.allSongs().shuffled().map { it.toMediaItem() }
            MediaNode.Liked, MediaNode.Recent, MediaNode.AllSongs, is MediaNode.PlaylistNode ->
                songsFor(item.mediaId).map { it.toMediaItem() }
            else -> emptyList()
        }
    }

    /**
     * A single song picked from a list on the car screen should queue the whole list, starting at
     * that song, like tapping a song in the phone UI does.
     */
    suspend fun expandSelection(item: MediaItem): Pair<List<MediaItem>, Int>? = withContext(Dispatchers.IO) {
        val leaf = MediaIds.parse(item.mediaId) as? MediaNode.Leaf ?: return@withContext null
        val songs = songsFor(leaf.parentId)
        val index = songs.indexOfFirst { it.id == leaf.songId }
        if (index < 0) null else songs.map { it.toMediaItem() } to index
    }

    private suspend fun songsFor(parentId: String): List<SongEntity> = when (val node = MediaIds.parse(parentId)) {
        MediaNode.Liked -> songDao.favoriteSongs()
        MediaNode.Recent -> songDao.recentlyPlayed(MAX_RECENT)
        MediaNode.AllSongs -> songDao.allSongs()
        is MediaNode.PlaylistNode -> songDao.songsByIdsChunked(playlistDao.songIdsForPlaylist(node.playlistId))
        else -> emptyList()
    }

    private suspend fun recentChildren(): List<MediaItem> {
        val queue = database.queueDao().queue() ?: return emptyList()
        val id = decodeQueueIds(queue.songIdsCsv).getOrNull(queue.currentIndex) ?: return emptyList()
        return listOfNotNull(songDao.songById(id)?.toMediaItem())
    }

    private fun rootChildren(): List<MediaItem> = listOf(
        playableAction(MediaIds.SHUFFLE_ALL, "Shuffle all"),
        folder(MediaIds.RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        folder(MediaIds.LIKED, "Liked songs", MediaMetadata.MEDIA_TYPE_PLAYLIST),
        folder(MediaIds.PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        folder(MediaIds.ALL_SONGS, "All songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
    )

    private fun SongEntity.toLeaf(parentId: String): MediaItem =
        toMediaItem().buildUpon().setMediaId(MediaIds.leaf(parentId, id)).build()

    private fun folder(id: String, title: String, type: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(type == MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setMediaType(type)
                    .build(),
            )
            .build()

    private fun playableAction(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build(),
            )
            .build()

    private companion object {
        const val MAX_PLAYLISTS = 40
        const val MAX_RECENT = 50
        const val MAX_SEARCH_RESULTS = 50
    }
}

/** Media3 pages are zero-based; a non-positive or huge page size means "everything". */
fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return this
    val from = page.coerceAtLeast(0).toLong() * pageSize
    if (from >= size) return emptyList()
    return subList(from.toInt(), minOf(size, from.toInt() + pageSize))
}
