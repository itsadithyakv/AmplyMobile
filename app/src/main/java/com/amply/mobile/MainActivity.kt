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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amply.mobile.domain.CachedLyrics
import com.amply.mobile.domain.ArtistInfo
import com.amply.mobile.domain.LyricsCandidate
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.RepeatMode
import com.amply.mobile.domain.Song
import com.amply.mobile.lyrics.parseLrc
import com.amply.mobile.ui.theme.AmplyBg
import com.amply.mobile.ui.theme.AmplyBgPurple
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
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmplyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: MainViewModel = viewModel()
                    AmplyPermissionGate(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AmplyPermissionGate(viewModel: MainViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermissions(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudioPermissions(context)
        if (granted) viewModel.scanLibrary()
    }

    if (granted) {
        AmplyApp(viewModel)
    } else {
        PermissionScreen { launcher.launch(requiredPermissions()) }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    AmplyBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmplyLogo(Modifier.size(118.dp))
            Spacer(Modifier.height(22.dp))
            Text("Amply", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Text("Local music, smart playlists, lyrics.", color = AmplyMuted)
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = AmplyLime, contentColor = Color(0xFF141600)),
            ) {
                Text("Allow music access")
            }
        }
    }
}

@Composable
private fun AmplyApp(viewModel: MainViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val candidates by viewModel.lyricsCandidates.collectAsState()
    val artistInfo by viewModel.artistInfo.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.addMusicFolder(uri)
        }
    }
    val addFolder = { folderPicker.launch(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(Modifier.padding(bottom = 10.dp)) {
                AnimatedVisibility(
                    visible = currentSong != null && selectedTab != AppTab.NowPlaying,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    currentSong?.let {
                        MiniPlayer(
                            song = it,
                            isPlaying = playback.isPlaying,
                            onOpen = { viewModel.selectTab(AppTab.NowPlaying) },
                            onPlayPause = viewModel::togglePlayPause,
                        )
                    }
                }
                GlassBottomNav(selectedTab = selectedTab, onSelect = viewModel::selectTab)
            }
        },
    ) { padding ->
        AmplyBackground {
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (selectedPlaylistId != null) {
                    val playlist = playlists.firstOrNull { it.id == selectedPlaylistId }
                    PlaylistDetailScreen(
                        playlist = playlist,
                        songs = playlist?.songIds?.mapNotNull { id -> songs.firstOrNull { it.id == id } }.orEmpty(),
                        onBack = viewModel::closePlaylist,
                        onPlay = { playlist?.let(viewModel::playPlaylist) },
                        onSong = { song -> viewModel.playSong(song, songs) },
                    )
                } else {
                    AnimatedScreenContainer(selectedTab) { tab ->
                        when (tab) {
                            AppTab.Home -> HomeScreen(
                                songs = songs,
                                playlists = playlists,
                                busy = busy,
                                onScan = viewModel::scanLibrary,
                                onAddFolder = addFolder,
                                onRefreshSmart = viewModel::regenerateSmartPlaylists,
                                onOpenPlaylist = viewModel::openPlaylist,
                                onPlayPlaylist = viewModel::playPlaylist,
                                onSong = { song -> viewModel.playSong(song, songs) },
                                onSearch = { viewModel.selectTab(AppTab.Search) },
                                onSettings = { viewModel.selectTab(AppTab.Settings) },
                            )
                            AppTab.Search -> SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                onQuery = viewModel::updateSearch,
                                onSong = { song -> viewModel.playSong(song, searchResults) },
                            )
                            AppTab.Library -> LibraryScreen(
                                songs = songs,
                                playlists = playlists,
                                onSong = { song -> viewModel.playSong(song, songs) },
                                onFavorite = viewModel::toggleFavorite,
                                onOpenPlaylist = viewModel::openPlaylist,
                                onPlayPlaylist = viewModel::playPlaylist,
                                onAddFolder = addFolder,
                                onScan = viewModel::scanLibrary,
                            )
                            AppTab.NowPlaying -> NowPlayingScreen(
                                song = currentSong,
                                playback = playback,
                                lyrics = lyrics,
                                candidates = candidates,
                                artistInfo = artistInfo,
                                busy = busy,
                                playlists = playlists.filter { it.type == PlaylistType.Custom },
                                onPlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                                onSeek = viewModel::seekTo,
                                onShuffle = viewModel::setShuffle,
                                onRepeat = viewModel::cycleRepeatMode,
                                onFavorite = { currentSong?.let(viewModel::toggleFavorite) },
                                onFetchLyrics = { currentSong?.let(viewModel::fetchLyrics) },
                                onLoadLyrics = { currentSong?.let(viewModel::loadCachedLyrics) },
                                onSaveLyrics = { raw -> currentSong?.let { viewModel.saveEditedLyrics(it.id, raw) } },
                                onCandidate = { candidate -> currentSong?.let { viewModel.selectLyrics(it, candidate) } },
                                onBack = { viewModel.selectTab(AppTab.Home) },
                            )
                            AppTab.Settings -> SettingsScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.selectTab(AppTab.Home) },
                            )
                            AppTab.Playlists -> viewModel.selectTab(AppTab.Library)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmplyBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF242424), AmplyBg, Color(0xFF050505)))),
    ) {
        content()
    }
}

