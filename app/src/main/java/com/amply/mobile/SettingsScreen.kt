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
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var equalizerOpen by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmplyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
        }
        item {
            SettingsGroup("Playback") {
                CrossfadeSetting(
                    seconds = settings.crossfadeSeconds,
                    onSeconds = viewModel::setCrossfadeSeconds,
                )
                EqualizerSettingRow(
                    enabled = settings.equalizerEnabled,
                    bass = settings.eqBass,
                    mid = settings.eqMid,
                    treble = settings.eqTreble,
                    onOpen = { equalizerOpen = true },
                )
            }
        }
        item {
            SettingsGroup("Library intelligence") {
                SettingRow("Pause metadata fetching", "Stop online artist and genre lookups", settings.metadataFetchPaused, viewModel::setMetadataPaused)
                SettingSlider("Discovery", settings.discoveryIntensity, viewModel::setDiscovery)
                SettingSlider("Randomness", settings.randomnessIntensity, viewModel::setRandomness)
                Button(onClick = viewModel::fetchAllMetadata, colors = ButtonDefaults.buttonColors(containerColor = AmplyOrange)) {
                    Text("Fetch all metadata")
                }
            }
        }
    }
    if (equalizerOpen) {
        EqualizerDialog(
            enabled = settings.equalizerEnabled,
            bass = settings.eqBass,
            mid = settings.eqMid,
            treble = settings.eqTreble,
            onEnabled = viewModel::setEqualizerEnabled,
            onBass = viewModel::setEqBass,
            onMid = viewModel::setEqMid,
            onTreble = viewModel::setEqTreble,
            onDismiss = { equalizerOpen = false },
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AmplyGlass), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
fun SettingRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
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
fun SettingSlider(title: String, value: Float, onValue: (Float) -> Unit) {
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
fun CrossfadeSetting(seconds: Float, onSeconds: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Crossfade / gapless", fontWeight = FontWeight.Black)
                Text(
                    if (seconds <= 0.1f) "0s - normal gapless playback" else "${seconds.roundToInt()}s transition between songs",
                    color = AmplyMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${seconds.roundToInt()}s", color = AmplyOrange, fontWeight = FontWeight.Black)
        }
        Slider(
            value = seconds.coerceIn(0f, 12f),
            onValueChange = onSeconds,
            valueRange = 0f..12f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = AmplyOrange,
                activeTrackColor = AmplyOrange,
                inactiveTrackColor = AmplyMuted.copy(alpha = 0.28f),
            ),
        )
    }
}

@Composable
fun EqualizerSettingRow(
    enabled: Boolean,
    bass: Float,
    mid: Float,
    treble: Float,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AmplyPanel)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Equalizer", fontWeight = FontWeight.Black)
            Text(
                if (enabled) "Bass ${eqDbLabel(bass)}  -  Mid ${eqDbLabel(mid)}  -  High ${eqDbLabel(treble)}" else "Off - tap to tune bass, mids, and treble",
                color = AmplyMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.size(width = 58.dp, height = 34.dp).clip(RoundedCornerShape(18.dp)).background(if (enabled) AmplyOrange else Color(0xFF3A3A3A)),
            contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(4.dp).size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.94f)))
        }
    }
}

@Composable
fun EqualizerDialog(
    enabled: Boolean,
    bass: Float,
    mid: Float,
    treble: Float,
    onEnabled: (Boolean) -> Unit,
    onBass: (Float) -> Unit,
    onMid: (Float) -> Unit,
    onTreble: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
            shape = RoundedCornerShape(34.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AmplyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close equalizer", onClick = onDismiss)
                    Spacer(Modifier.width(12.dp))
                    Text("Equalizer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    EqPowerSwitch(enabled = enabled, onToggle = { onEnabled(!enabled) })
                }
                EditableEqGraph(
                    enabled = enabled,
                    bass = bass,
                    mid = mid,
                    treble = treble,
                    onBass = onBass,
                    onMid = onMid,
                    onTreble = onTreble,
                )
                EqPresetSelector(
                    presets = EqPreset.defaults,
                    current = EqPreset.match(bass, mid, treble),
                    onPreset = { preset ->
                        if (!enabled) onEnabled(true)
                        onBass(preset.bass)
                        onMid(preset.mid)
                        onTreble(preset.treble)
                    },
                )
            }
        }
    }
}

@Composable
fun EqPowerSwitch(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) AmplyOrange else AmplyPanel)
            .clickable(onClick = onToggle)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (enabled) "ON" else "OFF", color = if (enabled) Color(0xFF160B02) else AmplyMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (enabled) Color(0xFF160B02) else AmplyMuted))
    }
}

