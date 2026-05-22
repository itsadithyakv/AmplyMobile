package com.amply.mobile.lyrics

import com.amply.mobile.data.local.LyricsDao
import com.amply.mobile.data.local.LyricsEntity
import com.amply.mobile.data.local.toDomain
import com.amply.mobile.domain.CachedLyrics
import com.amply.mobile.domain.LyricsCandidate
import com.amply.mobile.domain.Song
import com.amply.mobile.util.nowSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.TimeUnit

class LyricsRepository(
    private val lyricsDao: LyricsDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    fun observeLyrics(songId: Long): Flow<CachedLyrics?> =
        lyricsDao.observeLyrics(songId).map { it?.toDomain() }

    suspend fun cachedLyrics(songId: Long): CachedLyrics? =
        lyricsDao.lyricsFor(songId)?.toDomain()

    suspend fun loadOrFetch(song: Song): CachedLyrics? = withContext(Dispatchers.IO) {
        lyricsDao.lyricsFor(song.id)?.takeIf { validateLyricsQuality(it.raw) }?.toDomain()
            ?: findCandidates(song).firstOrNull { validateLyricsQuality(it.raw) }?.let { saveCandidate(song, it) }
    }

    suspend fun findCandidates(song: Song): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val primaryArtist = primaryArtist(song.artist)
        val title = cleanTitle(song.title)
        if (primaryArtist.isBlank() || title.isBlank()) return@withContext emptyList()

        val queries = buildList {
            add(Query(primaryArtist, song.title, song.album.takeUnless { it.equals("Unknown Album", true) }))
            if (!title.equals(song.title, ignoreCase = true)) {
                add(Query(primaryArtist, title, null))
            }
        }.distinctBy { "${it.artist.lowercase()}::${it.title.lowercase()}" }

        val all = queries.flatMap { searchLrcLib(song, it) }
        rankCandidates(song, all.distinctBy { it.raw.trim() })
    }

    suspend fun saveCandidate(song: Song, candidate: LyricsCandidate): CachedLyrics {
        val entity = LyricsEntity(
            songId = song.id,
            raw = candidate.raw.trim(),
            synced = isSyncedLyrics(candidate.raw),
            source = "lrclib",
            candidateId = candidate.id,
            edited = false,
            updatedAtSec = nowSec(),
        )
        lyricsDao.upsertLyrics(entity)
        return entity.toDomain()
    }

    suspend fun saveEditedLyrics(songId: Long, raw: String): CachedLyrics {
        val entity = LyricsEntity(
            songId = songId,
            raw = raw.trim(),
            synced = isSyncedLyrics(raw),
            source = "manual",
            candidateId = null,
            edited = true,
            updatedAtSec = nowSec(),
        )
        lyricsDao.upsertLyrics(entity)
        return entity.toDomain()
    }

    private fun searchLrcLib(song: Song, query: Query): List<LyricsCandidate> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", query.artist)
            .addQueryParameter("track_name", query.title)
            .apply {
                query.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album_name", it) }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AmplyMobile/0.1 (offline music player)")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emptyList()
                } else {
                    val body = response.body?.string().orEmpty()
                    parseCandidates(song, JSONArray(body))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseCandidates(song: Song, payload: JSONArray): List<LyricsCandidate> {
        val candidates = mutableListOf<LyricsCandidate>()
        for (index in 0 until payload.length()) {
            val obj = payload.optJSONObject(index) ?: continue
            val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() }
                ?: obj.optString("synced_lyrics").takeIf { it.isNotBlank() }
            val plainRaw = obj.optString("plainLyrics").takeIf { it.isNotBlank() }
                ?: obj.optString("plain_lyrics").takeIf { it.isNotBlank() }
            val raw = synced ?: plainRaw ?: continue
            val preview = plainRaw?.normalizePlainLyrics() ?: raw
            candidates += LyricsCandidate(
                id = obj.opt("id")?.toString()?.slugify().orEmpty().ifBlank { "candidate-${index + 1}" },
                trackName = obj.optString("trackName", song.title),
                artistName = obj.optString("artistName", song.artist),
                albumName = obj.optStringOrNull("albumName"),
                durationMs = obj.optDoubleOrNull("duration")?.times(1000)?.toLong(),
                synced = synced != null,
                raw = raw,
                preview = preview,
            )
        }
        return candidates
    }

    private data class Query(val artist: String, val title: String, val album: String?)
}

fun rankCandidates(song: Song, candidates: List<LyricsCandidate>): List<LyricsCandidate> =
    candidates.sortedByDescending { candidate ->
        var score = 0
        if (normalizeMatch(candidate.trackName) == normalizeMatch(song.title)) score += 6
        else if (normalizeMatch(candidate.trackName).contains(normalizeMatch(song.title))) score += 3

        val primary = primaryArtist(song.artist)
        if (normalizeMatch(candidate.artistName) == normalizeMatch(primary)) score += 5
        else if (normalizeMatch(candidate.artistName).contains(normalizeMatch(primary))) score += 2

        if (candidate.albumName != null && normalizeMatch(candidate.albumName) == normalizeMatch(song.album)) score += 3
        if (candidate.synced) score += 2
        candidate.durationMs?.let { duration ->
            val diff = kotlin.math.abs(duration - song.durationMs)
            score += when {
                diff <= 2_000 -> 3
                diff <= 7_000 -> 2
                diff <= 12_000 -> 1
                else -> 0
            }
        }
        score
    }.take(8)

fun primaryArtist(artist: String): String =
    artist.split(" feat. ", " ft. ", " featuring ", ",", " & ", " x ", " / ")
        .firstOrNull()
        ?.trim()
        .orEmpty()
        .ifBlank { artist.trim() }

fun cleanTitle(title: String): String =
    title.substringBefore(" - ")
        .replace(Regex("""(?i)\s*[\[(].*?[\])]"""), " ")
        .replace(Regex("""(?i)\b(remaster(ed)?|official|audio|video|lyrics|explicit|clean)\b"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun normalizeMatch(value: String): String =
    Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun String.normalizePlainLyrics(): String =
    lineSequence().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")

private fun String.slugify(): String =
    lowercase()
        .replace(Regex("""[^a-z0-9]+"""), "-")
        .trim('-')

private fun JSONObject.optStringOrNull(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeIf { !it.isNaN() } else null