@Composable
private fun AnimatedScreenContainer(target: AppTab, content: @Composable (AppTab) -> Unit) {
    Crossfade(targetState = target, label = "screen-crossfade") { tab ->
        Box(
            Modifier
                .fillMaxSize()
                .animateContentSize(),
        ) {
            content(tab)
        }
    }
}

@Composable
private fun GlassBottomNav(selectedTab: AppTab, onSelect: (AppTab) -> Unit) {
    val items = listOf(
        NavItem(AppTab.Home, "Home", Icons.Rounded.Home),
        NavItem(AppTab.Search, "Search", Icons.Rounded.Search),
        NavItem(AppTab.Library, "Library", Icons.Rounded.LibraryMusic),
        NavItem(AppTab.NowPlaying, "Now", Icons.Rounded.GraphicEq),
    )
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(AmplyGlass)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = selectedTab == item.tab
            val bg by animateColorAsState(if (selected) AmplyLime else Color.Transparent, label = "nav-bg")
            val fg by animateColorAsState(if (selected) Color(0xFF151801) else MaterialTheme.colorScheme.onSurface, label = "nav-fg")
            val scale by animateFloatAsState(if (selected) 1.06f else 1f, label = "nav-scale")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(RoundedCornerShape(26.dp))
                    .background(bg)
                    .clickable { onSelect(item.tab) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.icon, contentDescription = item.label, tint = fg, modifier = Modifier.size(22.dp))
                Text(item.label, color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

private data class NavItem(val tab: AppTab, val label: String, val icon: ImageVector)

@Composable
private fun HomeScreen(
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmplyLogo(Modifier.size(54.dp))
                Spacer(Modifier.weight(1f))
                AmplyIconButton(Icons.Rounded.Search, "Search", onClick = onSearch)
                Spacer(Modifier.width(10.dp))
                OverflowActions(onAddFolder, onScan, onRefreshSmart, onSettings)
            }
            Spacer(Modifier.height(22.dp))
            Text("Hi, Amply", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            Text("${songs.size} local songs ready", color = AmplyMuted)
            if (busy) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = AmplyLime,
                    trackColor = AmplyPanel,
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf("All", "Smart", "Recent", "Favorites")) { label ->
                    AmplyChip(label = label, selected = label == "All", onClick = {})
                }
            }
        }
        playlists.filter { it.type == PlaylistType.Smart }.firstOrNull()?.let { featured ->
            item {
                FeaturedPlaylistCard(
                    playlist = featured,
                    songs = songs,
                    onPlay = { onPlayPlaylist(featured) },
                    onOpen = { onOpenPlaylist(featured.id) },
                )
            }
        }
        homeWheelSections(playlists).forEach { section ->
            item {
                WheelPlaylistSection(
                    title = section.title,
                    playlists = section.playlists,
                    songs = songs,
                    subtitle = section.subtitle,
                    onOpenPlaylist = onOpenPlaylist,
                    onPlayPlaylist = onPlayPlaylist,
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recently added")
                Spacer(Modifier.weight(1f))
                Text("See all", color = AmplyMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (songs.isEmpty() && !busy) {
            item { EmptyLibraryActions(onAddFolder = onAddFolder, onScan = onScan) }
        }
        items(songs.sortedByDescending { it.addedAtSec }.take(12), key = { it.id }) { song ->
            SongListRow(song = song, onClick = { onSong(song) }, onPlay = { onSong(song) })
        }
    }
}

private data class HomeWheelSection(
    val title: String,
    val subtitle: String,
    val playlists: List<Playlist>,
)

private fun homeWheelSections(playlists: List<Playlist>): List<HomeWheelSection> {
    val smart = playlists.filter { it.type == PlaylistType.Smart }
    val custom = playlists.filter { it.type == PlaylistType.Custom }
    return listOf(
        HomeWheelSection(
            title = "Your Top Mixes",
            subtitle = "Big local blends from your library",
            playlists = smart.filter { it.name.contains("mix", true) || it.name.contains("radio", true) }.ifEmpty { smart }.take(10),
        ),
        HomeWheelSection(
            title = "Listen Again",
            subtitle = "Songs worth replaying",
            playlists = smart.filter { it.name.contains("repeat", true) || it.name.contains("recent", true) }.ifEmpty { smart.drop(1).ifEmpty { smart } }.take(10),
        ),
        HomeWheelSection(
            title = "Made for You",
            subtitle = "Generated from your local taste",
            playlists = smart.filter { !it.name.contains("recent", true) }.ifEmpty { smart }.take(10),
        ),
        HomeWheelSection(
            title = "Your Playlists",
            subtitle = "Manual playlists and saved sets",
            playlists = custom.ifEmpty { smart.take(6) }.take(10),
        ),
    ).filter { it.playlists.isNotEmpty() }
}

@Composable
private fun FeaturedPlaylistCard(playlist: Playlist, songs: List<Song>, onPlay: () -> Unit, onOpen: () -> Unit) {
    val artworkUri = playlistBackgroundUri(playlist, songs)
    val bitmap by rememberArtwork(artworkUri)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = AmplyCard),
        shape = RoundedCornerShape(32.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(10.dp).graphicsLayer(alpha = 0.72f),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.76f), Color.Black.copy(alpha = 0.28f)))))
            Column(
                Modifier.fillMaxSize().padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Built from your local library", color = AmplyMuted, maxLines = 1)
                    Text("${playlist.songIds.size} songs", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(58.dp).clip(CircleShape).background(AmplyOrange),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color(0xFF160B02), modifier = Modifier.size(32.dp))
                    }
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Rounded.Save, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun WheelPlaylistSection(
    title: String,
    playlists: List<Playlist>,
    songs: List<Song>,
    subtitle: String,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeIndex by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex + if (listState.firstVisibleItemScrollOffset > 120) 1 else 0)
                .coerceIn(0, playlists.lastIndex)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                SectionTitle(title)
                Text(subtitle, color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text("See all", color = AmplyMuted, style = MaterialTheme.typography.bodyMedium)
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 42.dp),
            horizontalArrangement = Arrangement.spacedBy((-18).dp),
        ) {
            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                val distance = (index - activeIndex).absoluteValue.coerceAtMost(2)
                val selected = index == activeIndex
                val scale by animateFloatAsState(if (selected) 1f else 0.86f - distance * 0.04f, label = "wheel-scale")
                val cardAlpha by animateFloatAsState(if (selected) 1f else 0.46f, label = "wheel-alpha")
                val rotation by animateFloatAsState(((index - activeIndex).coerceIn(-1, 1) * -8).toFloat(), label = "wheel-rotation")
                WheelPlaylistCard(
                    playlist = playlist,
                    songs = songs,
                    selected = selected,
                    modifier = Modifier
                        .width(258.dp)
                        .height(178.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = cardAlpha
                            rotationY = rotation
                            cameraDistance = 18f * density
                        },
                    onClick = {
                        if (selected) onOpenPlaylist(playlist.id) else scope.launch { listState.animateScrollToItem(index) }
                    },
                    onPlay = { onPlayPlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
private fun WheelPlaylistCard(
    playlist: Playlist,
    songs: List<Song>,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val artworkUri = playlistBackgroundUri(playlist, songs)
    val bitmap by rememberArtwork(artworkUri)
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AmplyCard),
        shape = RoundedCornerShape(30.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(if (selected) 4.dp else 8.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = if (selected) 0.18f else 0.42f), Color.Black.copy(alpha = 0.82f)),
                        ),
                    ),
            )
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (playlist.type == PlaylistType.Custom) "Your playlist" else "Smart local mix", color = AmplyMuted, maxLines = 1)
                    Text("${playlist.songIds.size} songs", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(if (selected) AmplyOrange else AmplyPanel),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = if (selected) Color(0xFF160B02) else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun playlistBackgroundUri(playlist: Playlist, songs: List<Song>): String? {
    val byId = songs.associateBy { it.id }
    return playlist.songIds
        .mapNotNull { byId[it]?.artworkUri }
        .firstOrNull()
        ?: playlist.artworkUri
        ?: songs.firstOrNull { playlist.name.contains(it.artist, ignoreCase = true) }?.artworkUri
}

@Composable
private fun SearchScreen(query: String, results: List<Song>, onQuery: (String) -> Unit, onSong: (Song) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Search", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                label = { Text("Songs, artists, albums") },
            )
        }
        items(results, key = { it.id }) { song ->
            SongListRow(song = song, onClick = { onSong(song) }, onPlay = { onSong(song) })
        }
    }
}

