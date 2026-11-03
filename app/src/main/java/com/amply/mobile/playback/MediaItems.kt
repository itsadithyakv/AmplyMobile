package com.amply.mobile.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.amply.mobile.data.local.SongEntity
import com.amply.mobile.domain.Song

/**
 * Queue items always use the bare song id as media id, so anything reading the player (UI, widget,
 * stats) can map an item back to a library row with `mediaId.toLongOrNull()`.
 */
fun Song.toMediaItem(): MediaItem =
    songMediaItem(id, contentUri, title, artist, album, artworkUri, durationMs)

fun SongEntity.toMediaItem(): MediaItem =
    songMediaItem(id, contentUri, title, artist, album, artworkUri, durationMs)

private fun songMediaItem(
    id: Long,
    contentUri: String,
    title: String,
    artist: String,
    album: String,
    artworkUri: String?,
    durationMs: Long,
): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(contentUri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setDurationMs(durationMs.takeIf { it > 0L })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

/** A file handed to Amply by another app ("Open with"); it is not in the library. */
fun externalMediaItem(uri: Uri, title: String, artist: String?): MediaItem =
    MediaItem.Builder()
        .setMediaId(EXTERNAL_MEDIA_PREFIX + uri)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

const val EXTERNAL_MEDIA_PREFIX = "external:"
