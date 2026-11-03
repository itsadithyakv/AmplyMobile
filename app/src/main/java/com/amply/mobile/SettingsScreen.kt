package com.amply.mobile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.amply.mobile.pro.AccentColor
import com.amply.mobile.pro.PRO_EQ_PRESETS
import com.amply.mobile.pro.ProFeature
import com.amply.mobile.report.BugReporter
import com.amply.mobile.report.ReportDraft
import com.amply.mobile.report.ReportKind
import com.amply.mobile.report.ReportResult
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.amply.mobile.ui.theme.NeuDepth
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuPressed
import com.amply.mobile.ui.theme.neuRaised
import com.amply.mobile.ui.theme.neuAccent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amply.mobile.ui.theme.AmplyMuted
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val saved by viewModel.settings.collectAsStateWithLifecycle()
    // Pro-only values show what is actually in effect on this plan; the saved choice comes back with Pro.
    val settings by viewModel.effectiveSettings.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val pro by viewModel.proState.collectAsStateWithLifecycle()
    var equalizerOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmplyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text("Settings", style = MaterialTheme.typography.headlineLarge)
            }
        }
        item { PlanCard(isPro = isPro, price = pro.price, onUpgrade = { viewModel.showPaywall(ProFeature.Themes) }, onRestore = viewModel::restorePro) }
        item {
            SettingsGroup("Appearance") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accent colour", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!isPro) ProLock()
                }
                AccentPicker(selected = settings.accent, onPick = viewModel::setAccent)
                SettingRow(
                    "True black",
                    if (isPro) "Pure black dark mode for OLED screens" else "Pure black dark mode for OLED screens (Pro)",
                    settings.trueBlack,
                    viewModel::setTrueBlack,
                )
            }
        }
        item {
            SettingsGroup("Playback") {
                CrossfadeSetting(
                    seconds = settings.crossfadeSeconds,
                    onSeconds = viewModel::setCrossfadeSeconds,
                    locked = !isPro,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mix tuning", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!isPro) ProLock()
                }
                SettingSlider("Discovery", settings.discoveryIntensity, viewModel::setDiscovery)
                SettingSlider("Randomness", settings.randomnessIntensity, viewModel::setRandomness)
                NeuButton("Fetch all metadata", null, viewModel::fetchAllMetadata)
            }
        }
        item {
            SettingsGroup("About") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("App version", style = MaterialTheme.typography.titleMedium)
                        Text("Amply ${BuildConfig.VERSION_NAME} for Android", color = Palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    PlanBadge(isPro)
                }
                NeuButton("Report a problem", null, { reportOpen = true })
                if (BuildConfig.DEBUG) {
                    SettingRow("Pretend Pro (debug build)", "Unlocks Pro features to test them without Google Play", saved.debugPro, viewModel::setDebugPro)
                }
            }
        }
    }
    if (reportOpen) {
        BugReportDialog(viewModel = viewModel, onDismiss = { reportOpen = false })
    }
    if (equalizerOpen) {
        EqualizerDialog(
            enabled = settings.equalizerEnabled,
            bass = settings.eqBass,
            mid = settings.eqMid,
            treble = settings.eqTreble,
            onEnabled = viewModel::setEqualizerEnabled,
            onBass = { v -> viewModel.requirePro(ProFeature.Equalizer) { viewModel.setEqPreset(null); viewModel.setEqBass(v) } },
            onMid = { v -> viewModel.requirePro(ProFeature.Equalizer) { viewModel.setEqPreset(null); viewModel.setEqMid(v) } },
            onTreble = { v -> viewModel.requirePro(ProFeature.Equalizer) { viewModel.setEqPreset(null); viewModel.setEqTreble(v) } },
            onPreset = { preset ->
                val apply = {
                    if (!settings.equalizerEnabled) viewModel.setEqualizerEnabled(true)
                    viewModel.setEqPreset(preset.name)
                    viewModel.setEqBass(preset.bass)
                    viewModel.setEqMid(preset.mid)
                    viewModel.setEqTreble(preset.treble)
                }
                if (preset.name in PRO_EQ_PRESETS) viewModel.requirePro(ProFeature.Equalizer, apply) else apply()
            },
            onDismiss = { equalizerOpen = false },
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(title, Modifier.padding(start = 6.dp))
        Column(
            Modifier.fillMaxWidth().neuRaised(RadiusLg, NeuDepth.Raised).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SettingRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = Palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        NeuSwitch(checked = checked, onChecked = onChecked, label = title)
    }
}

@Composable
fun SettingSlider(title: String, value: Float, onValue: (Float) -> Unit) {
    // Commit on release: each commit is a database write and regenerates nothing until then anyway.
    var local by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text(title, fontWeight = FontWeight.Bold)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onValue(local) },
            colors = SliderDefaults.colors(
                thumbColor = Palette.accent,
                activeTrackColor = Palette.accent,
                inactiveTrackColor = AmplyMuted.copy(alpha = 0.28f),
            ),
        )
    }
}