private enum class LibraryFilter { All, Playlists, Liked, Genres }

@Composable
private fun LibraryScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    onSong: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onAddFolder: () -> Unit,
    onScan: () -> Unit,
) {
    var filter by remember { mutableStateOf(LibraryFilter.All) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("My Music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                LibraryActions(onAddFolder = onAddFolder, onScan = onScan)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(LibraryFilter.entries) { item ->
                    AmplyChip(
                        label = when (item) {
                            LibraryFilter.All -> "All"
                            LibraryFilter.Playlists -> "Playlists"
                            LibraryFilter.Liked -> "Liked Songs"
                            LibraryFilter.Genres -> "Genres"
                        },
                        selected = filter == item,
                        onClick = { filter = item },
                    )
                }
            }
        }
        if (songs.isEmpty()) {
            item { EmptyLibraryActions(onAddFolder = onAddFolder, onScan = onScan) }
        }
        when (filter) {
            LibraryFilter.All -> items(songs, key = { it.id }) { song ->
                SongListRow(song = song, onClick = { onSong(song) }, onPlay = { onSong(song) }, onFavorite = { onFavorite(song) })
            }
            LibraryFilter.Liked -> items(songs.filter { it.favorite }, key = { it.id }) { song ->
                SongListRow(song = song, onClick = { onSong(song) }, onPlay = { onSong(song) }, onFavorite = { onFavorite(song) })
            }
            LibraryFilter.Playlists -> items(playlists, key = { it.id }) { playlist ->
                PlaylistListRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    onPlay = { onPlayPlaylist(playlist) },
                )
            }
            LibraryFilter.Genres -> items(songs.groupBy { it.effectiveGenre }.toList(), key = { it.first }) { (genre, genreSongs) ->
                GenreRow(genre = genre, count = genreSongs.size, onClick = { genreSongs.firstOrNull()?.let(onSong) })
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist?,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onSong: (Song) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmplyIconButton(Icons.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist?.name ?: "Playlist", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${songs.size} songs", color = AmplyMuted)
                }
                AmplyIconButton(Icons.Rounded.PlayArrow, "Play", onPlay, highlight = true)
            }
        }
        items(songs, key = { it.id }) { song ->
            SongListRow(song = song, onClick = { onSong(song) }, onPlay = { onSong(song) })
        }
    }
}

