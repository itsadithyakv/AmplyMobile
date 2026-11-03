package com.amply.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amply.mobile.domain.Song
import com.amply.mobile.stats.ListeningStats
import com.amply.mobile.stats.MIN_ENCORE_PLAYS
import com.amply.mobile.stats.RankedEntry
import com.amply.mobile.stats.StatsRange
import com.amply.mobile.stats.formatListeningTime
import com.amply.mobile.ui.theme.NeuDepth
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuRaised
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(
    stats: ListeningStats?,
    range: StatsRange,
    isPro: Boolean,
    yearPlays: Int,
    songsById: Map<Long, Song>,
    onRange: (StatsRange) -> Unit,
    onOpenEncore: () -> Unit,
    onPlaySong: (Song) -> Unit,
) {
    val p = Palette
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("Stats", style = MaterialTheme.typography.headlineLarge)
            Text("Your listening, counted on this phone.", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(StatsRange.entries) { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AmplyChip(label = option.label, selected = option == range, onClick = { onRange(option) })
                        if (option != StatsRange.Week && !isPro) {
                            Spacer(Modifier.width(4.dp))
                            ProLock()
                        }
                    }
                }
            }
        }
        item { EncoreTeaser(yearPlays = yearPlays, isPro = isPro, onOpen = onOpenEncore) }
        if (stats == null) return@LazyColumn
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Listened", formatListeningTime(stats.totalMs), Modifier.weight(1f))
                StatTile("Plays", stats.plays.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Artists", stats.artists.toString(), Modifier.weight(1f))
                StatTile("Day streak", stats.streakDays.toString(), Modifier.weight(1f))
            }
        }
        item {
            NeuCard {
                Column {
                    Eyebrow(if (range == StatsRange.AllTime) "Last 30 days" else "Minutes per day")
                    Spacer(Modifier.height(12.dp))
                    DayColumns(stats.byDay.map { it.second }, labels = stats.byDay.map { it.first.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) })
                }
            }
        }
        if (stats.plays == 0) {
            item {
                Text(
                    "Nothing counted yet. A song counts once you've listened for 30 seconds or half of it.",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (stats.topArtists.isNotEmpty()) item { RankedList("Top artists", stats.topArtists, songsById) { songsById[it.songId]?.let(onPlaySong) } }
        if (stats.topSongs.isNotEmpty()) item { RankedList("Top songs", stats.topSongs, songsById) { songsById[it.songId]?.let(onPlaySong) } }
        if (stats.topGenres.isNotEmpty()) item { RankedList("Top genres", stats.topGenres, songsById, onClick = null) }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.neuRaised(RadiusMd, NeuDepth.Raised).padding(16.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
    }
}

/** Column chart of minutes per day (desktop ColumnChart), drawn in the accent. */
@Composable
private fun DayColumns(values: List<Long>, labels: List<String>) {
    val accent = Palette.accent
    val track = Palette.textMuted.copy(alpha = 0.14f)
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val total = values.sum()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = "Minutes listened per day, ${formatListeningTime(total)} in total" },
    ) {
        val n = values.size.coerceAtLeast(1)
        val gap = if (n > 10) 3.dp.toPx() else 8.dp.toPx()
        val barWidth = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { index, value ->
            val x = index * (barWidth + gap)
            drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(barWidth / 2, barWidth / 2))
            val h = size.height * value / max
            if (h > 0f) {
                drawRoundRect(accent, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(barWidth / 2, barWidth / 2))
            }
        }
    }
    if (labels.size <= 7) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach { Text(it, color = Palette.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun RankedList(title: String, entries: List<RankedEntry>, songsById: Map<Long, Song>, onClick: ((RankedEntry) -> Unit)?) {
    val p = Palette
    Column {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().neuRaised(RadiusLg, NeuDepth.Raised).padding(vertical = 6.dp)) {
            entries.forEachIndexed { index, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (onClick != null) Modifier.clickable { onClick(entry) } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = p.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(22.dp))
                    val song = entry.songId?.let(songsById::get)
                    if (song != null) {
                        ArtworkBox(song.artworkUri, 40.dp, shape = RadiusSm, audioUri = song.contentUri)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(entry.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        entry.sublabel?.let { Text(it, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Text("${entry.plays} plays", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** The Encore entry point: a colourful card, like the desktop's Stats page header. */
@Composable
private fun EncoreTeaser(yearPlays: Int, isPro: Boolean, onOpen: () -> Unit) {
    val year = java.time.LocalDate.now().year
    val ready = yearPlays >= MIN_ENCORE_PLAYS
    Column(
        Modifier
            .fillMaxWidth()
            .neuRaised(RadiusLg, NeuDepth.Raised)
            .clip(RadiusLg)
            .background(Brush.linearGradient(listOf(Color(0xFF5B21B6), Color(0xFFDB2777))))
            .clickable(onClickLabel = "Open Amply Encore", onClick = onOpen)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("AMPLY ENCORE", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            Text("Your $year Encore", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ready) "$yearPlays plays so far. Ready to play it back?" else "Play ${MIN_ENCORE_PLAYS - yearPlays} more songs to unlock it.",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (isPro) "Play my Encore" else "Unlock Encore",
                color = Color(0xFF5B21B6),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.background(Color.White, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