@Composable
fun EditableEqGraph(
    enabled: Boolean,
    bass: Float,
    mid: Float,
    treble: Float,
    onBass: (Float) -> Unit,
    onMid: (Float) -> Unit,
    onTreble: (Float) -> Unit,
) {
    val low by animateFloatAsState(bass.coerceIn(0f, 1f), animationSpec = tween(160), label = "eq-low")
    val middle by animateFloatAsState(mid.coerceIn(0f, 1f), animationSpec = tween(160), label = "eq-mid")
    val high by animateFloatAsState(treble.coerceIn(0f, 1f), animationSpec = tween(160), label = "eq-high")
    val values = listOf(low, middle, high)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(AmplyPanel)
                .pointerInput(enabled) {
                    detectTapGestures { offset ->
                        if (enabled) updateEqPoint(offset, size.width.toFloat(), size.height.toFloat(), onBass, onMid, onTreble)
                    }
                }
                .pointerInput(enabled) {
                    detectDragGestures { change, _ ->
                        if (enabled) updateEqPoint(change.position, size.width.toFloat(), size.height.toFloat(), onBass, onMid, onTreble)
                    }
                }
                .padding(14.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val left = 26.dp.toPx()
                val right = size.width - 26.dp.toPx()
                val top = 26.dp.toPx()
                val bottom = size.height - 38.dp.toPx()
                val zeroY = bottom - (bottom - top) * 0.5f
                val xs = listOf(left, (left + right) / 2f, right)
                val points = values.mapIndexed { index, value -> Offset(xs[index], bottom - (bottom - top) * value) }

                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                    val y = bottom - (bottom - top) * fraction
                    drawLine(
                        color = if (fraction == 0.5f) AmplyMuted.copy(alpha = 0.34f) else AmplyMuted.copy(alpha = 0.14f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = if (fraction == 0.5f) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }

                xs.forEach { x ->
                    drawLine(
                        color = AmplyMuted.copy(alpha = 0.12f),
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    cubicTo(
                        (points[0].x + points[1].x) / 2f,
                        points[0].y,
                        (points[0].x + points[1].x) / 2f,
                        points[1].y,
                        points[1].x,
                        points[1].y,
                    )
                    cubicTo(
                        (points[1].x + points[2].x) / 2f,
                        points[1].y,
                        (points[1].x + points[2].x) / 2f,
                        points[2].y,
                        points[2].x,
                        points[2].y,
                    )
                }
                drawPath(
                    path = path,
                    color = if (enabled) AmplyOrange else AmplyMuted.copy(alpha = 0.40f),
                    style = Stroke(width = 5.dp.toPx()),
                )
                points.forEach { point ->
                    drawCircle(Color(0xFF0C0C0C), radius = 19.dp.toPx(), center = point)
                    drawCircle(if (enabled) AmplyOrange else AmplyMuted, radius = 13.dp.toPx(), center = point)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EqGraphLabel("Low", bass)
            EqGraphLabel("Mid", mid)
            EqGraphLabel("High", treble)
        }
    }
}

@Composable
fun EqGraphLabel(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontWeight = FontWeight.Black)
        Text(eqDbLabel(value), color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
    }
}

fun updateEqPoint(
    position: Offset,
    width: Float,
    height: Float,
    onBass: (Float) -> Unit,
    onMid: (Float) -> Unit,
    onTreble: (Float) -> Unit,
) {
    val band = when {
        position.x < width / 3f -> 0
        position.x < width * 2f / 3f -> 1
        else -> 2
    }
    val value = (1f - (position.y / height)).coerceIn(0f, 1f)
    when (band) {
        0 -> onBass(value)
        1 -> onMid(value)
        else -> onTreble(value)
    }
}

@Composable
fun EqPresetSelector(presets: List<EqPreset>, current: String?, onPreset: (EqPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Templates", color = AmplyMuted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(presets, key = { it.name }) { preset ->
                val selected = current == preset.name
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) AmplyOrange else AmplyPanel)
                        .clickable { onPreset(preset) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        preset.name,
                        color = if (selected) Color(0xFF160B02) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

data class EqPreset(val name: String, val bass: Float, val mid: Float, val treble: Float) {
    companion object {
        val defaults = listOf(
            EqPreset("Flat", 0.50f, 0.50f, 0.50f),
            EqPreset("Bass Boost", 0.82f, 0.56f, 0.48f),
            EqPreset("Vocal", 0.45f, 0.76f, 0.58f),
            EqPreset("Rock", 0.70f, 0.48f, 0.72f),
            EqPreset("Late Night", 0.38f, 0.45f, 0.42f),
        )

        fun match(bass: Float, mid: Float, treble: Float): String? =
            defaults.firstOrNull { preset ->
                abs(preset.bass - bass) < 0.02f &&
                    abs(preset.mid - mid) < 0.02f &&
                    abs(preset.treble - treble) < 0.02f
            }?.name
    }
}

fun eqDbLabel(value: Float): String {
    val db = ((value.coerceIn(0f, 1f) - 0.5f) * 30f).roundToInt()
    return when {
        db > 0 -> "+${db}db"
        else -> "${db}db"
    }
}