@Composable
private fun NowPlayingScreen(
    song: Song?,
    playback: com.amply.mobile.domain.AmplyPlaybackState,
    lyrics: CachedLyrics?,
    candidates: List<LyricsCandidate>,
    artistInfo: ArtistInfo?,
    busy: Boolean,
    playlists: List<Playlist>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onFetchLyrics: () -> Unit,
    onLoadLyrics: () -> Unit,
    onSaveLyrics: (String) -> Unit,
    onCandidate: (LyricsCandidate) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(song?.id) { if (song != null) onLoadLyrics() }
    var lyricsOpen by remember(song?.id) { mutableStateOf(false) }
    if (song == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No song playing", color = AmplyMuted)
        }
        return
    }
    val bitmap by rememberArtwork(song.artworkUri)
    val currentLyric = remember(lyrics?.raw, playback.positionMs) {
        currentLyricWindow(lyrics?.raw.orEmpty(), playback.positionMs)
    }
    Box(Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(320.dp).blur(22.dp).graphicsLayer(alpha = 0.22f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x66242424), AmplyBg, AmplyBg))),
        )
        if (lyricsOpen) {
            FullLyricsView(
                lyrics = lyrics,
                positionMs = playback.positionMs,
                onClose = { lyricsOpen = false },
                onFetch = onFetchLyrics,
                playback = playback,
                onSeek = onSeek,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 118.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AmplyIconButton(Icons.Rounded.ArrowBack, "Back", onClick = onBack)
                        Spacer(Modifier.weight(1f))
                        Text("Now Playing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        AmplyIconButton(if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", onFavorite)
                    }
                }
                item {
                    ArtworkBox(song.artworkUri, 242.dp, Modifier.fillMaxWidth(0.78f), shape = RoundedCornerShape(30.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(song.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(song.artist, color = AmplyMuted, style = MaterialTheme.typography.titleMedium)
                }
                item {
                    LyricsPreview(
                        current = currentLyric,
                        busy = busy,
                        onFetch = onFetchLyrics,
                        onOpen = { lyricsOpen = true },
                    )
                }
                item {
                    PlayerProgress(playback = playback, onSeek = onSeek)
                }
                item {
                    PlayerControls(
                        isPlaying = playback.isPlaying,
                        shuffle = playback.shuffle,
                        repeat = playback.repeatMode != RepeatMode.Off,
                        onShuffle = { onShuffle(!playback.shuffle) },
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onRepeat = onRepeat,
                    )
                }
                item {
                    ArtistInfoPanel(artistInfo = artistInfo)
                }
                if (candidates.isNotEmpty()) {
                    item {
                        LyricsPanel(
                            song = song,
                            lyrics = lyrics,
                            candidates = candidates,
                            busy = busy,
                            onFetch = onFetchLyrics,
                            onSave = onSaveLyrics,
                            onCandidate = onCandidate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeat: Boolean,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmplyIconButton(Icons.Rounded.Shuffle, "Shuffle", onShuffle, active = shuffle)
        AmplyIconButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
        val scale by animateFloatAsState(if (isPlaying) 1.04f else 1f, label = "play-scale")
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(CircleShape)
                .background(AmplyLime),
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = Color(0xFF151801),
                modifier = Modifier.size(36.dp),
            )
        }
        AmplyIconButton(Icons.Rounded.SkipNext, "Next", onNext)
        AmplyIconButton(Icons.Rounded.Repeat, "Repeat", onRepeat, active = repeat)
    }
}

@Composable
private fun PlayerProgress(playback: com.amply.mobile.domain.AmplyPlaybackState, onSeek: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = playback.positionMs.toFloat().coerceAtMost(playback.durationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..playback.durationMs.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = AmplyOrange,
                activeTrackColor = AmplyOrange,
                inactiveTrackColor = AmplyMuted.copy(alpha = 0.35f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(playback.positionMs), color = AmplyMuted, style = MaterialTheme.typography.labelSmall)
            Text(formatTime(playback.durationMs), color = AmplyMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LyricsPreview(current: List<String>, busy: Boolean, onFetch: () -> Unit, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth(0.72f), color = AmplyOrange, trackColor = AmplyPanel)
        } else if (current.isEmpty()) {
            OutlinedButton(onClick = onFetch, border = BorderStroke(1.dp, AmplyGlass)) {
                Text("Find lyrics")
            }
        } else {
            current.forEachIndexed { index, line ->
                Text(
                    line,
                    color = if (index == 1) MaterialTheme.colorScheme.onSurface else AmplyMuted.copy(alpha = 0.48f),
                    style = if (index == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FullLyricsView(
    lyrics: CachedLyrics?,
    positionMs: Long,
    onClose: () -> Unit,
    onFetch: () -> Unit,
    playback: com.amply.mobile.domain.AmplyPlaybackState,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val parsed = lyrics?.raw?.let(::parseLrc).orEmpty()
    val activeIndex = parsed.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }.coerceAtLeast(0)
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 118.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmplyIconButton(Icons.Rounded.ArrowBack, "Back", onClick = onClose)
            Spacer(Modifier.width(14.dp))
            Text("Lyrics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(20.dp))
        if (parsed.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = onFetch) { Text("Find lyrics") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(parsed.size) { index ->
                    val line = parsed[index].text
                    if (line.isNotBlank()) {
                        Text(
                            line,
                            color = if (index == activeIndex) MaterialTheme.colorScheme.onSurface else AmplyMuted.copy(alpha = 0.48f),
                            style = if (index == activeIndex) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = if (index == activeIndex) FontWeight.Black else FontWeight.Medium,
                        )
                    }
                }
            }
        }
        PlayerProgress(playback = playback, onSeek = onSeek)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            AmplyIconButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
            AmplyIconButton(if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play", onPlayPause, highlight = true)
            AmplyIconButton(Icons.Rounded.SkipNext, "Next", onNext)
        }
    }
}

@Composable
private fun ArtistInfoPanel(artistInfo: ArtistInfo?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmplyGlass),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Artist info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                artistInfo?.summary ?: "Opps, I cant find that!",
                color = if (artistInfo == null) AmplyMuted else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun currentLyricWindow(raw: String, positionMs: Long): List<String> {
    val parsed = parseLrc(raw).filter { it.text.isNotBlank() }
    if (parsed.isEmpty()) return emptyList()
    val active = parsed.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }
        .takeIf { it >= 0 }
        ?: 0
    return listOf(active - 1, active, active + 1).mapNotNull { parsed.getOrNull(it)?.text }
}

@Composable
private fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmplyIconButton(Icons.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
        }
        item {
            SettingsGroup("Playback") {
                SettingRow("Gapless playback", "Seamless album and queue transitions", settings.gaplessPlayback, viewModel::setGapless)
                SettingRow("Equalizer", "Tune bass, mids, and treble", settings.equalizerEnabled, viewModel::setEqualizerEnabled)
                EqPanel(
                    enabled = settings.equalizerEnabled,
                    bass = settings.eqBass,
                    mid = settings.eqMid,
                    treble = settings.eqTreble,
                    onBass = viewModel::setEqBass,
                    onMid = viewModel::setEqMid,
                    onTreble = viewModel::setEqTreble,
                )
            }
        }
        item {
            SettingsGroup("Library intelligence") {
                SettingRow("Pause metadata fetching", "Stop online artist and genre lookups", settings.metadataFetchPaused, viewModel::setMetadataPaused)
                SettingSlider("Discovery", settings.discoveryIntensity, viewModel::setDiscovery)
                SettingSlider("Randomness", settings.randomnessIntensity, viewModel::setRandomness)
            }
        }
        item {
            Button(onClick = viewModel::enrichUnknownGenres, colors = ButtonDefaults.buttonColors(containerColor = AmplyOrange)) {
                Text("Update missing genres")
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AmplyGlass), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black)
            Text(description, color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
        }
        val track by animateColorAsState(if (checked) AmplyOrange else Color(0xFF3A3A3A), label = "switch-track")
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 34.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(track)
                .clickable { onChecked(!checked) }
                .padding(4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)))
        }
    }
}

