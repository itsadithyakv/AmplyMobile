package com.amply.mobile.stats

import com.amply.mobile.data.local.PlayEventEntity
import com.amply.mobile.domain.Song
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Port of the desktop's src/services/listeningStats.ts; same ranges and the same counting rules. */
enum class StatsRange(val days: Int, val label: String) {
    Week(7, "7 days"),
    Month(30, "30 days"),
    AllTime(30, "All time"),
}

data class RankedEntry(val key: String, val label: String, val sublabel: String? = null, val plays: Int, val songId: Long? = null)

data class ListeningStats(
    val totalMs: Long,
    val plays: Int,
    val songs: Int,
    val artists: Int,
    /** Consecutive days with listening, ending today (or yesterday if nothing yet today). */
    val streakDays: Int,
    /** Milliseconds listened per day, oldest first, covering the range (last 30 days for all time). */
    val byDay: List<Pair<LocalDate, Long>>,
    /** Plays per hour of day, 0-23. */
    val byHour: List<Int>,
    val topSongs: List<RankedEntry>,
    val topArtists: List<RankedEntry>,
    val topGenres: List<RankedEntry>,
)

private const val DAY_MS = 86_400_000L
private const val TOP_N = 8

fun primaryArtist(artist: String): String =
    artist.split(" feat.", " ft.", " featuring ", ",", " & ", ";", " x ").first().trim().ifBlank { artist.trim() }

internal fun isKnownGenre(genre: String): Boolean {
    val g = genre.trim().lowercase()
    return g.isNotEmpty() && g != "unknown" && g != "unknown genre"
}

private class Tally {
    val map = LinkedHashMap<String, RankedEntry>()
    fun bump(key: String, label: String, plays: Int, sublabel: String? = null, songId: Long? = null) {
        if (key.isBlank() || plays <= 0) return
        val existing = map[key]
        map[key] = existing?.copy(plays = existing.plays + plays) ?: RankedEntry(key, label, sublabel, plays, songId)
    }
    fun top(n: Int = TOP_N) = map.values.sortedWith(compareByDescending<RankedEntry> { it.plays }.thenBy { it.label.lowercase() }).take(n)
}

/**
 * Windows (7 / 30 days) come from the play log; "all time" uses each song's lifetime totals, which
 * go back further than the log does. A play is a listen that was not a skip.
 */
fun computeListeningStats(
    events: List<PlayEventEntity>,
    songsById: Map<Long, Song>,
    range: StatsRange,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): ListeningStats {
    fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val since = nowMs - range.days * DAY_MS
    val inWindow = events.filter { it.atMs in since..nowMs }
    val listened = inWindow.filterNot { it.skipped }

    val dayTotals = HashMap<LocalDate, Long>()
    inWindow.forEach { dayTotals.merge(day(it.atMs), it.listenedMs, Long::plus) }
    val today = day(nowMs)
    val byDay = (range.days - 1 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to (dayTotals[it] ?: 0L) } }

    val byHour = MutableList(24) { 0 }
    (if (range == StatsRange.AllTime) events.filterNot { it.skipped } else listened).forEach {
        byHour[Instant.ofEpochMilli(it.atMs).atZone(zone).hour]++
    }

    val activeDays = events.mapTo(HashSet()) { day(it.atMs) }
    var streak = 0
    var cursor = if (today in activeDays) today else today.minusDays(1)
    while (cursor in activeDays) {
        streak++
        cursor = cursor.minusDays(1)
    }

    val songs = Tally()
    val artists = Tally()
    val genres = Tally()
    fun tally(song: Song, plays: Int) {
        songs.bump(song.id.toString(), song.title, plays, song.artist, song.id)
        val artist = primaryArtist(song.artist)
        if (!artist.startsWith("Unknown", ignoreCase = true)) artists.bump(artist.lowercase(), artist, plays, songId = song.id)
        val genre = song.effectiveGenre.trim()
        if (isKnownGenre(genre)) genres.bump(genre.lowercase(), genre, plays)
    }

    var totalMs = 0L
    var plays = 0
    if (range == StatsRange.AllTime) {
        songsById.values.forEach {
            totalMs += it.totalPlayMs
            plays += it.playCount
            tally(it, it.playCount)
        }
    } else {
        totalMs = inWindow.sumOf { it.listenedMs }
        plays = listened.size
        listened.forEach { event -> songsById[event.songId]?.let { tally(it, 1) } }
    }

    return ListeningStats(
        totalMs = totalMs,
        plays = plays,
        songs = songs.map.size,
        artists = artists.map.size,
        streakDays = streak,
        byDay = byDay,
        byHour = byHour,
        topSongs = songs.top(),
        topArtists = artists.top(),
        topGenres = genres.top(),
    )
}

