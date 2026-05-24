package com.amply.mobile.data.local

import com.amply.mobile.domain.CachedLyrics
import com.amply.mobile.domain.ArtistInfo
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.RepeatMode
import com.amply.mobile.domain.Song

fun SongEntity.toDomain(): Song = Song(
    id = id,
    contentUri = contentUri,
    filename = filename,
    title = title,
    artist = artist,
    album = album,
    genre = genre,
    manualGenre = manualGenre,
    durationMs = durationMs,
    track = track,
    year = year,
    albumId = albumId,
    artworkUri = artworkUri,
    addedAtSec = addedAtSec,
    lastModifiedSec = lastModifiedSec,
    playCount = playCount,
    skipCount = skipCount,
    totalPlayMs = totalPlayMs,
    lastPlayedAtSec = lastPlayedAtSec,
    favorite = favorite,
)

fun PlaylistEntity.toDomain(songIds: List<Long>): Playlist = Playlist(
    id = id,
    name = name,
    type = if (type == "custom") PlaylistType.Custom else PlaylistType.Smart,
    description = description,
    songIds = songIds,
    artworkUri = artworkUri,
    updatedAtSec = updatedAtSec,
)

fun LyricsEntity.toDomain(): CachedLyrics = CachedLyrics(
    songId = songId,
    raw = raw,
    synced = synced,
    source = source,
    candidateId = candidateId,
    edited = edited,
    updatedAtSec = updatedAtSec,
)

fun ArtistInfoEntity.toDomain(): ArtistInfo = ArtistInfo(
    artist = artist,
    summary = summary,
    sourceUrl = sourceUrl,
    imageUrl = imageUrl,
    fetchedAtSec = fetchedAtSec,
)

fun RepeatMode.toStorageValue(): String = when (this) {
    RepeatMode.Off -> "off"
    RepeatMode.One -> "one"
    RepeatMode.All -> "all"
}

fun String.toRepeatMode(): RepeatMode = when (this) {
    "one" -> RepeatMode.One
    "all" -> RepeatMode.All
    else -> RepeatMode.Off
}