@Composable
private fun SettingSlider(title: String, value: Float, onValue: (Float) -> Unit) {
    Column {
        Text(title, fontWeight = FontWeight.Bold)
        Slider(
            value = value,
            onValueChange = onValue,
            colors = SliderDefaults.colors(
                thumbColor = AmplyOrange,
                activeTrackColor = AmplyOrange,
                inactiveTrackColor = AmplyMuted.copy(alpha = 0.28f),
            ),
        )
    }
}

@Composable
private fun EqPanel(
    enabled: Boolean,
    bass: Float,
    mid: Float,
    treble: Float,
    onBass: (Float) -> Unit,
    onMid: (Float) -> Unit,
    onTreble: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EqGraph(values = listOf(bass, mid, treble), enabled = enabled)
        SettingSlider("Bass", bass, onBass)
        SettingSlider("Mid", mid, onMid)
        SettingSlider("Treble", treble, onTreble)
    }
}

@Composable
private fun EqGraph(values: List<Float>, enabled: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AmplyPanel)
            .padding(12.dp),
    ) {
        val step = size.width / (values.size + 1)
        values.forEachIndexed { index, value ->
            val x = step * (index + 1)
            val barHeight = size.height * value.coerceIn(0f, 1f)
            drawRoundRect(
                color = if (enabled) AmplyOrange else AmplyMuted.copy(alpha = 0.35f),
                topLeft = androidx.compose.ui.geometry.Offset(x - 14f, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(28f, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
            )
        }
    }
}

@Composable
private fun LyricsPanel(
    song: Song,
    lyrics: CachedLyrics?,
    candidates: List<LyricsCandidate>,
    busy: Boolean,
    onFetch: () -> Unit,
    onSave: (String) -> Unit,
    onCandidate: (LyricsCandidate) -> Unit,
) {
    var editText by remember(song.id, lyrics?.updatedAtSec) { mutableStateOf(lyrics?.raw.orEmpty()) }
    Card(colors = CardDefaults.cardColors(containerColor = AmplyGlass), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lyrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                AmplyIconButton(Icons.Rounded.Refresh, "Fetch lyrics", onFetch)
                Spacer(Modifier.width(8.dp))
                AmplyIconButton(Icons.Rounded.Save, "Save lyrics", onClick = { onSave(editText) })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AmplyLime, trackColor = AmplyPanel)
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth().height(190.dp),
                minLines = 7,
            )
            candidates.forEach { candidate -> CandidateRow(candidate = candidate, onClick = { onCandidate(candidate) }) }
        }
    }
}

