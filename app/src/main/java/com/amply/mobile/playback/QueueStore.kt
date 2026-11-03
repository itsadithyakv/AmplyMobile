package com.amply.mobile.playback

import com.amply.mobile.data.local.QueueDao
import com.amply.mobile.data.local.QueueStateEntity
import com.amply.mobile.data.local.SongDao
import com.amply.mobile.data.local.SongEntity
import com.amply.mobile.data.local.songsByIdsChunked
import com.amply.mobile.util.nowSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QueueSnapshot(
    val songIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    /** "off", "one" or "all", the values QueueStateEntity has always stored. */
    val repeatMode: String,
)

data class RestoredQueue(
    val songs: List<SongEntity>,
    val startIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: String,
)

/**
 * The one place the last queue is saved and read back. The playback service writes it (so it is
 * kept even when playback was started from the widget, a headset or the car with no UI open) and
 * reads it for playback resumption; the UI and widget only read it.
 */
class QueueStore(
    private val queueDao: QueueDao,
    private val songDao: SongDao,
) {
    suspend fun save(snapshot: QueueSnapshot) = withContext(Dispatchers.IO) {
        if (snapshot.songIds.isEmpty()) return@withContext
        queueDao.upsert(
            QueueStateEntity(
                songIdsCsv = encodeQueueIds(snapshot.songIds),
                currentIndex = snapshot.currentIndex.coerceIn(0, snapshot.songIds.lastIndex),
                positionMs = snapshot.positionMs.coerceAtLeast(0L),
                shuffle = snapshot.shuffle,
                repeatMode = snapshot.repeatMode,
                updatedAtSec = nowSec(),
            ),
        )
    }

    suspend fun restore(): RestoredQueue? = withContext(Dispatchers.IO) {
        val stored = queueDao.queue() ?: return@withContext null
        val ids = decodeQueueIds(stored.songIdsCsv)
        if (ids.isEmpty()) return@withContext null
        val songs = songDao.songsByIdsChunked(ids)
        if (songs.isEmpty()) return@withContext null
        val start = resolveRestoreIndex(ids, stored.currentIndex, songs.map { it.id })
        RestoredQueue(
            songs = songs,
            startIndex = start,
            // Resuming mid-song only makes sense if we land on the same song we saved.
            positionMs = if (songs[start].id == ids.getOrNull(stored.currentIndex)) stored.positionMs else 0L,
            shuffle = stored.shuffle,
            repeatMode = stored.repeatMode,
        )
    }

    /** Song id that was current when the queue was last saved, for the widget's idle state. */
    suspend fun currentSongId(): Long? = withContext(Dispatchers.IO) {
        queueDao.queue()?.let { decodeQueueIds(it.songIdsCsv).getOrNull(it.currentIndex) }
    }
}

fun encodeQueueIds(ids: List<Long>): String = ids.joinToString(",")

fun decodeQueueIds(csv: String): List<Long> = csv.split(",").mapNotNull { it.trim().toLongOrNull() }

/**
 * Where to start after songs may have been deleted since the queue was saved: the saved song if it
 * still exists, otherwise the first surviving song after it, otherwise the last one.
 */
fun resolveRestoreIndex(savedIds: List<Long>, savedIndex: Int, availableIds: List<Long>): Int {
    if (availableIds.isEmpty()) return 0
    val available = availableIds.toHashSet()
    val fromSaved = savedIds.drop(savedIndex.coerceAtLeast(0)).firstOrNull { it in available }
    return fromSaved?.let(availableIds::indexOf)?.takeIf { it >= 0 } ?: availableIds.lastIndex
}
