package com.amply.mobile

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.Song
import com.amply.mobile.playback.PlaybackCommands
import com.amply.mobile.playback.SleepTimerState
import com.amply.mobile.ui.theme.AmplyMuted
import com.amply.mobile.ui.theme.AmplyOrange
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuPressed
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.delay

/** What the user can do with one song, from a long press or the row's menu button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFavorite: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.bg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtworkBox(song.artworkUri, 52.dp, shape = RoundedCornerShape(16.dp), audioUri = song.contentUri)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = AmplyMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SheetAction(Icons.Rounded.SkipNext, "Play next") { onPlayNext(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { onAddToQueue(); onDismiss() }
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { onAddToPlaylist() }
            SheetAction(
                if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (song.favorite) "Remove from liked songs" else "Add to liked songs",
            ) { onFavorite(); onDismiss() }
            if (onRemoveFromPlaylist != null) {
                SheetAction(Icons.Rounded.PlaylistRemove, "Remove from this playlist") { onRemoveFromPlaylist(); onDismiss() }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.accent)
        Spacer(Modifier.width(18.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

/** Pick one of the user's playlists or make a new one with the song in it. */
@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.bg,
        title = { Text("Add \"${song.title}\" to", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (playlists.isEmpty()) {
                    Text("You have no playlists yet. Name one to create it.", color = AmplyMuted)
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onPick(playlist) }
                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(playlist.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${playlist.songIds.size}", color = AmplyMuted)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New playlist") },
                    trailingIcon = {
                        IconButton(onClick = { onCreate(newName) }, enabled = newName.isNotBlank()) {
                            Icon(Icons.Rounded.Add, contentDescription = "Create playlist")
                        }
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The rest of the queue in play order; tap to jump, X to drop a song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    current: Song?,
    upNext: List<QueueEntry>,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.bg) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, bottom = 32.dp),
        ) {
            if (current != null) {
                item {
                    Text("Now playing", color = AmplyMuted, style = MaterialTheme.typography.labelLarge)
                    QueueRow(current, highlighted = true, onClick = null, onRemove = null)
                    Spacer(Modifier.size(8.dp))
                }
            }
            item {
                Text(
                    if (upNext.isEmpty()) "Nothing queued after this song" else "Up next - ${upNext.size} songs",
                    color = AmplyMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            items(upNext, key = { "${it.position}-${it.song.id}" }) { entry ->
                QueueRow(entry.song, highlighted = false, onClick = { onPlay(entry.position) }, onRemove = { onRemove(entry.position) })
            }
        }
    }
}

@Composable
private fun QueueRow(song: Song, highlighted: Boolean, onClick: (() -> Unit)?, onRemove: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(song.artworkUri, 46.dp, shape = RoundedCornerShape(14.dp), audioUri = song.contentUri.ifBlank { null })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) Palette.accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, color = AmplyMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${song.title} from queue") }
        }
    }
}

/** Countdown or end-of-song sleep timer; the service fades out and pauses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(state: SleepTimerState, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.bg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = Palette.accent)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Sleep timer", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(sleepTimerLabel(state) ?: "Off", color = AmplyMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            listOf(5, 15, 30, 45, 60, 90).forEach { minutes ->
                SleepOption("$minutes minutes") { onSet(minutes); onDismiss() }
            }
            SleepOption("End of this song") { onSet(PlaybackCommands.SLEEP_END_OF_SONG); onDismiss() }
            if (state != SleepTimerState.Off) {
                SleepOption("Turn off timer") { onSet(0); onDismiss() }
            }
        }
    }
}

@Composable
private fun SleepOption(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 60.dp, vertical = 14.dp),
    )
}

/** "23 min left", "End of song", or null when off. Re-reads the clock every second while shown. */
@Composable
fun sleepTimerLabel(state: SleepTimerState): String? {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (state is SleepTimerState.At) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    return when (state) {
        SleepTimerState.Off -> null
        SleepTimerState.EndOfSong -> "Stops after this song"
        is SleepTimerState.At -> {
            val remaining = state.remainingMs(now)
            val minutes = (remaining + 59_999L) / 60_000L
            if (remaining < 60_000L) "${remaining / 1000L}s left" else "$minutes min left"
        }
    }
}

@Composable
fun PlayerToolButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, badge: String? = null) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .then(if (active) Modifier.neuPressed(RoundedCornerShape(14.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active) Palette.accent else Palette.textPrimary, modifier = Modifier.size(22.dp))
        }
        Text(
            badge ?: label,
            color = if (active) Palette.accent else Palette.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