@Composable
private fun CandidateRow(candidate: LyricsCandidate, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AmplyCard),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(candidate.trackName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${candidate.artistName}${if (candidate.synced) " • synced" else ""}", color = AmplyMuted)
            Text(candidate.preview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MiniPlayer(song: Song, isPlaying: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 22.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(AmplyGlass)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(song.artworkUri, 48.dp, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(42.dp).clip(CircleShape).background(AmplyPanel)) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play")
        }
    }
}

@Composable
private fun SongListRow(song: Song, onClick: () -> Unit, onPlay: () -> Unit, onFavorite: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(song.artworkUri, 62.dp, shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
            Text("${song.artist}  •  ${song.effectiveGenre}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (onFavorite != null) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(42.dp)) {
                Icon(if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = if (song.favorite) AmplyOrange else MaterialTheme.colorScheme.onSurface)
            }
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(44.dp).clip(CircleShape).background(AmplyPanel)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PlaylistHeroCard(playlist: Playlist, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(286.dp).height(150.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AmplyOrangeSoftCard()),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(playlist.name, color = Color(0xFF120B02), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${playlist.songIds.size} songs", color = Color(0xCC120B02))
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF120B02), modifier = Modifier.size(34.dp))
            }
            ArtworkBox(playlist.artworkUri, 96.dp, shape = RoundedCornerShape(22.dp))
        }
    }
}

