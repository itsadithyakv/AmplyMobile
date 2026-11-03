package com.amply.mobile

import com.amply.mobile.util.plural
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.amply.mobile.ui.theme.NeuDepth
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuPressed
import com.amply.mobile.ui.theme.neuRaised
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.Song

@Composable
fun HomeScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    busy: Boolean,
    onScan: () -> Unit,
    onAddFolder: () -> Unit,
    onRefreshSmart: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onSong: (Song) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlayArtist: (List<Song>) -> Unit,
    planLabel: String? = null,
    showProMixesTeaser: Boolean = false,
    onProMixes: () -> Unit = {},
) {
    val p = Palette
    val explorePlaylists = remember(playlists) { pickExplorePlaylists(playlists) }
    val recentSongs = remember(songs) { songs.recentlyPlayedOrAdded().take(12) }
    val libraryArtists = remember(songs) { pickLibraryArtists(songs) }
    // Built once per library change; every card used to rebuild this map for itself.
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val genreMixes = remember(playlists) { pickHomeGenreMixes(playlists) }
    val forYouToday = remember(playlists) { pickForYouToday(playlists) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmplyLogo(Modifier.size(38.dp))
                Spacer(Modifier.width(8.dp))
                Text("Amply", style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.045).em))
                if (planLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        planLabel,
                        color = p.textSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.08.em),
                        modifier = Modifier.neuPressed(CircleShape).padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (songs.isNotEmpty()) {
                    NeuIconButton(Icons.Rounded.Shuffle, "Shuffle all songs", onShuffleAll, size = 42.dp)
                    Spacer(Modifier.width(10.dp))
                }
                NeuIconButton(Icons.Rounded.Search, "Search", onSearch, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                OverflowActions(onAddFolder, onScan, onRefreshSmart, onSettings)
            }
            if (busy) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = p.accent,
                    trackColor = p.bgDeep,
                )
            }
        }
        if (songs.isEmpty()) {
            if (!busy) item { EmptyLibraryActions(onAddFolder = onAddFolder, onScan = onScan) }
            return@LazyColumn
        }
        val featured = explorePlaylists.firstOrNull()
        if (featured != null) {
            item {
                FeaturedMixCard(
                    playlist = featured,
                    artworkUri = playlistBackgroundUri(featured, songsById),
                    onOpen = { onOpenPlaylist(featured.id) },
                    onPlay = { onPlayPlaylist(featured) },
                )
            }
        } else if (!busy) {
            item { HomeExploreEmptyState(onRefresh = onRefreshSmart) }
        }
        val morePlaylists = explorePlaylists.drop(1)
        if (morePlaylists.isNotEmpty()) {
            item {
                TileRow("Smart Playlists", morePlaylists, key = { it.id }) { playlist ->
                    SmartMixCard(playlist, songsById, onOpen = { onOpenPlaylist(playlist.id) }, onPlay = { onPlayPlaylist(playlist) })
                }
            }
        }
        if (recentSongs.isNotEmpty()) {
            item {
                TileRow(if (recentSongs.any { it.lastPlayedAtSec != null }) "Recently Played" else "Recently Added", recentSongs, key = { it.id }) { song ->
                    SongTile(song = song, onPlay = { onSong(song) })
                }
            }
        }
        if (libraryArtists.isNotEmpty()) {
            item {
                SectionHeader(title = "Top Artists")
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    items(libraryArtists, key = { it.name }) { artist ->
                        ArtistBubble(artist = artist, onClick = { onPlayArtist(artist.songs) })
                    }
                }
            }
        }
        if (genreMixes.isNotEmpty()) {
            item {
                TileRow("Genre Mixes", genreMixes, key = { it.id }) { playlist ->
                    SmartMixCard(playlist, songsById, onOpen = { onOpenPlaylist(playlist.id) }, onPlay = { onPlayPlaylist(playlist) })
                }
            }
        }
        if (showProMixesTeaser) {
            item {
                NeuCard(Modifier.clickable(onClickLabel = "Learn about Amply Pro", onClick = onProMixes)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Genre and mood mixes", style = MaterialTheme.typography.titleMedium)
                            Text("A mix for every genre and mood in your library, with Amply Pro.", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        ProLock(Modifier.size(18.dp))
                    }
                }
            }
        }
        if (forYouToday.isNotEmpty()) {
            item {
                TileRow("For You Today", forYouToday, key = { it.id }) { playlist ->
                    SmartMixCard(playlist, songsById, onOpen = { onOpenPlaylist(playlist.id) }, onPlay = { onPlayPlaylist(playlist) })
                }
            }
        }
    }
}

