package com.amply.mobile.metadata

import com.amply.mobile.data.local.ArtistInfoDao
import com.amply.mobile.data.local.ArtistInfoEntity
import com.amply.mobile.data.local.GenreCacheEntity
import com.amply.mobile.data.local.MetadataDao
import com.amply.mobile.data.local.SongDao
import com.amply.mobile.data.local.toDomain
import com.amply.mobile.domain.ArtistInfo
import com.amply.mobile.domain.Song
import com.amply.mobile.lyrics.primaryArtist
import com.amply.mobile.util.nowSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MetadataRepository(
    private val metadataDao: MetadataDao,
    private val songDao: SongDao,
    private val artistInfoDao: ArtistInfoDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    private val throttle = Mutex()
    private var lastMusicBrainzRequestMs = 0L

    suspend fun enrichGenre(song: Song): String? = withContext(Dispatchers.IO) {
        song.manualGenre?.takeIf { it.isNotBlank() }?.let { return@withContext normalizeGenreBucket(it) }
        if (!isUnknownGenre(song.genre)) {
            val normalized = normalizeGenreBucket(song.genre)
            metadataDao.upsertGenre(GenreCacheEntity(song.id, normalized, "tag", nowSec()))
            return@withContext normalized
        }

        metadataDao.genreFor(song.id)?.genre?.let { return@withContext it }
        val fetched = fetchMusicBrainzGenre(song) ?: return@withContext null
        val normalized = normalizeGenreBucket(fetched)
        metadataDao.upsertGenre(GenreCacheEntity(song.id, normalized, "musicbrainz", nowSec()))
        songDao.setManualGenre(song.id, normalized)
        normalized
    }

    suspend fun artistInfo(song: Song): ArtistInfo = withContext(Dispatchers.IO) {
        val artist = primaryArtist(song.artist).ifBlank { song.artist.trim() }
        if (artist.isBlank() || artist.equals("Unknown Artist", true)) {
            return@withContext notFound(artist.ifBlank { "Unknown Artist" })
        }
        artistInfoDao.infoFor(artist)?.toDomain()?.let { return@withContext it }
        val fetched = fetchWikipediaArtistInfo(artist) ?: notFound(artist)
        artistInfoDao.upsert(ArtistInfoEntity(fetched.artist, fetched.summary, fetched.sourceUrl, fetched.imageUrl, nowSec()))
        fetched
    }

    private suspend fun fetchMusicBrainzGenre(song: Song): String? {
        throttle.withLock {
            val elapsed = System.currentTimeMillis() - lastMusicBrainzRequestMs
            if (elapsed < 1_100L) {
                kotlinx.coroutines.delay(1_100L - elapsed)
            }
            lastMusicBrainzRequestMs = System.currentTimeMillis()
        }

        val query = "recording:\"${song.title}\" AND artist:\"${song.artist}\""
        val url = "https://musicbrainz.org/ws/2/recording".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "5")
            .addQueryParameter("inc", "genres+tags")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AmplyMobile/0.1 (local metadata cache)")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    val body = response.body?.string().orEmpty()
                    pickBestGenre(JSONObject(body))
                }
            }
        }.getOrNull()
    }

    private fun fetchWikipediaArtistInfo(artist: String): ArtistInfo? {
        val searchUrl = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "search")
            .addQueryParameter("srsearch", "$artist musician")
            .addQueryParameter("format", "json")
            .addQueryParameter("srlimit", "1")
            .build()
        val title = runCatching {
            val request = Request.Builder()
                .url(searchUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "AmplyMobile/0.1 (artist info cache)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("query")
                    ?.optJSONArray("search")
                    ?.optJSONObject(0)
                    ?.optString("title")
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull() ?: return null

        val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary".toHttpUrl().newBuilder()
            .addPathSegment(title)
            .build()
        return runCatching {
            val request = Request.Builder()
                .url(summaryUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "AmplyMobile/0.1 (artist info cache)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val payload = JSONObject(response.body?.string().orEmpty())
                val summary = payload.optString("extract").takeIf { it.isNotBlank() } ?: return@use null
                ArtistInfo(
                    artist = artist,
                    summary = summary,
                    sourceUrl = payload.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page"),
                    imageUrl = payload.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() },
                    fetchedAtSec = nowSec(),
                )
            }
        }.getOrNull()
    }

    private fun notFound(artist: String): ArtistInfo = ArtistInfo(
        artist = artist,
        summary = "Opps, I cant find that!",
        sourceUrl = null,
        imageUrl = null,
        fetchedAtSec = nowSec(),
    )
}

fun pickBestGenre(payload: JSONObject): String? {
    val recordings = payload.optJSONArray("recordings") ?: return null
    val candidates = mutableMapOf<String, Int>()
    for (i in 0 until recordings.length()) {
        val recording = recordings.optJSONObject(i) ?: continue
        collectNames(recording.optJSONArray("genres"), candidates, weight = 3)
        collectNames(recording.optJSONArray("tags"), candidates, weight = 1)
    }
    return candidates.maxByOrNull { it.value }?.key
}

private fun collectNames(array: JSONArray?, target: MutableMap<String, Int>, weight: Int) {
    if (array == null) return
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
        val count = obj.optInt("count", 1).coerceAtLeast(1)
        target[name] = (target[name] ?: 0) + count * weight
    }
}
