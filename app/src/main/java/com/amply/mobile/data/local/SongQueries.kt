package com.amply.mobile.data.local

/**
 * SQLite builds before 3.32 (every Android release up to 11) refuse statements with more than 999
 * bound arguments, so any `IN (:ids)` query over a real library has to be split up.
 */
const val SQLITE_MAX_BOUND_ARGS = 900

/** Songs for [songIds] in the order of [songIds]; unknown ids are dropped. */
suspend fun SongDao.songsByIdsChunked(songIds: List<Long>): List<SongEntity> {
    if (songIds.isEmpty()) return emptyList()
    val byId = songIds.distinct()
        .chunked(SQLITE_MAX_BOUND_ARGS)
        .flatMap { songsByIds(it) }
        .associateBy { it.id }
    return songIds.mapNotNull { byId[it] }
}

suspend fun SongDao.deleteSongsByIdsChunked(songIds: Collection<Long>) {
    songIds.chunked(SQLITE_MAX_BOUND_ARGS).forEach { deleteSongsByIds(it) }
}

/**
 * MediaStore rows use their positive MediaStore id; songs added through a picked folder use a
 * negative hash of their document uri (see MusicScanner.stableSongId). A MediaStore rescan may only
 * remove MediaStore songs, otherwise every periodic scan wipes the folders the user added by hand.
 */
fun staleMediaStoreIds(existingIds: Collection<Long>, scannedIds: Set<Long>): List<Long> =
    existingIds.filter { it > 0L && it !in scannedIds }
