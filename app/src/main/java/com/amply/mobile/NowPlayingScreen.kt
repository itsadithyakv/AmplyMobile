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
fun NowPlayingScreen(
    song: Song?,
    playback: com.amply.mobile.domain.AmplyPlaybackState,
    lyrics: CachedLyrics?,
    busy: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onRepeat: () -> Unit,
    onRepeatMode: (RepeatMode) -> Unit,
    onFavorite: () -> Unit,
    onFetchLyrics: () -> Unit,
    onLoadLyrics: () -> Unit,
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
                modifier = Modifier.fillMaxSize().blur(36.dp).graphicsLayer(alpha = 0.22f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.34f), AmplyBg, Color(0xFF050505)))),
        )
        if (lyricsOpen) {
            FullLyricsView(
                song = song,
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
            OpeningContainer {
                LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AmplyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                        Spacer(Modifier.weight(1f))
                        Text("Now Playing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        AmplyIconButton(if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", onFavorite)
                    }
                }
                item {
                    VinylDisk(
                        song = song,
                        bitmap = bitmap,
                        playback = playback,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxWidth(0.84f).aspectRatio(1f),
                    )
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(song.artist, color = AmplyMuted, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    PlayerProgress(playback = playback, onSeek = onSeek)
                }
                item {
                    PlaybackModeOptions(
                        shuffle = playback.shuffle,
                        repeatMode = playback.repeatMode,
                        onOrder = { onShuffle(false) },
                        onShuffle = { onShuffle(true) },
                        onLoopSong = {
                            onRepeatMode(if (playback.repeatMode == RepeatMode.One) RepeatMode.Off else RepeatMode.One)
                        },
                    )
                }
                item {
                    PlayerControls(
                        isPlaying = playback.isPlaying,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                    )
                }
                item {
                    LyricsPreview(
                        current = currentLyric,
                        busy = busy,
                        onFetch = onFetchLyrics,
                        onOpen = { lyricsOpen = true },
                    )
                }
                }
            }
        }
    }
}

@Composable
fun OpeningContainer(content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open = true }
    val scale by animateFloatAsState(if (open) 1f else 0.94f, animationSpec = tween(420), label = "open-scale")
    val offset by animateFloatAsState(if (open) 0f else 34f, animationSpec = tween(420), label = "open-offset")
    val alpha by animateFloatAsState(if (open) 1f else 0.65f, animationSpec = tween(420), label = "open-alpha")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offset
                this.alpha = alpha
            },
    ) {
        content()
    }
}

@Composable
fun VinylDisk(
    song: Song,
    bitmap: ImageBitmap?,
    playback: com.amply.mobile.domain.AmplyPlaybackState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = remember(song.id) { Animatable(0f) }
    var manualRotation by remember(song.id) { mutableFloatStateOf(0f) }
    var lastDragAngle by remember(song.id) { mutableStateOf<Float?>(null) }
    var scrubPositionMs by remember(song.id) { mutableStateOf<Long?>(null) }

    LaunchedEffect(playback.isPlaying, song.id) {
        if (playback.isPlaying) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 16000, easing = LinearEasing),
                )
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer(rotationZ = rotation.value + manualRotation)
            .pointerInput(song.id, playback.durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        lastDragAngle = angleForOffset(offset, Offset(size.width / 2f, size.height / 2f))
                        scrubPositionMs = playback.positionMs
                    },
                    onDragEnd = {
                        lastDragAngle = null
                        scrubPositionMs = null
                    },
                    onDragCancel = {
                        lastDragAngle = null
                        scrubPositionMs = null
                    },
                    onDrag = { change, _ ->
                        val duration = playback.durationMs
                        if (duration > 0L) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val currentAngle = angleForOffset(change.position, center)
                            val previousAngle = lastDragAngle ?: currentAngle
                            val delta = shortestAngleDelta(currentAngle, previousAngle)
                            lastDragAngle = currentAngle
                            manualRotation += delta
                            val base = scrubPositionMs ?: playback.positionMs
                            val nextPosition = (base + (delta / 360f * 20_000f).toLong()).coerceIn(0L, duration)
                            scrubPositionMs = nextPosition
                            onSeek(nextPosition)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF111111), radius = radius, center = center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2C2C2C), Color(0xFF090909), Color(0xFF202020)),
                    center = center,
                    radius = radius,
                ),
                radius = radius * 0.98f,
                center = center,
            )
            for (step in 1..10) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.035f),
                    radius = radius * (0.30f + step * 0.06f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawCircle(
                color = AmplyOrange.copy(alpha = 0.18f),
                radius = radius * 0.96f,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(Color(0xFF050505), radius = radius * 0.09f, center = center)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.44f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(AmplyPanel),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                AmplyLogo(Modifier.fillMaxSize().padding(28.dp))
            }
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)))))
        }
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF080808)))
    }
}