/** A titled horizontal row of tiles. Vertical padding leaves room for the tiles' shadows. */
@Composable
fun <T> TileRow(title: String, items: List<T>, key: (T) -> Any, tile: @Composable (T) -> Unit) {
    Column {
        SectionHeader(title = title)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        ) {
            items(items, key = key) { tile(it) }
        }
    }
}

/** The desktop's "Featured mix" hero: cover on the right fading into the surface, text on the left. */
@Composable
fun FeaturedMixCard(playlist: Playlist, artworkUri: String?, onOpen: () -> Unit, onPlay: () -> Unit) {
    val p = Palette
    val bitmap by rememberArtwork(artworkUri, 768)
    val subtitle = playlist.description.ifBlank { if (playlist.type == PlaylistType.Custom) "Your playlist" else "Made by Amply" }
    Box(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .neuRaised(RadiusLg, NeuDepth.Raised)
            .clip(RadiusLg)
            .clickable(onClickLabel = "Open ${playlist.name}", onClick = onOpen),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.CenterEnd),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                // Fully opaque until just past where the cover starts (38%), so its edge never shows.
                Brush.horizontalGradient(0f to p.bg, 0.42f to p.bg, 0.72f to p.bg.copy(alpha = 0.45f), 1f to p.bg.copy(alpha = 0f)),
            ),
        )
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth(0.66f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Featured mix")
                Text(playlist.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuButton("Play", Icons.Rounded.PlayArrow, onPlay, accent = true)
                Spacer(Modifier.width(12.dp))
                Text(plural(playlist.songIds.size, "song"), color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Raised tile: cover, title and subtitle, with an amber play button on the cover. */
@Composable
private fun CoverTile(
    title: String,
    subtitle: String,
    artworkUri: String?,
    audioUri: String?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val p = Palette
    Column(
        Modifier
            .width(146.dp)
            .neuRaised(RadiusLg, NeuDepth.Raised)
            .clip(RadiusLg)
            .clickable(onClickLabel = "Open $title", onClick = onOpen)
            .padding(9.dp),
    ) {
        Box {
            ArtworkBox(artworkUri, 128.dp, shape = RadiusMd, audioUri = audioUri)
            Box(Modifier.align(Alignment.BottomEnd).padding(7.dp)) {
                NeuIconButton(Icons.Rounded.PlayArrow, "Play $title", onPlay, size = 36.dp, accent = true)
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 3.dp))
        Text(subtitle, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 3.dp))
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
fun SongTile(song: Song, onPlay: () -> Unit) =
    CoverTile(song.title, song.artist, song.artworkUri, song.contentUri, onOpen = onPlay, onPlay = onPlay)

@Composable
fun SmartMixCard(
    playlist: Playlist,
    songsById: Map<Long, Song>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val subtitle = when {
        playlist.type == PlaylistType.Custom -> "Your playlist  ·  ${plural(playlist.songIds.size, "song")}"
        playlist.description.isNotBlank() -> playlist.description
        else -> plural(playlist.songIds.size, "song")
    }
    CoverTile(playlist.name, subtitle, playlistBackgroundUri(playlist, songsById), null, onOpen, onPlay)
}

/** Songs exist but no mixes yet (they are generated after a scan). */
@Composable
fun HomeExploreEmptyState(onRefresh: () -> Unit) {
    NeuCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Smart playlists", style = MaterialTheme.typography.titleLarge)
            Text("Amply builds daily mixes, genre and mood mixes from what you play.", color = Palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
            NeuButton("Build my mixes", Icons.Rounded.Shuffle, onRefresh, accent = true)
        }
    }
}

data class LibraryArtist(val name: String, val artworkUri: String?, val songs: List<Song>)

@Composable
fun ArtistBubble(artist: LibraryArtist, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(84.dp).clip(RadiusMd).clickable(onClickLabel = "Play ${artist.name}", onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.neuRaised(CircleShape, NeuDepth.Raised).padding(4.dp)) {
            ArtworkBox(artist.artworkUri, 66.dp, shape = CircleShape, audioUri = artist.songs.firstOrNull()?.contentUri)
        }
        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(title)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(action, color = Palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun pickExplorePlaylists(playlists: List<Playlist>): List<Playlist> {
    val playable = playlists.filter { it.songIds.isNotEmpty() }
    val daily = playable.firstOrNull(::isDailyPlaylist)
    val custom = playable.filter { it.type == PlaylistType.Custom && it.id != daily?.id }
    val smart = playable.filter { it.type == PlaylistType.Smart && it.id != daily?.id }
    val source = custom
        .sortedByDescending { it.updatedAtSec } +
        smart
            .sortedWith(
                compareByDescending<Playlist> { playlistPriority(it) }
                    .thenByDescending { it.updatedAtSec }
                    .thenByDescending { it.songIds.size },
            )
    return (listOfNotNull(daily) + source)
        .distinctBy { it.id }
        .take(10)
}

fun pickHomeGenreMixes(playlists: List<Playlist>): List<Playlist> =
    playlists.smartHomePlaylists()
        .filter { playlist ->
            val text = "${playlist.id} ${playlist.name}".lowercase()
            "genre" in text ||
                "pop" in text ||
                "rock" in text ||
                "rap" in text ||
                "hip-hop" in text ||
                "hip hop" in text ||
                "alternative" in text ||
                "electronic" in text ||
                "indie" in text
        }
        .take(12)

fun pickForYouToday(playlists: List<Playlist>): List<Playlist> =
    playlists.smartHomePlaylists()
        .filter { playlist ->
            val text = "${playlist.id} ${playlist.name}".lowercase()
            "daily" in text || "today" in text || "vibe" in text || "repeat" in text || "rediscover" in text
        }
        .sortedByDescending { playlistPriority(it) }
        .take(12)

fun isDailyPlaylist(playlist: Playlist): Boolean {
    val text = "${playlist.id} ${playlist.name}".lowercase()
    return "smart_daily_mix" in text || "daily mix" in text || "daily discovery" in text
}

fun List<Playlist>.smartHomePlaylists(): List<Playlist> =
    filter { it.type == PlaylistType.Smart && it.songIds.isNotEmpty() }
        .sortedWith(
            compareByDescending<Playlist> { playlistPriority(it) }
                .thenByDescending { it.updatedAtSec }
                .thenByDescending { it.songIds.size },
        )
        .distinctBy { it.id }

fun List<Song>.recentlyPlayedOrAdded(): List<Song> =
    sortedWith(
        compareByDescending<Song> { it.lastPlayedAtSec ?: 0L }
            .thenByDescending { it.addedAtSec },
    )

/** Artists ranked by how much they are actually played (the old list was alphabetical). */
fun pickLibraryArtists(songs: List<Song>): List<LibraryArtist> =
    songs
        .filter { it.artist.isNotBlank() && !it.artist.startsWith("unknown", ignoreCase = true) }
        .groupBy { it.artist.trim() }
        .map { (artist, artistSongs) ->
            val ordered = artistSongs.sortedWith(
                compareByDescending<Song> { it.playCount }.thenByDescending { it.lastPlayedAtSec ?: 0L }.thenByDescending { it.addedAtSec },
            )
            Triple(artist, ordered, artistSongs.sumOf { it.totalPlayMs })
        }
        .sortedWith(compareByDescending<Triple<String, List<Song>, Long>> { it.third }.thenByDescending { it.second.size }.thenBy { it.first.lowercase() })
        .take(12)
        .map { (artist, ordered, _) -> LibraryArtist(name = artist, artworkUri = ordered.firstNotNullOfOrNull { it.artworkUri }, songs = ordered) }

fun playlistPriority(playlist: Playlist): Int {
    val text = "${playlist.id} ${playlist.name}".lowercase()
    return when {
        "daily" in text -> 100
        "today" in text -> 95
        "repeat" in text -> 90
        "favorite" in text -> 84
        "mood" in text || "happy" in text || "sad" in text || "chill" in text || "focus" in text || "energy" in text -> 78
        "genre" in text || "mix" in text -> 70
        "artist" in text || "radio" in text -> 64
        playlist.type == PlaylistType.Custom -> 58
        else -> 40
    }
}

fun playlistBackgroundUri(playlist: Playlist, songsById: Map<Long, Song>): String? {
    // Mixes often start with the same top song; picking a cover by playlist id keeps tiles distinct.
    val covers = playlist.songIds.asSequence().take(12).mapNotNull { songsById[it]?.artworkUri }.distinct().toList()
    if (covers.isEmpty()) return playlist.artworkUri
    return covers[(playlist.id.hashCode() and Int.MAX_VALUE) % covers.size]
}

fun String.cleanMixName(): String =
    replace(" Mix", "")
        .replace(" Radio", "")
        .trim()
        .ifBlank { this }