@Composable
private fun PlaylistListRow(playlist: Playlist, onClick: () -> Unit, onPlay: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(playlist.artworkUri, 62.dp, shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.songIds.size} songs", color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(44.dp).clip(CircleShape).background(AmplyPanel)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
        }
    }
}

@Composable
private fun GenreRow(genre: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AmplyCard).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = AmplyOrange)
        Spacer(Modifier.width(14.dp))
        Text(genre, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text("$count", color = AmplyLime)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
}

@Composable
private fun EmptyLibraryActions(onAddFolder: () -> Unit, onScan: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmplyGlass),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmplyLogo(Modifier.size(86.dp))
            Text("No songs found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Choose your music folder and Amply will scan it automatically.", color = AmplyMuted)
            Button(
                onClick = onAddFolder,
                colors = ButtonDefaults.buttonColors(containerColor = AmplyLime, contentColor = Color(0xFF151801)),
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add folder")
            }
            OutlinedButton(onClick = onScan) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan device")
            }
        }
    }
}

@Composable
private fun OverflowActions(onAddFolder: () -> Unit, onScan: () -> Unit, onRefreshSmart: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AmplyIconButton(Icons.Rounded.MoreHoriz, "More", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Add folder") }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) }, onClick = { open = false; onAddFolder() })
            DropdownMenuItem(text = { Text("Scan device") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { open = false; onScan() })
            DropdownMenuItem(text = { Text("Refresh smart playlists") }, leadingIcon = { Icon(Icons.Rounded.Shuffle, null) }, onClick = { open = false; onRefreshSmart() })
            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Rounded.Settings, null) }, onClick = { open = false; onSettings() })
        }
    }
}

