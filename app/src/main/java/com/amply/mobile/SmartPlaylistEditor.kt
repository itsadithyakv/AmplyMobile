package com.amply.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amply.mobile.domain.Song
import com.amply.mobile.playlist.Rule
import com.amply.mobile.playlist.RuleField
import com.amply.mobile.playlist.RuleKind
import com.amply.mobile.playlist.RuleOp
import com.amply.mobile.playlist.RulePlaylist
import com.amply.mobile.playlist.RuleSort
import com.amply.mobile.playlist.evaluateRules
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuPressed
import com.amply.mobile.ui.theme.neuRaised

/** Build or edit a smart playlist. The preview count updates as rules change. */
@Composable
fun SmartPlaylistEditor(
    initial: RulePlaylist?,
    songs: List<Song>,
    onSave: (RulePlaylist) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val p = Palette
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var matchAll by remember { mutableStateOf(initial?.matchAll ?: true) }
    var sort by remember { mutableStateOf(initial?.sort ?: RuleSort.MostPlayed) }
    var limit by remember { mutableStateOf(initial?.limit?.takeIf { it > 0 }?.toString() ?: "") }
    val rules = remember {
        mutableStateListOf<Rule>().apply { addAll(initial?.rules ?: listOf(Rule(RuleField.Genre, RuleOp.Contains, ""))) }
    }
    val draft = RulePlaylist(
        id = initial?.id ?: RulePlaylist(name = "", rules = emptyList()).id,
        name = name,
        rules = rules.filter { it.value.isNotBlank() || it.field.kind == RuleKind.Flag },
        matchAll = matchAll,
        limit = limit.toIntOrNull() ?: 0,
        sort = sort,
    )
    val preview = remember(draft, songs) { evaluateRules(draft, songs).size }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.bg,
        title = { Text(if (initial == null) "New smart playlist" else "Edit smart playlist") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuField(name, { name = it }, "Name, e.g. Rock on repeat")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Match", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
                    AmplyChip("all rules", matchAll) { matchAll = true }
                    AmplyChip("any rule", !matchAll) { matchAll = false }
                }
                rules.forEachIndexed { index, rule ->
                    RuleRow(
                        rule = rule,
                        onChange = { rules[index] = it },
                        onRemove = if (rules.size > 1) ({ rules.removeAt(index) }) else null,
                    )
                }
                Row(
                    Modifier.clip(CircleShape).clickable { rules.add(Rule(RuleField.Plays, RuleOp.AtLeast, "")) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = p.accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Add rule", color = p.accent, style = MaterialTheme.typography.labelLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort by", color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                    Picker(sort.label, RuleSort.entries.map { it.label }) { sort = RuleSort.entries[it] }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Limit", color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                    NeuField(limit, { limit = it.filter(Char::isDigit).take(4) }, "No limit", number = true, modifier = Modifier.width(120.dp))
                }
                Text("$preview songs match right now", color = p.accent, style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = name.isNotBlank() && draft.rules.isNotEmpty()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = p.danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun RuleRow(rule: Rule, onChange: (Rule) -> Unit, onRemove: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().neuRaised(RadiusMd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Picker(rule.field.label, RuleField.entries.map { it.label }) { index ->
                    val field = RuleField.entries[index]
                    onChange(Rule(field, RuleOp.forKind(field.kind).first(), if (field.kind == RuleKind.Flag) "" else ""))
                }
            }
            if (onRemove != null) NeuIconButton(Icons.Rounded.Close, "Remove rule", onRemove, size = 32.dp, flat = true, tint = Palette.textMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val ops = RuleOp.forKind(rule.field.kind)
            if (ops.size > 1) Picker(rule.op.label, ops.map { it.label }) { onChange(rule.copy(op = ops[it])) }
            when (rule.field.kind) {
                RuleKind.Flag -> Unit
                RuleKind.Text -> NeuField(rule.value, { onChange(rule.copy(value = it)) }, "Value", modifier = Modifier.weight(1f))
                RuleKind.Number, RuleKind.Days -> NeuField(rule.value, { onChange(rule.copy(value = it.filter { c -> c.isDigit() || c == '.' })) }, if (rule.field.kind == RuleKind.Days) "Days" else "Number", number = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Picker(current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.neuRaised(CircleShape).clip(CircleShape).clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.textMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.bg) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(index) })
            }
        }
    }
}

/** Pressed-in text field in the desktop style. */
@Composable
fun NeuField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    number: Boolean = false,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val p = Palette
    Box(modifier.heightIn(min = minHeight).neuPressed(RadiusMd).padding(horizontal = 14.dp, vertical = 11.dp)) {
        if (value.isEmpty()) Text(placeholder, color = p.textMuted, style = MaterialTheme.typography.bodyMedium)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = p.textPrimary),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(0.dp))
}
