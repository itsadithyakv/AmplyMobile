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
fun MiniPlayer(song: Song, isPlaying: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(Color(0xFF111111))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(song.artworkUri, 52.dp, shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Favorite", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = onPlayPause, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = Color(0xFF101010),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
fun SongListRow(song: Song, onClick: () -> Unit, onPlay: () -> Unit, onFavorite: (() -> Unit)? = null) {
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
            Text("${song.artist}  -  ${song.effectiveGenre}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
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
fun PlaylistListRow(playlist: Playlist, onClick: () -> Unit, onPlay: () -> Unit) {
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
fun GenreRow(genre: String, count: Int, onClick: () -> Unit) {
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
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
}

@Composable
fun EmptyLibraryActions(onAddFolder: () -> Unit, onScan: () -> Unit) {
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
fun OverflowActions(onAddFolder: () -> Unit, onScan: () -> Unit, onRefreshSmart: () -> Unit, onSettings: () -> Unit) {
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
fun LibraryActions(onAddFolder: () -> Unit, onScan: () -> Unit) {
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
fun AmplyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) AmplyOrange else AmplyCard, label = "chip-bg")
    val fg by animateColorAsState(if (selected) Color(0xFF160B02) else MaterialTheme.colorScheme.onSurface, label = "chip-fg")
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
fun AmplyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
    active: Boolean = false,
) {
    val bg by animateColorAsState(
        when {
            highlight || active -> AmplyOrange
            else -> AmplyCard
        },
        label = "icon-bg",
    )
    val fg by animateColorAsState(if (highlight || active) Color(0xFF160B02) else MaterialTheme.colorScheme.onSurface, label = "icon-fg")
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(bg),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg)
    }
}

@Composable
fun ArtworkBox(
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
fun AmplyLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_amply),
        contentDescription = "Amply",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
fun rememberArtwork(uri: String?): androidx.compose.runtime.State<ImageBitmap?> {
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

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

