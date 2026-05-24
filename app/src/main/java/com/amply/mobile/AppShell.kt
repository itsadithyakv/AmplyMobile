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
fun AmplyPermissionGate(viewModel: MainViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermissions(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudioPermissions(context)
    }

    if (granted) {
        AmplyApp(viewModel)
    } else {
        PermissionScreen { launcher.launch(requiredPermissions()) }
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    AmplyBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmplyLogo(Modifier.size(118.dp))
            Spacer(Modifier.height(22.dp))
            Text("Amply", style = MaterialTheme.typography.displayMedium)
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
fun AmplyApp(viewModel: MainViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
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
            Column(
                Modifier
                    .padding(horizontal = 22.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(AmplyGlass)
                    .animateContentSize(animationSpec = tween(320)),
            ) {
                AnimatedVisibility(
                    visible = currentSong != null && selectedTab != AppTab.NowPlaying,
                    enter = slideInVertically(animationSpec = tween(320)) { it } + fadeIn(animationSpec = tween(220)),
                    exit = slideOutVertically(animationSpec = tween(260)) { it } + fadeOut(animationSpec = tween(160)),
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
                                onSong = viewModel::playSingleThenDaily,
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
                                busy = busy,
                                onPlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                                onSeek = viewModel::seekTo,
                                onShuffle = viewModel::setShuffle,
                                onRepeat = viewModel::cycleRepeatMode,
                                onRepeatMode = viewModel::setRepeatMode,
                                onFavorite = { currentSong?.let(viewModel::toggleFavorite) },
                                onFetchLyrics = { currentSong?.let(viewModel::fetchLyrics) },
                                onLoadLyrics = { currentSong?.let(viewModel::loadCachedLyrics) },
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
fun AmplyBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF242424), AmplyBg, Color(0xFF050505)))),
    ) {
        content()
    }
}

@Composable
fun AnimatedScreenContainer(target: AppTab, content: @Composable (AppTab) -> Unit) {
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
fun GlassBottomNav(selectedTab: AppTab, onSelect: (AppTab) -> Unit) {
    val items = listOf(
        NavItem(AppTab.Home, "Home", Icons.Rounded.Home),
        NavItem(AppTab.Search, "Search", Icons.Rounded.Search),
        NavItem(AppTab.Library, "Library", Icons.Rounded.LibraryMusic),
        NavItem(AppTab.NowPlaying, "Now", Icons.Rounded.GraphicEq),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = selectedTab == item.tab
            val bg by animateColorAsState(if (selected) AmplyOrange else Color.Transparent, label = "nav-bg")
            val fg by animateColorAsState(if (selected) Color(0xFF160B02) else MaterialTheme.colorScheme.onSurface, label = "nav-fg")
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

data class NavItem(val tab: AppTab, val label: String, val icon: ImageVector)


fun requiredPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}.toTypedArray()

fun hasAudioPermissions(context: Context): Boolean =
    requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
