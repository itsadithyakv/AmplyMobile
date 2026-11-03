package com.amply.mobile.playlist

import com.amply.mobile.data.local.RulePlaylistEntity
import com.amply.mobile.domain.Song
import com.amply.mobile.util.nowSec
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Smart playlists that fill themselves from rules, e.g. "Rock, played at least 5 times, added in
 * the last year" (desktop 1.8 Pro feature). They are evaluated live against the library, so they
 * update as you listen.
 */
enum class RuleField(val label: String, val kind: RuleKind) {
    Genre("Genre", RuleKind.Text),
    Artist("Artist", RuleKind.Text),
    Album("Album", RuleKind.Text),
    Title("Title", RuleKind.Text),
    Plays("Plays", RuleKind.Number),
    Skips("Skips", RuleKind.Number),
    AddedWithinDays("Added in the last (days)", RuleKind.Days),
    PlayedWithinDays("Played in the last (days)", RuleKind.Days),
    NotPlayedForDays("Not played for (days)", RuleKind.Days),
    Year("Year", RuleKind.Number),
    LengthMinutes("Length (minutes)", RuleKind.Number),
    Liked("Liked", RuleKind.Flag),
}

enum class RuleKind { Text, Number, Days, Flag }

enum class RuleOp(val label: String) {
    Contains("contains"),
    Is("is"),
    IsNot("is not"),
    AtLeast("at least"),
    AtMost("at most"),
    ;

    companion object {
        fun forKind(kind: RuleKind): List<RuleOp> = when (kind) {
            RuleKind.Text -> listOf(Contains, Is, IsNot)
            RuleKind.Number -> listOf(AtLeast, AtMost, Is)
            RuleKind.Days -> listOf(Is)
            RuleKind.Flag -> listOf(Is, IsNot)
        }
    }
}

data class Rule(val field: RuleField, val op: RuleOp, val value: String)

enum class RuleSort(val label: String) { MostPlayed("Most played"), RecentlyAdded("Recently added"), RecentlyPlayed("Recently played"), Title("Title"), Random("Random") }

data class RulePlaylist(
    val id: String = "rules_" + UUID.randomUUID(),
    val name: String,
    val rules: List<Rule>,
    val matchAll: Boolean = true,
    /** 0 means no limit. */
    val limit: Int = 0,
    val sort: RuleSort = RuleSort.MostPlayed,
)

fun Rule.matches(song: Song, nowSec: Long): Boolean {
    fun text(source: String): Boolean = when (op) {
        RuleOp.Contains -> source.contains(value.trim(), ignoreCase = true)
        RuleOp.Is -> source.trim().equals(value.trim(), ignoreCase = true)
        RuleOp.IsNot -> !source.trim().equals(value.trim(), ignoreCase = true)
        else -> false
    }
    fun number(source: Double): Boolean {
        val target = value.trim().toDoubleOrNull() ?: return false
        return when (op) {
            RuleOp.AtLeast -> source >= target
            RuleOp.AtMost -> source <= target
            RuleOp.Is -> source == target
            else -> false
        }
    }
    val days = value.trim().toLongOrNull() ?: 0L
    return when (field) {
        RuleField.Genre -> text(song.effectiveGenre)
        RuleField.Artist -> text(song.artist)
        RuleField.Album -> text(song.album)
        RuleField.Title -> text(song.title)
        RuleField.Plays -> number(song.playCount.toDouble())
        RuleField.Skips -> number(song.skipCount.toDouble())
        RuleField.Year -> song.year?.let { number(it.toDouble()) } ?: false
        RuleField.LengthMinutes -> number(song.durationMs / 60_000.0)
        RuleField.AddedWithinDays -> song.addedAtSec >= nowSec - days * 86_400L
        RuleField.PlayedWithinDays -> (song.lastPlayedAtSec ?: 0L) >= nowSec - days * 86_400L
        RuleField.NotPlayedForDays -> (song.lastPlayedAtSec ?: 0L) < nowSec - days * 86_400L
        RuleField.Liked -> (op == RuleOp.Is) == song.favorite
    }
}

/** The songs a rule playlist holds right now. A playlist with no rules is empty, not the whole library. */
fun evaluateRules(playlist: RulePlaylist, songs: List<Song>, nowSec: Long = nowSec()): List<Song> {
    if (playlist.rules.isEmpty()) return emptyList()
    val matched = songs.filter { song ->
        if (playlist.matchAll) playlist.rules.all { it.matches(song, nowSec) } else playlist.rules.any { it.matches(song, nowSec) }
    }
    val sorted = when (playlist.sort) {
        RuleSort.MostPlayed -> matched.sortedWith(compareByDescending<Song> { it.playCount }.thenByDescending { it.lastPlayedAtSec ?: 0L })
        RuleSort.RecentlyAdded -> matched.sortedByDescending { it.addedAtSec }
        RuleSort.RecentlyPlayed -> matched.sortedByDescending { it.lastPlayedAtSec ?: 0L }
        RuleSort.Title -> matched.sortedBy { it.title.lowercase() }
        // Stable per day, so the list doesn't reshuffle on every recomposition.
        RuleSort.Random -> matched.shuffled(java.util.Random(playlist.id.hashCode().toLong() + nowSec / 86_400L))
    }
    return if (playlist.limit > 0) sorted.take(playlist.limit) else sorted
}

fun RulePlaylist.toEntity(): RulePlaylistEntity = RulePlaylistEntity(
    id = id,
    name = name.trim().ifBlank { "Smart playlist" },
    rulesJson = JSONArray().apply {
        rules.forEach { put(JSONObject().put("field", it.field.name).put("op", it.op.name).put("value", it.value)) }
    }.toString(),
    matchAll = matchAll,
    limit = limit,
    sort = sort.name,
    updatedAtSec = nowSec(),
)

/** Null when the stored JSON can't be read (written by a newer version, say); such rows are skipped. */
fun RulePlaylistEntity.toRulePlaylist(): RulePlaylist? = runCatching {
    val array = JSONArray(rulesJson)
    RulePlaylist(
        id = id,
        name = name,
        rules = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Rule(RuleField.valueOf(item.getString("field")), RuleOp.valueOf(item.getString("op")), item.optString("value"))
        },
        matchAll = matchAll,
        limit = limit,
        sort = runCatching { RuleSort.valueOf(sort) }.getOrDefault(RuleSort.MostPlayed),
    )
}.getOrNull()

/** One-line summary for list rows: "Genre contains rock · Plays at least 5". */
fun RulePlaylist.describe(): String =
    rules.joinToString(if (matchAll) "  ·  " else "  or  ") { rule ->
        when (rule.field.kind) {
            RuleKind.Flag -> if (rule.op == RuleOp.Is) "Liked" else "Not liked"
            RuleKind.Days -> "${rule.field.label.substringBefore(" (")} ${rule.value} days"
            else -> "${rule.field.label} ${rule.op.label} ${rule.value}"
        }
    }