@Composable
fun CrossfadeSetting(seconds: Float, onSeconds: (Float) -> Unit, locked: Boolean = false) {
    var local by remember(seconds) { mutableFloatStateOf(seconds) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fade between songs", fontWeight = FontWeight.Black)
                Text(
                    if (local <= 0.1f) "Off - songs play back to back (gapless)" else "Fades out the last ${local.roundToInt()}s of a song and fades the next one in",
                    color = AmplyMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (locked) ProLock(Modifier.padding(end = 6.dp))
            Text("${local.roundToInt()}s", color = Palette.accent, fontWeight = FontWeight.Black)
        }
        Slider(
            value = local.coerceIn(0f, 12f),
            onValueChange = { local = it },
            onValueChangeFinished = { onSeconds(local) },
            valueRange = 0f..12f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = Palette.accent,
                activeTrackColor = Palette.accent,
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
            .clip(RadiusMd)
            .clickable(onClickLabel = "Open equalizer", onClick = onOpen)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Equalizer", style = MaterialTheme.typography.titleMedium)
            Text(
                if (enabled) "Bass ${eqDbLabel(bass)}  -  Mid ${eqDbLabel(mid)}  -  High ${eqDbLabel(treble)}" else "Off - tap to tune bass, mids, and treble",
                color = AmplyMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(if (enabled) "On" else "Off", color = if (enabled) Palette.accent else Palette.textMuted, style = MaterialTheme.typography.labelLarge)
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
    onPreset: (EqPreset) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Palette.bg),
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
                    onPreset = onPreset,
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
            .then(if (enabled) Modifier.neuAccent(RoundedCornerShape(18.dp)) else Modifier.neuPressed(RoundedCornerShape(18.dp)))
            .clickable(onClick = onToggle)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (enabled) "ON" else "OFF", color = if (enabled) Palette.onAccent else Palette.textSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (enabled) Palette.onAccent else Palette.textSecondary))
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
                .neuPressed(RoundedCornerShape(28.dp), small = false)
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
            val muted = Palette.textMuted
            val knobRing = Palette.bg
            val accent = Palette.accent
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
                        color = if (fraction == 0.5f) muted.copy(alpha = 0.34f) else muted.copy(alpha = 0.14f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = if (fraction == 0.5f) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }

                xs.forEach { x ->
                    drawLine(
                        color = muted.copy(alpha = 0.12f),
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
                    color = if (enabled) accent else muted.copy(alpha = 0.40f),
                    style = Stroke(width = 5.dp.toPx()),
                )
                points.forEach { point ->
                    drawCircle(knobRing, radius = 19.dp.toPx(), center = point)
                    drawCircle(if (enabled) accent else muted, radius = 13.dp.toPx(), center = point)
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
            items(presets, key = { it.name }) { preset ->
                val selected = current == preset.name
                Box(
                    modifier = Modifier
                        .then(if (selected) Modifier.neuPressed(CircleShape) else Modifier.neuRaised(CircleShape))
                        .clip(CircleShape)
                        .clickable { onPreset(preset) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        preset.name,
                        color = if (selected) Palette.accent else Palette.textSecondary,
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
            // Pro presets, as on desktop.
            EqPreset("Warm", 0.66f, 0.58f, 0.40f),
            EqPreset("Club", 0.86f, 0.44f, 0.70f),
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

/**
 * Desktop switch: a pressed track with a raised knob. The thin outline keeps "off" readable, as
 * the desktop 1.8.1 notes call out.
 */
@Composable
fun NeuSwitch(checked: Boolean, onChecked: (Boolean) -> Unit, label: String) {
    val p = Palette
    val trackShape = CircleShape
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 30.dp)
            .neuPressed(trackShape)
            .border(1.dp, p.edge, trackShape)
            .clip(trackShape)
            .background(if (checked) p.accent.copy(alpha = 0.9f) else Color.Transparent)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChecked)
            .semantics { contentDescription = label }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).neuRaised(CircleShape, color = if (checked) Color.White else p.bg))
    }
}

/** Plan card at the top of Settings, like the desktop's. */
@Composable
private fun PlanCard(isPro: Boolean, price: String?, onUpgrade: () -> Unit, onRestore: () -> Unit) {
    val p = Palette
    Row(
        Modifier.fillMaxWidth().neuRaised(RadiusLg, NeuDepth.Raised).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your plan", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                PlanBadge(isPro)
            }
            Text(
                if (isPro) "Every Amply Pro feature is on. Thank you!" else "Amply is free. Pro is a one-time purchase${price?.let { " of $it" } ?: ""}.",
                color = p.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!isPro) {
            Column(horizontalAlignment = Alignment.End) {
                NeuButton("Get Pro", null, onUpgrade, accent = true)
                TextButton(onClick = onRestore) { Text("Restore", color = p.textSecondary, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun AccentPicker(selected: String, onPick: (String) -> Unit) {
    val dark = Palette.isDark
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
        items(AccentColor.entries) { accent ->
            val chosen = accent.key == selected
            Box(
                Modifier
                    .size(40.dp)
                    .then(if (chosen) Modifier.neuPressed(CircleShape) else Modifier.neuRaised(CircleShape))
                    .clip(CircleShape)
                    .clickable(onClickLabel = accent.label) { onPick(accent.key) }
                    .padding(7.dp)
                    .background(Color(if (dark) accent.dark else accent.light), CircleShape),
            )
        }
    }
}

/** Settings > About > Report a problem. Shows exactly what will be sent before sending. */
@Composable
private fun BugReportDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val p = Palette
    val scope = rememberCoroutineScope()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(ReportKind.Bug) }
    var summary by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var includeDetails by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ReportResult?>(null) }
    val draft = ReportDraft(kind, summary, details, steps, email, includeDetails)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.bg,
        title = { Text(if (result == ReportResult.Sent) "Thanks for the report" else "Report a problem") },
        text = {
            if (result == ReportResult.Sent) {
                Text("It's on its way to the developer.${if (email.isNotBlank()) " You may get a reply at $email." else ""}", color = p.textSecondary)
            } else {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportKind.entries.forEach { AmplyChip(it.subject, kind == it) { kind = it } }
                    }
                    NeuField(summary, { summary = it.take(120) }, "Short summary")
                    NeuField(details, { details = it.take(4000) }, "What happened? What did you expect?", singleLine = false, minHeight = 96.dp)
                    NeuField(steps, { steps = it.take(2000) }, "Steps to make it happen (optional)", singleLine = false)
                    NeuField(email, { email = it.take(254) }, "Your email, if you'd like a reply (optional)")
                    SettingRow("Include device details", "Shown below; nothing else is sent", includeDetails) { includeDetails = it }
                    if (includeDetails) {
                        Text(
                            BugReporter.deviceDescription(songs.size, if (isPro) "Pro" else "Free"),
                            color = p.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    result?.let {
                        Text(
                            when (it) {
                                ReportResult.RateLimited -> "You've sent a report recently. Try again in a minute."
                                ReportResult.Offline -> "You're offline. Connect and try again."
                                ReportResult.Invalid -> "Add a short summary and a few words about what happened."
                                else -> "The report couldn't be sent. Try again later."
                            },
                            color = p.danger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (result == ReportResult.Sent) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(
                    enabled = !sending && BugReporter.isValid(draft),
                    onClick = {
                        sending = true
                        scope.launch {
                            result = viewModel.sendReport(draft)
                            sending = false
                        }
                    },
                ) { Text(if (sending) "Sending..." else "Send") }
            }
        },
        dismissButton = { if (result != ReportResult.Sent) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