@Composable
private fun LibraryActions(onAddFolder: () -> Unit, onScan: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AmplyIconButton(Icons.Rounded.MoreHoriz, "Library actions", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Add folder") }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) }, onClick = { open = false; onAddFolder() })
            DropdownMenuItem(text = { Text("Scan device") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { open = false; onScan() })
        }
    }
}

@Composable
private fun AmplyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) AmplyLime else AmplyCard, label = "chip-bg")
    val fg by animateColorAsState(if (selected) Color(0xFF151801) else MaterialTheme.colorScheme.onSurface, label = "chip-fg")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AmplyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
    active: Boolean = false,
) {
    val bg by animateColorAsState(
        when {
            highlight || active -> AmplyLime
            else -> AmplyCard
        },
        label = "icon-bg",
    )
    val fg by animateColorAsState(if (highlight || active) Color(0xFF151801) else MaterialTheme.colorScheme.onSurface, label = "icon-fg")
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(bg),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg)
    }
}

@Composable
private fun ArtworkBox(
    artworkUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
) {
    val bitmap by rememberArtwork(artworkUri)
    Box(
        modifier = modifier.size(size).aspectRatio(1f).clip(shape).background(AmplyPanel),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            AmplyLogo(Modifier.fillMaxSize().padding(size * 0.18f))
        }
    }
}

@Composable
private fun AmplyLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_amply),
        contentDescription = "Amply",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
private fun rememberArtwork(uri: String?): androidx.compose.runtime.State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uri == null) {
                    null
                } else {
                    context.contentResolver.openInputStream(Uri.parse(uri)).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            }.getOrNull()
        }
    }
}

private fun AmplyOrangeSoftCard(): Color = Color(0xFFE9A86E)

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun requiredPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}.toTypedArray()

private fun hasAudioPermissions(context: Context): Boolean =
    requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
