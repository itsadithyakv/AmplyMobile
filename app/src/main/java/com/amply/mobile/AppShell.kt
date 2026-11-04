package com.amply.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import com.amply.mobile.playlist.RulePlaylist
import com.amply.mobile.pro.ProFeature
import com.amply.mobile.pro.isProMix
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.Song
import com.amply.mobile.ui.theme.AmplyMuted
import com.amply.mobile.ui.theme.NeuDepth
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuPressed
import com.amply.mobile.ui.theme.neuRaised

/**
 * Asks for audio access, and for notification access alongside it on Android 13+. Only audio access
 * is required: without the notification permission playback still works, Android just hides the
 * media notification. The old gate required both, so denying notifications locked the app forever.
 */
@Composable
fun AmplyPermissionGate(viewModel: MainViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermissions(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var skipped by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        granted = hasAudioPermissions(context)
    }
    // Coming back from the system settings page is not a permission result, so re-check on resume.
    LifecycleResumeEffect(Unit) {
        granted = hasAudioPermissions(context)
        onPauseOrDispose { }
    }
    // Picking a folder through the system picker needs no storage permission, so allow that path too.
    if (granted || skipped) {
        AmplyApp(viewModel)
    } else {
        val permanentlyDenied = asked && !shouldShowAudioRationale(context)
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onGrant = {
                if (permanentlyDenied) openAppSettings(context) else launcher.launch(requestedPermissions())
            },
            onSkip = { skipped = true },
        )
    }
}

@Composable
fun PermissionScreen(permanentlyDenied: Boolean, onGrant: () -> Unit, onSkip: () -> Unit) {
    AmplyBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmplyLogo(Modifier.size(118.dp))
            Spacer(Modifier.height(22.dp))
            Text("Amply", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(4.dp))
            Text("Local music, smart playlists, lyrics.", color = AmplyMuted)
            Spacer(Modifier.height(18.dp))
            Text(
                if (permanentlyDenied) {
                    "Music access is turned off for Amply. Turn on \"Music and audio\" in the app's settings to scan your library."
                } else {
                    "Amply needs access to the music on this phone to build your library. Nothing leaves your device."
                },
                color = AmplyMuted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(26.dp))
            NeuButton(if (permanentlyDenied) "Open settings" else "Allow music access", null, onGrant, accent = true)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSkip) {
                Text("Pick a music folder instead", color = AmplyMuted)
            }
        }
    }
}