/** "3 h 25 min", "42 min". */
fun formatListeningTime(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

// Amply Encore (desktop src/services/yearInMusic.ts) ----------------------------------------------

enum class Persona(val emoji: String, val title: String, val body: String) {
    NightOwl("🦉", "Night owl", "You come alive after 10 pm, and so does your music."),
    EarlyBird("🌅", "Early riser", "You start the day with a soundtrack, before 10 am."),
    Daytime("☀️", "All-day listener", "Music keeps you company all through the day."),
    Evening("🌆", "Golden hour listener", "Your music comes out between 5 and 10 pm."),
}

fun personaFor(hour: Int): Persona = when {
    hour >= 22 || hour < 5 -> Persona.NightOwl
    hour < 10 -> Persona.EarlyBird
    hour < 17 -> Persona.Daytime
    else -> Persona.Evening
}

data class Encore(
    val year: Int,
    val minutes: Long,
    val plays: Int,
    val songs: Int,
    val artists: Int,
    val topSongs: List<RankedEntry>,
    val mixSongIds: List<Long>,
    val topArtists: List<RankedEntry>,
    val topGenres: List<RankedEntry>,
    val persona: Persona,
    val peakHour: Int,
    /** 1-12. */
    val busiestMonth: Int,
    val daysListened: Int,
    val longestStreak: Int,
)

/** Fewer plays than this and a recap would mostly be noise (same threshold as desktop). */
const val MIN_ENCORE_PLAYS = 20
private const val ENCORE_MIX_SIZE = 30

fun longestRun(days: Collection<LocalDate>): Int {
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in days.toSortedSet()) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        best = maxOf(best, run)
        previous = day
    }
    return best
}

/** The year's recap, or null below [MIN_ENCORE_PLAYS]. [events] should already be limited to [year]. */
fun computeEncore(year: Int, events: List<PlayEventEntity>, songsById: Map<Long, Song>, zone: ZoneId = ZoneId.systemDefault()): Encore? {
    val played = events.filterNot { it.skipped }
    if (played.size < MIN_ENCORE_PLAYS) return null
    val songs = Tally()
    val artists = Tally()
    val genres = Tally()
    played.groupingBy { it.songId }.eachCount().forEach { (songId, plays) ->
        val song = songsById[songId] ?: return@forEach // gone from the library: cannot be named
        songs.bump(songId.toString(), song.title, plays, song.artist, songId)
        val artist = primaryArtist(song.artist)
        if (!artist.startsWith("Unknown", ignoreCase = true)) artists.bump(artist.lowercase(), artist, plays, songId = songId)
        val genre = song.effectiveGenre.split(';', ',', '/').first().trim()
        if (isKnownGenre(genre)) genres.bump(genre.lowercase(), genre, plays)
    }
    val byHour = IntArray(24)
    val byMonthMs = LongArray(12)
    val days = HashSet<LocalDate>()
    events.forEach {
        val at = Instant.ofEpochMilli(it.atMs).atZone(zone)
        if (!it.skipped) byHour[at.hour]++
        byMonthMs[at.monthValue - 1] += it.listenedMs
        days += at.toLocalDate()
    }
    val peakHour = byHour.indices.maxByOrNull { byHour[it] } ?: 0
    return Encore(
        year = year,
        minutes = events.sumOf { it.listenedMs } / 60_000L,
        plays = played.size,
        songs = songs.map.size,
        artists = artists.map.size,
        topSongs = songs.top(5),
        mixSongIds = songs.top(ENCORE_MIX_SIZE).mapNotNull { it.songId },
        topArtists = artists.top(5),
        topGenres = genres.top(3),
        persona = personaFor(peakHour),
        peakHour = peakHour,
        busiestMonth = (byMonthMs.indices.maxByOrNull { byMonthMs[it] } ?: 0) + 1,
        daysListened = days.size,
        longestStreak = longestRun(days),
    )
}
