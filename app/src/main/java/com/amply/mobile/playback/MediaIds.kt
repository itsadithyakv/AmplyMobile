package com.amply.mobile.playback

/**
 * Media ids for the browse tree that Android Auto, Assistant and the system media resumption UI
 * see. Queue items keep the bare numeric song id (see MediaItems.kt); browse leaves carry the list
 * they were picked from, so choosing a song in a playlist on the car screen queues that playlist.
 */
object MediaIds {
    const val ROOT = "root"
    const val RECENT_ROOT = "recent_root"
    const val PLAYLISTS = "playlists"
    const val LIKED = "liked"
    const val RECENT = "recent"
    const val ALL_SONGS = "songs"
    const val SHUFFLE_ALL = "shuffle_all"
    private const val PLAYLIST_PREFIX = "playlist:"
    private const val LEAF_PREFIX = "play|"

    fun playlist(playlistId: String): String = PLAYLIST_PREFIX + playlistId

    /** A playable song inside [parentId]; `|` never appears in song or playlist ids. */
    fun leaf(parentId: String, songId: Long): String = "$LEAF_PREFIX$parentId|$songId"

    fun parse(mediaId: String): MediaNode = when {
        mediaId == ROOT -> MediaNode.Root
        mediaId == RECENT_ROOT -> MediaNode.RecentRoot
        mediaId == PLAYLISTS -> MediaNode.Playlists
        mediaId == LIKED -> MediaNode.Liked
        mediaId == RECENT -> MediaNode.Recent
        mediaId == ALL_SONGS -> MediaNode.AllSongs
        mediaId == SHUFFLE_ALL -> MediaNode.ShuffleAll
        mediaId.startsWith(PLAYLIST_PREFIX) -> MediaNode.PlaylistNode(mediaId.removePrefix(PLAYLIST_PREFIX))
        mediaId.startsWith(LEAF_PREFIX) -> {
            val parent = mediaId.removePrefix(LEAF_PREFIX).substringBeforeLast('|')
            val songId = mediaId.substringAfterLast('|').toLongOrNull()
            if (songId == null || parent.isEmpty()) MediaNode.Unknown else MediaNode.Leaf(parent, songId)
        }
        mediaId.toLongOrNull() != null -> MediaNode.SongItem(mediaId.toLong())
        else -> MediaNode.Unknown
    }
}

sealed interface MediaNode {
    data object Root : MediaNode
    data object RecentRoot : MediaNode
    data object Playlists : MediaNode
    data object Liked : MediaNode
    data object Recent : MediaNode
    data object AllSongs : MediaNode
    data object ShuffleAll : MediaNode
    data class PlaylistNode(val playlistId: String) : MediaNode
    data class Leaf(val parentId: String, val songId: Long) : MediaNode
    data class SongItem(val songId: Long) : MediaNode
    data object Unknown : MediaNode
}
