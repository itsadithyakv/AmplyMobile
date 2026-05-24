package com.amply.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amply.mobile.domain.CachedLyrics
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.RepeatMode
import com.amply.mobile.domain.Song
import com.amply.mobile.lyrics.parseLrc
import com.amply.mobile.ui.theme.AmplyBg
import com.amply.mobile.ui.theme.AmplyBgPurple
import com.amply.mobile.ui.theme.AmplyBrand
import com.amply.mobile.ui.theme.AmplyCard
import com.amply.mobile.ui.theme.AmplyGlass
import com.amply.mobile.ui.theme.AmplyLime
import com.amply.mobile.ui.theme.AmplyMuted
import com.amply.mobile.ui.theme.AmplyOrange
import com.amply.mobile.ui.theme.AmplyPanel
import com.amply.mobile.ui.theme.AmplyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt


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
) {
    val explorePlaylists = remember(playlists) { pickExplorePlaylists(playlists) }
    val recentSongs = remember(songs) { songs.recentlyPlayedOrAdded().take(12) }
    val libraryArtists = remember(songs) { pickLibraryArtists(songs) }
    val genreMixes = remember(playlists) { pickHomeGenreMixes(playlists) }
    val forYouToday = remember(playlists) { pickForYouToday(playlists) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 126.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmplyLogo(Modifier.size(54.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Amply",
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = AmplyBrand,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                AmplyIconButton(Icons.Rounded.Search, "Search", onClick = onSearch)
                Spacer(Modifier.width(10.dp))
                OverflowActions(onAddFolder, onScan, onRefreshSmart, onSettings)
            }
            if (busy) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = AmplyOrange,
                    trackColor = AmplyPanel,
                )
            }
        }
        if (explorePlaylists.isNotEmpty()) {
            item {
                PlaylistExploreCarousel(
                    playlists = explorePlaylists,
                    songs = songs,
                    onOpenPlaylist = onOpenPlaylist,
                    onPlayPlaylist = onPlayPlaylist,
                )
            }
        } else if (!busy) {
            item { HomeExploreEmptyState(onAddFolder = onAddFolder, onScan = onScan) }
        }
        if (recentSongs.isNotEmpty()) {
            item {
                SectionHeader(title = "Recently Played", action = if (recentSongs.size > 4) "See all" else null)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(recentSongs, key = { it.id }) { song ->
                        HomeSectionCard(item = HomeQuickItem.SongItem(song), songs = songs, onSong = onSong, onOpenPlaylist = onOpenPlaylist, onPlayPlaylist = onPlayPlaylist)
                    }
                }
            }
        }
        if (libraryArtists.isNotEmpty()) {
            item {
                SectionHeader(title = "Top Artists")
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(libraryArtists, key = { it.name }) { artist ->
                        ArtistBubble(artist = artist, onClick = { onSong(artist.song) })
                    }
                }
            }
        }
        if (genreMixes.isNotEmpty()) {
            item {
                SectionHeader(title = "Genre Mixes", action = if (genreMixes.size > 4) "See all" else null)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(genreMixes, key = { it.id }) { playlist ->
                        HomeSectionCard(item = HomeQuickItem.PlaylistItem(playlist), songs = songs, onSong = onSong, onOpenPlaylist = onOpenPlaylist, onPlayPlaylist = onPlayPlaylist)
                    }
                }
            }
        }
        if (forYouToday.isNotEmpty()) {
            item {
                SectionHeader(title = "For You Today", action = if (forYouToday.size > 4) "See all" else null)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(forYouToday, key = { it.id }) { playlist ->
                        HomeSectionCard(item = HomeQuickItem.PlaylistItem(playlist), songs = songs, onSong = onSong, onOpenPlaylist = onOpenPlaylist, onPlayPlaylist = onPlayPlaylist)
                    }
                }
            }
        }
        if (songs.isEmpty() && !busy) {
            item { EmptyLibraryActions(onAddFolder = onAddFolder, onScan = onScan) }
        }
    }
}