fun angleForOffset(position: Offset, center: Offset): Float {
    val degrees = atan2(position.y - center.y, position.x - center.x) * 180f / PI.toFloat()
    return (degrees + 360f) % 360f
}

fun shortestAngleDelta(current: Float, previous: Float): Float {
    var delta = current - previous
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmplyIconButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
        Spacer(Modifier.width(26.dp))
        val scale by animateFloatAsState(if (isPlaying) 1.04f else 1f, label = "play-scale")
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(CircleShape)
                .background(AmplyOrange),
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = Color(0xFF160B02),
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.width(26.dp))
        AmplyIconButton(Icons.Rounded.SkipNext, "Next", onNext)
    }
}

@Composable
fun PlaybackModeOptions(
    shuffle: Boolean,
    repeatMode: RepeatMode,
    onOrder: () -> Unit,
    onShuffle: () -> Unit,
    onLoopSong: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(AmplyGlass.copy(alpha = 0.48f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModePill(
            label = "In order",
            icon = Icons.Rounded.Repeat,
            selected = !shuffle,
            modifier = Modifier.weight(1f),
            onClick = onOrder,
        )
        ModePill(
            label = "Shuffle",
            icon = Icons.Rounded.Shuffle,
            selected = shuffle,
            modifier = Modifier.weight(1f),
            onClick = onShuffle,
        )
        ModePill(
            label = "Loop song",
            icon = Icons.Rounded.Repeat,
            selected = repeatMode == RepeatMode.One,
            modifier = Modifier.weight(1f),
            onClick = onLoopSong,
        )
    }
}

@Composable
fun ModePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) AmplyOrange else Color.Transparent, label = "mode-pill-bg")
    val fg by animateColorAsState(if (selected) Color(0xFF160B02) else MaterialTheme.colorScheme.onSurface, label = "mode-pill-fg")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PlayerProgress(playback: com.amply.mobile.domain.AmplyPlaybackState, onSeek: (Long) -> Unit) {
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(Modifier.fillMaxWidth()) {
        MinimalProgressBar(
            progress = progress,
            onSeekFraction = { fraction ->
                val duration = playback.durationMs.takeIf { it > 0L } ?: return@MinimalProgressBar
                onSeek((duration * fraction).toLong())
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(playback.positionMs), color = AmplyMuted, style = MaterialTheme.typography.labelSmall)
            Text(formatTime(playback.durationMs), color = AmplyMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun MinimalProgressBar(progress: Float, onSeekFraction: (Float) -> Unit) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val trackHeight = 3.dp.toPx()
        val y = size.height / 2f
        drawRoundRect(
            color = AmplyMuted.copy(alpha = 0.28f),
            topLeft = Offset(0f, y - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight),
        )
        drawRoundRect(
            color = AmplyOrange,
            topLeft = Offset(0f, y - trackHeight / 2f),
            size = Size(size.width * progress.coerceIn(0f, 1f), trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight),
        )
        drawCircle(
            color = AmplyOrange,
            radius = 4.dp.toPx(),
            center = Offset(size.width * progress.coerceIn(0f, 1f), y),
        )
    }
}

@Composable
fun LyricsPreview(current: List<LyricPreviewLine>, busy: Boolean, onFetch: () -> Unit, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AmplyGlass.copy(alpha = 0.45f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth(0.72f), color = AmplyOrange, trackColor = AmplyPanel)
        } else if (current.isEmpty()) {
            OutlinedButton(onClick = onFetch, border = BorderStroke(1.dp, AmplyGlass)) {
                Text("Find lyrics")
            }
        } else {
            current.forEach { line ->
                val scale by animateFloatAsState(if (line.active) 1f else 0.94f, animationSpec = tween(320), label = "preview-line-scale")
                val alpha by animateFloatAsState(if (line.active) 1f else 0.44f, animationSpec = tween(320), label = "preview-line-alpha")
                Text(
                    line.text,
                    color = if (line.active) MaterialTheme.colorScheme.onSurface else AmplyMuted,
                    style = if (line.active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                )
            }
        }
    }
}

@Composable
fun FullLyricsView(
    song: Song,
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
    val activeIndex = parsed.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }.let { if (it >= 0) it else 0 }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex, parsed.size) {
        if (parsed.isNotEmpty()) {
            listState.animateScrollToItem((activeIndex - 4).coerceAtLeast(0))
        }
    }
    OpeningContainer {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(AmplyGlass).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmplyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onClose)
                Spacer(Modifier.width(10.dp))
                ArtworkBox(song.artworkUri, 54.dp, shape = RoundedCornerShape(18.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
                }
                AmplyIconButton(Icons.Rounded.Refresh, "Refresh lyrics", onClick = onFetch)
            }

            if (parsed.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = onFetch) { Text("Find lyrics") }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 92.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(parsed.size) { index ->
                        val line = parsed[index].text
                        if (line.isNotBlank()) {
                            val distance = abs(index - activeIndex)
                            val targetAlpha = when {
                                distance == 0 -> 1f
                                distance == 1 -> 0.58f
                                distance == 2 -> 0.34f
                                else -> 0.18f
                            }
                            val targetScale = when {
                                distance == 0 -> 1f
                                distance == 1 -> 0.94f
                                else -> 0.90f
                            }
                            val targetOffset = when {
                                index < activeIndex -> -8f
                                index > activeIndex -> 8f
                                else -> 0f
                            }
                            val alpha by animateFloatAsState(targetAlpha, animationSpec = tween(360), label = "lyric-alpha")
                            val scale by animateFloatAsState(targetScale, animationSpec = tween(360), label = "lyric-scale")
                            val offset by animateFloatAsState(targetOffset, animationSpec = tween(360), label = "lyric-offset")
                            Text(
                                line,
                                color = if (index == activeIndex) MaterialTheme.colorScheme.onSurface else AmplyMuted,
                                style = if (index == activeIndex) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                                fontWeight = if (index == activeIndex) FontWeight.Black else FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        this.alpha = alpha
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = offset
                                    },
                            )
                        }
                    }
                }
            }
            PlayerProgress(playback = playback, onSeek = onSeek)
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmplyIconButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
                AmplyIconButton(if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play", onPlayPause, highlight = true)
                AmplyIconButton(Icons.Rounded.SkipNext, "Next", onNext)
            }
        }
    }
}

data class LyricPreviewLine(val text: String, val active: Boolean)

fun currentLyricWindow(raw: String, positionMs: Long): List<LyricPreviewLine> {
    val parsed = parseLrc(raw).filter { it.text.isNotBlank() }
    if (parsed.isEmpty()) return emptyList()
    val active = parsed.indexOfLast { it.timeMs != null && it.timeMs <= positionMs }
        .takeIf { it >= 0 }
        ?: 0
    return listOf(active - 1, active, active + 1).mapNotNull { index ->
        parsed.getOrNull(index)?.let { LyricPreviewLine(it.text, index == active) }
    }
}

