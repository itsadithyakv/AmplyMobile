package com.amply.mobile.stats

import com.amply.mobile.data.local.PlayEventEntity
import com.amply.mobile.domain.AppSettings
import com.amply.mobile.domain.Song
import com.amply.mobile.playlist.Rule
import com.amply.mobile.playlist.RuleField
import com.amply.mobile.playlist.RuleOp
import com.amply.mobile.playlist.RulePlaylist
import com.amply.mobile.playlist.RuleSort
import com.amply.mobile.playlist.evaluateRules
import com.amply.mobile.playlist.toEntity
import com.amply.mobile.playlist.toRulePlaylist
import com.amply.mobile.pro.FREE_DISCOVERY
import com.amply.mobile.pro.ProFeature
import com.amply.mobile.pro.isProMix
import com.amply.mobile.pro.limitSettings
import com.amply.mobile.pro.proFeaturesInUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsAndProTest {
    private val zone = ZoneOffset.UTC
    private val day = 86_400_000L
    private val now = LocalDate.of(2026, 11, 3).atTime(20, 0).toInstant(zone).toEpochMilli()

    private fun song(id: Long, artist: String = "Artist $id", genre: String = "Rock", plays: Int = 0, favorite: Boolean = false, added: Long = 0L) = Song(
        id = id, contentUri = "content://$id", filename = "$id.mp3", title = "Song $id", artist = artist, album = "Album",
        genre = genre, manualGenre = null, durationMs = 200_000L, track = 1, year = 2020, albumId = null, artworkUri = null,
        addedAtSec = added, lastModifiedSec = 0L, playCount = plays, skipCount = 0, totalPlayMs = plays * 200_000L,
        lastPlayedAtSec = null, favorite = favorite,
    )

    private fun event(songId: Long, atMs: Long, skipped: Boolean = false) =
        PlayEventEntity(songId = songId, atMs = atMs, listenedMs = if (skipped) 5_000L else 180_000L, skipped = skipped)

    @Test
    fun weekStatsCountOnlyListensInsideTheWindow() {
        val songs = listOf(song(1, "Vela"), song(2, "Vela feat. Oak", genre = "Indie"), song(3, "Oak")).associateBy { it.id }
        val events = listOf(
            event(1, now - 1 * day),
            event(2, now - 2 * day),
            event(3, now - 3 * day, skipped = true),
            event(1, now - 20 * day), // outside a week
        )
        val stats = computeListeningStats(events, songs, StatsRange.Week, now, zone)
        assertEquals(2, stats.plays)
        assertEquals(180_000L * 2 + 5_000L, stats.totalMs)
        assertEquals("Vela", stats.topArtists.first().label) // "feat." collapses to the primary artist
        assertEquals(2, stats.topArtists.first().plays)
        assertEquals(7, stats.byDay.size)
        assertEquals(30, computeListeningStats(events, songs, StatsRange.Month, now, zone).byDay.size)
    }

    @Test
    fun streakCountsBackFromYesterdayWhenNothingPlayedToday() {
        val songs = mapOf(1L to song(1))
        val events = listOf(event(1, now - 1 * day), event(1, now - 2 * day), event(1, now - 4 * day))
        assertEquals(2, computeListeningStats(events, songs, StatsRange.Week, now, zone).streakDays)
    }

    @Test
    fun allTimeUsesLifetimeTotals() {
        val songs = listOf(song(1, plays = 10), song(2, plays = 3)).associateBy { it.id }
        val stats = computeListeningStats(emptyList(), songs, StatsRange.AllTime, now, zone)
        assertEquals(13, stats.plays)
        assertEquals(1L, stats.topSongs.first().songId)
    }

    @Test
    fun encoreNeedsTwentyPlaysAndPicksPersonaFromPeakHour() {
        val songs = listOf(song(1, "Vela", "Indie"), song(2, "Oak", "Folk")).associateBy { it.id }
        val night = LocalDate.of(2026, 3, 10).atTime(23, 30).toInstant(zone).toEpochMilli()
        val few = (0 until 19).map { event(1, night + it * day) }
        assertNull(computeEncore(2026, few, songs, zone))
        val enough = (0 until 15).map { event(1, night + it * day) } + (0 until 6).map { event(2, night + it * day) }
        val encore = computeEncore(2026, enough, songs, zone)
        assertNotNull(encore!!)
        assertEquals(Persona.NightOwl, encore.persona)
        assertEquals("Vela", encore.topArtists.first().label)
        assertEquals("Indie", encore.topGenres.first().label)
        assertEquals(15, encore.longestStreak)
        assertEquals(3, encore.busiestMonth)
    }

    @Test
    fun longestRunHandlesGaps() {
        val d = LocalDate.of(2026, 1, 1)
        assertEquals(3, longestRun(listOf(d, d.plusDays(1), d.plusDays(2), d.plusDays(5), d.plusDays(6))))
        assertEquals(0, longestRun(emptyList()))
    }

    @Test
    fun rulePlaylistsMatchAllOrAnyAndRoundTrip() {
        val library = listOf(song(1, genre = "Rock", plays = 6), song(2, genre = "Rock", plays = 1), song(3, genre = "Jazz", plays = 9, favorite = true))
        val rockOnRepeat = RulePlaylist(
            name = "Rock on repeat",
            rules = listOf(Rule(RuleField.Genre, RuleOp.Contains, "rock"), Rule(RuleField.Plays, RuleOp.AtLeast, "5")),
        )
        assertEquals(listOf(1L), evaluateRules(rockOnRepeat, library).map { it.id })
        val either = rockOnRepeat.copy(matchAll = false, sort = RuleSort.MostPlayed)
        assertEquals(listOf(3L, 1L, 2L), evaluateRules(either, library).map { it.id })
        val liked = RulePlaylist(name = "Liked", rules = listOf(Rule(RuleField.Liked, RuleOp.Is, "")))
        assertEquals(listOf(3L), evaluateRules(liked, library).map { it.id })
        assertTrue(evaluateRules(RulePlaylist(name = "Empty", rules = emptyList()), library).isEmpty())
        assertEquals(rockOnRepeat, rockOnRepeat.toEntity().toRulePlaylist())
    }

    @Test
    fun freePlanRunsOnLimitedSettingsButKeepsTheSavedChoice() {
        val chosen = AppSettings(accent = "violet", trueBlack = true, crossfadeSeconds = 6f, discoveryIntensity = 0.9f, equalizerEnabled = true, eqPresetName = null, eqBass = 0.9f)
        val free = limitSettings(chosen, isPro = false)
        assertEquals("amber", free.accent)
        assertFalse(free.trueBlack)
        assertEquals(0f, free.crossfadeSeconds)
        assertEquals(FREE_DISCOVERY, free.discoveryIntensity)
        assertEquals(0.5f, free.eqBass) // custom curve flattens
        assertSame(chosen, limitSettings(chosen, isPro = true))
        assertEquals(setOf(ProFeature.Themes, ProFeature.Fade, ProFeature.Equalizer, ProFeature.MixTuning), proFeaturesInUse(chosen).toSet())
        // A free preset keeps its curve on the free plan.
        val bassBoost = AppSettings(equalizerEnabled = true, eqPresetName = "Bass Boost", eqBass = 0.82f)
        assertEquals(0.82f, limitSettings(bassBoost, isPro = false).eqBass)
    }

    @Test
    fun onlyGenreAndMoodMixesArePro() {
        assertTrue(isProMix("smart_genre_rock"))
        assertTrue(isProMix("smart_mood_chill"))
        assertFalse(isProMix("smart_daily_mix"))
        assertFalse(isProMix("smart_artist_vela"))
    }
}
