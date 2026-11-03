package com.amply.mobile.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes cover art at the size it is drawn at and keeps recent results in memory.
 *
 * Embedded covers are often 1500-3000 px squares; decoding them at full size for every 62 dp list
 * row costs tens of megabytes per row and is what made long lists stutter. Results are cached per
 * (uri, size bucket) and misses are cached too, so a song without art is not re-opened on every
 * scroll.
 */
object ArtworkLoader {
    private val missing = Any()
    private val cache = object : LruCache<String, Any>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: Any): Int = (value as? Bitmap)?.allocationByteCount ?: 64
    }

    fun cached(uri: String?, sizePx: Int): Bitmap? =
        uri?.let { cache.get(key(it, sizePx)) as? Bitmap }

    /**
     * Loads [uri] (an album-art or image content uri) scaled to roughly [sizePx]. When that fails and
     * [audioUri] is given, falls back to the picture embedded in the audio file itself, which is the
     * only art songs added through a picked folder have.
     */
    suspend fun load(context: Context, uri: String?, sizePx: Int, audioUri: String? = null): Bitmap? {
        val source = uri ?: audioUri ?: return null
        val key = key(source, sizePx)
        cache.get(key)?.let { return it as? Bitmap }
        val bitmap = withContext(Dispatchers.IO) {
            uri?.let { decodeUri(context, it, sizePx) }
                ?: audioUri?.let { embeddedPicture(context, it)?.let { bytes -> decodeBytes(bytes, sizePx) } }
        }
        cache.put(key, bitmap ?: missing)
        return bitmap
    }

    fun decodeBytes(bytes: ByteArray, sizePx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sizePx) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private fun decodeUri(context: Context, uri: String, sizePx: Int): Bitmap? = runCatching {
        val parsed = Uri.parse(uri)
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sizePx) }
        resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun embeddedPicture(context: Context, audioUri: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(audioUri))
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Sizes are bucketed so a 52 dp and a 54 dp view share one decoded bitmap. */
    private fun key(uri: String, sizePx: Int): String {
        val bucket = when {
            sizePx <= 128 -> 128
            sizePx <= 256 -> 256
            sizePx <= 512 -> 512
            else -> 1024
        }
        return "$bucket|$uri"
    }

    private fun maxCacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(48L * 1024 * 1024).toInt()
}

/** Largest power-of-two `inSampleSize` that keeps both sides at or above [target] pixels. */
fun sampleSize(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
    return sample
}