@Composable
fun PlaylistExploreCarousel(
    playlists: List<Playlist>,
    songs: List<Song>,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
) {
    val listState = rememberLazyListState()
    val selectedIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> abs((item.offset + item.size / 2) - viewportCenter) }
                ?.index
                ?: 0
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(title = "Explore")
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth().height(252.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                val index = playlists.indexOfFirst { it.id == playlist.id }
                val selected = index == selectedIndex
                ExplorePlaylistCard(
                    playlist = playlist,
                    songs = songs,
                    selected = selected,
                    onOpen = { onOpenPlaylist(playlist.id) },
                    onPlay = { onPlayPlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
fun ExplorePlaylistCard(
    playlist: Playlist,
    songs: List<Song>,
    selected: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val glowAlpha by animateFloatAsState(if (selected) 0.40f else 0f, animationSpec = tween(260), label = "explore-glow")
    val cardAlpha by animateFloatAsState(if (selected) 1f else 0.70f, animationSpec = tween(260), label = "explore-alpha")
    val cardScale by animateFloatAsState(if (selected) 1f else 0.94f, animationSpec = tween(260), label = "explore-scale")
    val subtitle = playlist.description.ifBlank { if (playlist.type == PlaylistType.Custom) "Your playlist" else "Made by Amply" }
    val songCount = playlist.songIds.size
    Box(
        modifier = Modifier
            .width(274.dp)
            .fillMaxHeight()
            .graphicsLayer {
                alpha = cardAlpha
                scaleX = cardScale
                scaleY = cardScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(alpha = glowAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AmplyOrange.copy(alpha = 0.62f), AmplyOrange.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(148f, 118f),
                        radius = 245f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF242424), Color(0xFF151515), Color(0xFF0D0D0D)),
                    ),
                )
                .clickable(onClick = onOpen)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkBox(playlistBackgroundUri(playlist, songs), 84.dp, shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(playlist.name.cleanMixName(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$songCount songs", color = AmplyMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = AmplyOrange, contentColor = Color(0xFF160B02)),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

sealed class HomeQuickItem {
    abstract val key: String
    abstract val title: String

    data class SongItem(val song: Song) : HomeQuickItem() {
        override val key: String = "song-${song.id}"
        override val title: String = song.title
    }

    data class PlaylistItem(val playlist: Playlist) : HomeQuickItem() {
        override val key: String = "playlist-${playlist.id}"
        override val title: String = playlist.name
    }
}

object AmplyHomeCardShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val c = min(w, h) * 0.14f
        val k = 0.55f
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            cubicTo(w - c * k, 0f, w, c * k, w, c)
            lineTo(w, h - c)
            cubicTo(w, h - c * k, w - c * k, h, w - c, h)
            lineTo(c, h)
            cubicTo(c * k, h, 0f, h - c * k, 0f, h - c)
            lineTo(0f, c)
            cubicTo(0f, c * k, c * k, 0f, c, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun HomeSectionCard(
    item: HomeQuickItem,
    songs: List<Song>,
    onSong: (Song) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
) {
    when (item) {
        is HomeQuickItem.PlaylistItem -> SmartMixCard(
            playlist = item.playlist,
            songs = songs,
            onOpen = { onOpenPlaylist(item.playlist.id) },
            onPlay = { onPlayPlaylist(item.playlist) },
        )
        is HomeQuickItem.SongItem -> SongSquircleCard(song = item.song, onClick = { onSong(item.song) }, onPlay = { onSong(item.song) })
    }
}

@Composable
fun SongSquircleCard(song: Song, onClick: () -> Unit, onPlay: () -> Unit) {
    val bitmap by rememberArtwork(song.artworkUri)
    Box(
        modifier = Modifier
            .width(128.dp)
            .height(142.dp)
            .clip(AmplyHomeCardShape)
            .background(AmplyCard)
            .clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize().blur(4.dp), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Black.copy(alpha = 0.86f)))))
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(song.artist, color = AmplyMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onPlay, modifier = Modifier.size(34.dp).clip(CircleShape).background(AmplyOrange)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color(0xFF160B02), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SmartMixCard(
    playlist: Playlist,
    songs: List<Song>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val bitmap by rememberArtwork(playlistBackgroundUri(playlist, songs))
    val subtitle = if (playlist.type == PlaylistType.Custom) "Your playlist" else playlist.description.ifBlank { "Made by Amply" }
    Box(
        modifier = Modifier
            .width(128.dp)
            .height(142.dp)
            .clip(AmplyHomeCardShape)
            .background(AmplyCard)
            .clickable(onClick = onOpen),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(5.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.86f)))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(playlist.name.cleanMixName(), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subtitle, color = AmplyMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(AmplyOrange),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color(0xFF160B02), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun HomeExploreEmptyState(onAddFolder: () -> Unit, onScan: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AmplyGlass), shape = AmplyHomeCardShape) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Explore", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text("Add songs to start building your local home screen.", color = AmplyMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddFolder, colors = ButtonDefaults.buttonColors(containerColor = AmplyOrange)) {
                    Text("Add folder")
                }
                OutlinedButton(onClick = onScan) {
                    Text("Scan")
                }
            }
        }
    }
}

data class LibraryArtist(val name: String, val artworkUri: String?, val song: Song)

@Composable
fun ArtistBubble(artist: LibraryArtist, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(88.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArtworkBox(artist.artworkUri, 78.dp, shape = CircleShape)
        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(title)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(action, color = AmplyMuted, style = MaterialTheme.typography.bodyMedium)
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

fun pickLibraryArtists(songs: List<Song>): List<LibraryArtist> =
    songs
        .filter { it.artist.isNotBlank() && !it.artist.equals("unknown", ignoreCase = true) }
        .groupBy { it.artist.trim() }
        .mapNotNull { (artist, artistSongs) ->
            val bestSong = artistSongs
                .sortedWith(compareByDescending<Song> { it.lastPlayedAtSec ?: 0L }.thenByDescending { it.addedAtSec })
                .firstOrNull()
            bestSong?.let { LibraryArtist(name = artist, artworkUri = it.artworkUri, song = it) }
        }
        .sortedBy { it.name.lowercase() }
        .take(12)

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

fun playlistBackgroundUri(playlist: Playlist, songs: List<Song>): String? {
    val byId = songs.associateBy { it.id }
    return playlist.songIds
        .mapNotNull { byId[it]?.artworkUri }
        .firstOrNull()
        ?: playlist.artworkUri
        ?: songs.firstOrNull { playlist.name.contains(it.artist, ignoreCase = true) }?.artworkUri
}

fun String.cleanMixName(): String =
    replace(" Mix", "")
        .replace(" Radio", "")
        .trim()
        .ifBlank { this }