@Composable
fun AmplyApp(viewModel: MainViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val proState by viewModel.proState.collectAsStateWithLifecycle()
    val paywall by viewModel.paywall.collectAsStateWithLifecycle()
    val encore by viewModel.encore.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val statsRange by viewModel.statsRange.collectAsStateWithLifecycle()
    val yearPlays by viewModel.yearPlays.collectAsStateWithLifecycle()
    val ruleResults by viewModel.ruleResults.collectAsStateWithLifecycle()
    var ruleEditor by remember { mutableStateOf<RulePlaylist?>(null) }
    var ruleEditorOpen by remember { mutableStateOf(false) }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    // Genre and mood mixes are Pro (desktop "moreMixes"); free users see the core mixes.
    val visiblePlaylists = remember(playlists, isPro) { if (isPro) playlists else playlists.filterNot { isProMix(it.id) } }
    val activity = LocalContext.current as? android.app.Activity

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.addMusicFolder(uri)
        }
    }
    val addFolder = { folderPicker.launch(null) }

    // Song menu state lives here so every list can open the same sheet.
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var actionPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    val openActions: (Song, Playlist?) -> Unit = { song, playlist ->
        actionSong = song
        actionPlaylist = playlist
    }

    // System back walks the in-app history (playlist -> library, settings -> home, ...).
    BackHandler(enabled = canGoBack) { viewModel.goBack() }

    LaunchedEffect(proState.message) {
        proState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearProMessage()
        }
    }

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
            // Now Playing is a full-screen player; the bar would only repeat its controls.
            if (selectedTab == AppTab.NowPlaying && selectedPlaylistId == null) return@Scaffold
            // The desktop player bar: one raised slab holding the now-playing strip and the tabs.
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .neuRaised(RadiusLg, NeuDepth.Raised)
                    .clip(RadiusLg)
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
                            durationMs = playback.durationMs,
                            position = viewModel.playbackPosition,
                            onOpen = { viewModel.open(AppTab.NowPlaying) },
                            onPlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                            onFavorite = { viewModel.toggleFavorite(it) },
                        )
                    }
                }
                GlassBottomNav(selectedTab = selectedTab, onSelect = viewModel::selectTab)
            }
        },
    ) { padding ->
        AmplyBackground {
            // Now Playing draws its blurred cover up to the top edge and pads its own content.
            val fullBleed = selectedTab == AppTab.NowPlaying && selectedPlaylistId == null
            Box(Modifier.fillMaxSize().then(if (fullBleed) Modifier else Modifier.padding(padding))) {
                val playlistId = selectedPlaylistId
                if (playlistId != null) {
                    val playlist = playlists.firstOrNull { it.id == playlistId }
                    val byId = remember(songs) { songs.associateBy { it.id } }
                    val playlistSongs = remember(playlist, byId) { playlist?.songIds?.mapNotNull(byId::get).orEmpty() }
                    PlaylistDetailScreen(
                        playlist = playlist,
                        songs = playlistSongs,
                        currentSongId = playback.currentSongId,
                        onBack = viewModel::closePlaylist,
                        onPlay = { playlist?.let(viewModel::playPlaylist) },
                        onShuffle = { playlist?.let { viewModel.playPlaylist(it, shuffle = true) } },
                        // Tapping a song plays this playlist from that song, not the whole library.
                        onSong = { song -> viewModel.playSong(song, playlistSongs) },
                        onMore = { song -> openActions(song, playlist) },
                        onDelete = { playlist?.let(viewModel::deletePlaylist) },
                    )
                } else {
                    AnimatedScreenContainer(selectedTab) { tab ->
                        when (tab) {
                            AppTab.Home -> HomeScreen(
                                songs = songs,
                                playlists = visiblePlaylists,
                                planLabel = if (isPro) "PRO" else "FREE",
                                showProMixesTeaser = !isPro && playlists.any { isProMix(it.id) },
                                onProMixes = { viewModel.showPaywall(ProFeature.MoreMixes) },
                                busy = busy,
                                onScan = viewModel::scanLibrary,
                                onAddFolder = addFolder,
                                onRefreshSmart = viewModel::regenerateSmartPlaylists,
                                onOpenPlaylist = viewModel::openPlaylist,
                                onPlayPlaylist = viewModel::playPlaylist,
                                onSong = viewModel::playSingleThenDaily,
                                onSearch = { viewModel.selectTab(AppTab.Search) },
                                onSettings = { viewModel.open(AppTab.Settings) },
                                onShuffleAll = viewModel::shuffleAll,
                                onPlayArtist = { artistSongs -> artistSongs.firstOrNull()?.let { viewModel.playSong(it, artistSongs) } },
                            )
                            AppTab.Search -> SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                currentSongId = playback.currentSongId,
                                onQuery = viewModel::updateSearch,
                                onSong = { song -> viewModel.playSong(song, searchResults) },
                                onMore = { song -> openActions(song, null) },
                            )
                            AppTab.Library, AppTab.Playlists -> LibraryScreen(
                                songs = songs,
                                playlists = visiblePlaylists,
                                currentSongId = playback.currentSongId,
                                ruleResults = ruleResults,
                                isPro = isPro,
                                onNewRulePlaylist = { viewModel.requirePro(ProFeature.SmartRules) { ruleEditor = null; ruleEditorOpen = true } },
                                onEditRulePlaylist = { ruleEditor = it; ruleEditorOpen = true },
                                onPlayRuleSongs = { list, shuffle -> viewModel.playRuleSongs(list, shuffle) },
                                onSong = { song, source -> viewModel.playSong(song, source) },
                                onFavorite = viewModel::toggleFavorite,
                                onMore = { song -> openActions(song, null) },
                                onOpenPlaylist = viewModel::openPlaylist,
                                onPlayPlaylist = viewModel::playPlaylist,
                                onAddFolder = addFolder,
                                onScan = viewModel::scanLibrary,
                                onShuffleAll = viewModel::shuffleAll,
                            )
                            AppTab.NowPlaying -> NowPlayingScreen(
                                song = currentSong,
                                playbackState = playback,
                                position = viewModel.playbackPosition,
                                lyrics = lyrics,
                                busy = busy,
                                upNext = upNext,
                                sleepTimer = sleepTimer,
                                onPlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                                onSeek = viewModel::seekTo,
                                onShuffle = viewModel::setShuffle,
                                onRepeat = viewModel::cycleRepeatMode,
                                onFavorite = { currentSong?.let(viewModel::toggleFavorite) },
                                onFetchLyrics = { currentSong?.let(viewModel::fetchLyrics) },
                                onLoadLyrics = { currentSong?.let(viewModel::loadCachedLyrics) },
                                onQueuePlay = viewModel::skipToQueuePosition,
                                onQueueRemove = viewModel::removeFromQueue,
                                onSleepTimer = viewModel::setSleepTimer,
                                onAddToPlaylist = { currentSong?.takeIf { !it.isExternal }?.let { playlistPickerSong = it } },
                                onBack = { if (!viewModel.goBack()) viewModel.selectTab(AppTab.Home) },
                            )
                            AppTab.Stats -> StatsScreen(
                                stats = stats,
                                range = statsRange,
                                isPro = isPro,
                                yearPlays = yearPlays,
                                songsById = songsById,
                                onRange = viewModel::setStatsRange,
                                onOpenEncore = viewModel::openEncore,
                                onPlaySong = { song -> viewModel.playSong(song, listOf(song)) },
                            )
                            AppTab.Settings -> SettingsScreen(
                                viewModel = viewModel,
                                onBack = { if (!viewModel.goBack()) viewModel.selectTab(AppTab.Home) },
                            )
                        }
                    }
                }
            }
        }
    }

    actionSong?.let { song ->
        val inPlaylist = actionPlaylist?.takeIf { it.type == PlaylistType.Custom }
        SongActionsSheet(
            song = song,
            onDismiss = { actionSong = null },
            onPlayNext = { viewModel.playNext(song) },
            onAddToQueue = { viewModel.addToQueue(song) },
            onAddToPlaylist = {
                actionSong = null
                playlistPickerSong = song
            },
            onFavorite = { viewModel.toggleFavorite(song) },
            onRemoveFromPlaylist = inPlaylist?.let { playlist -> { viewModel.removeSongFromPlaylist(playlist, song) } },
        )
    }

    paywall?.let { trigger ->
        ProPaywallSheet(
            trigger = trigger,
            pro = proState,
            isPro = isPro,
            onBuy = { activity?.let(viewModel::buyPro) },
            onRestore = viewModel::restorePro,
            onDismiss = viewModel::dismissPaywall,
        )
    }

    encore?.let { recap ->
        EncoreDeck(
            encore = recap,
            songsById = songsById,
            onPlayMix = { viewModel.playEncoreMix(recap) },
            onClose = viewModel::closeEncore,
        )
    }

    if (ruleEditorOpen) {
        val editing = ruleEditor
        SmartPlaylistEditor(
            initial = editing,
            songs = songs,
            onSave = {
                viewModel.saveRulePlaylist(it)
                ruleEditorOpen = false
            },
            onDelete = editing?.let { { viewModel.deleteRulePlaylist(it.id); ruleEditorOpen = false } },
            onDismiss = { ruleEditorOpen = false },
        )
    }

    playlistPickerSong?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = customPlaylists,
            onDismiss = { playlistPickerSong = null },
            onPick = { playlist ->
                viewModel.addSongToPlaylist(playlist, song)
                playlistPickerSong = null
            },
            onCreate = { name ->
                viewModel.createPlaylist(name, song)
                playlistPickerSong = null
            },
        )
    }
}

@Composable
fun AmplyBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.bg),
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
        // Now Playing is one tap away on the player bar, so the fourth tab is Stats, as in the desktop sidebar.
        NavItem(AppTab.Stats, "Stats", Icons.Rounded.BarChart),
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
            val selected = selectedTab == item.tab || (item.tab == AppTab.Library && selectedTab == AppTab.Playlists)
            val fg by animateColorAsState(if (selected) Palette.accent else Palette.textSecondary, label = "nav-fg")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 3.dp)
                    .then(if (selected) Modifier.neuPressed(RadiusMd) else Modifier)
                    .clip(RadiusMd)
                    .clickable(onClickLabel = item.label) { onSelect(item.tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(2.dp))
                Text(item.label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

data class NavItem(val tab: AppTab, val label: String, val icon: ImageVector)

/** Audio access is required; notifications are requested at the same time but are optional. */
fun requestedPermissions(): Array<String> = buildList {
    add(audioPermission())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAudioPermissions(context: Context): Boolean =
    context.checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED

private fun shouldShowAudioRationale(context: Context): Boolean =
    (context as? Activity)?.shouldShowRequestPermissionRationale(audioPermission()) ?: true

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
