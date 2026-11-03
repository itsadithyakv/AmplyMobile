package com.amply.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amply.mobile.artwork.ArtworkLoader
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.Song
import com.amply.mobile.ui.theme.NeuDepth
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuAccent
import com.amply.mobile.ui.theme.neuPressed
import com.amply.mobile.ui.theme.neuRaised

// Radii follow the desktop's four-step scale (--radius-sm/md/lg and full).
val RadiusSm = RoundedCornerShape(10.dp)
val RadiusMd = RoundedCornerShape(16.dp)
val RadiusLg = RoundedCornerShape(24.dp)

/** Now-playing strip above the tab bar, like the desktop player bar: raised, accent play button. */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
) {
    val p = Palette
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Open now playing", onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkBox(song.artworkUri, 48.dp, shape = RadiusSm, audioUri = song.contentUri)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            NeuIconButton(
                if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (song.favorite) "Remove from liked songs" else "Add to liked songs",
                onFavorite,
                size = 40.dp,
                flat = true,
                tint = if (song.favorite) p.accent else p.textSecondary,
            )
            Spacer(Modifier.width(4.dp))
            NeuIconButton(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                onPlayPause,
                size = 46.dp,
                accent = true,
            )
            Spacer(Modifier.width(4.dp))
            NeuIconButton(Icons.Rounded.SkipNext, "Next song", onNext, size = 40.dp, flat = true)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(p.textMuted.copy(alpha = 0.18f)),
        ) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(p.accent))
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** A song row: cover, bold title, secondary artist, like and menu. The playing song is amber. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListRow(
    song: Song,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    isCurrent: Boolean = false,
) {
    val p = Palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isCurrent) Modifier.neuPressed(RadiusMd) else Modifier)
            .clip(RadiusMd)
            .combinedClickable(onClick = onClick, onLongClick = onMore, onLongClickLabel = "Song options")
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(song.artworkUri, 50.dp, shape = RadiusSm, audioUri = song.contentUri)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) p.accent else p.textPrimary,
            )
            Text(
                listOf(song.artist, song.album).filter { it.isNotBlank() && !it.startsWith("Unknown") }.joinToString("  ·  ").ifBlank { song.artist },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = p.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (onFavorite != null) {
            NeuIconButton(
                if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (song.favorite) "Remove from liked songs" else "Add to liked songs",
                onFavorite,
                size = 38.dp,
                flat = true,
                tint = if (song.favorite) p.accent else p.textMuted,
            )
        }
        if (onMore != null) {
            NeuIconButton(Icons.Rounded.MoreVert, "Song options", onMore, size = 38.dp, flat = true, tint = p.textMuted)
        } else {
            NeuIconButton(Icons.Rounded.PlayArrow, "Play ${song.title}", onPlay, size = 38.dp)
        }
    }
}

@Composable
fun PlaylistListRow(playlist: Playlist, onClick: () -> Unit, onPlay: () -> Unit, artworkUri: String? = playlist.artworkUri) {
    val p = Palette
    Row(
        modifier = Modifier.fillMaxWidth().clip(RadiusMd).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(artworkUri, 50.dp, shape = RadiusSm)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.songIds.size} songs", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        NeuIconButton(Icons.Rounded.PlayArrow, "Play ${playlist.name}", onPlay, size = 38.dp)
    }
}

@Composable
fun GenreRow(genre: String, count: Int, onClick: () -> Unit) {
    val p = Palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neuRaised(RadiusMd)
            .clip(RadiusMd)
            .clickable(onClickLabel = "Play $genre", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = p.accent)
        Spacer(Modifier.width(14.dp))
        Text(genre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("$count songs", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

/** Small uppercase label used above groups, like the desktop's "SONGS" / "SORT" captions. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Palette.textMuted,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = MaterialTheme.typography.labelMedium.fontSize * 0.12f),
    )
}

/** Raised panel: the desktop's `.neu-raised` card. */
@Composable
fun NeuCard(modifier: Modifier = Modifier, shape: Shape = RadiusLg, padding: Dp = 18.dp, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().neuRaised(shape, NeuDepth.Raised).padding(padding)) { content() }
}

@Composable
fun EmptyLibraryActions(onAddFolder: () -> Unit, onScan: () -> Unit) {
    val p = Palette
    NeuCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).neuPressed(CircleShape, small = false), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = p.accent, modifier = Modifier.size(30.dp))
            }
            Text("Your library is empty", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Scan the music on this phone, or pick a folder and Amply will keep it in your library.",
                color = p.textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton("Scan device", Icons.Rounded.Refresh, onScan, accent = true)
                NeuButton("Add folder", Icons.Rounded.FolderOpen, onAddFolder)
            }
        }
    }
}

@Composable
fun OverflowActions(onAddFolder: () -> Unit, onScan: () -> Unit, onRefreshSmart: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        NeuIconButton(Icons.Rounded.MoreHoriz, "More", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.bg) {
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
        NeuIconButton(Icons.Rounded.MoreHoriz, "Library actions", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.bg) {
            DropdownMenuItem(text = { Text("Add folder") }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) }, onClick = { open = false; onAddFolder() })
            DropdownMenuItem(text = { Text("Scan device") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { open = false; onScan() })
        }
    }
}

/** Filter pill: raised when idle, pressed in with amber text when selected (desktop tab pills). */
@Composable
fun AmplyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = Palette
    val shape = CircleShape
    Box(
        modifier = Modifier
            .then(if (selected) Modifier.neuPressed(shape) else Modifier.neuRaised(shape))
            .clip(shape)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (selected) p.accent else p.textSecondary, style = MaterialTheme.typography.labelLarge)
    }
}

/** Kept for existing call sites: a round raised icon button, amber when highlighted or active. */
@Composable
fun AmplyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
    active: Boolean = false,
) {
    NeuIconButton(
        icon,
        contentDescription,
        onClick,
        accent = highlight,
        pressed = active && !highlight,
        tint = if (active && !highlight) Palette.accent else null,
    )
}

/**
 * Round icon button in the three desktop states: raised (default), pressed (on/selected) and accent
 * (the main action). `flat` drops the surface for dense rows, where a field of raised circles is noise.
 */
@Composable
fun NeuIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 46.dp,
    accent: Boolean = false,
    pressed: Boolean = false,
    flat: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val p = Palette
    val surface = when {
        accent -> Modifier.neuAccent(CircleShape)
        pressed -> Modifier.neuPressed(CircleShape)
        flat -> Modifier
        else -> Modifier.neuRaised(CircleShape)
    }
    Box(
        modifier = Modifier
            .size(size)
            .then(surface)
            .clip(CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint ?: if (accent) p.onAccent else p.textPrimary,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** Text button with an optional icon, raised or amber. */
@Composable
fun NeuButton(label: String, icon: ImageVector?, onClick: () -> Unit, accent: Boolean = false, modifier: Modifier = Modifier) {
    val p = Palette
    val shape = CircleShape
    Row(
        modifier = modifier
            .then(if (accent) Modifier.neuAccent(shape) else Modifier.neuRaised(shape))
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (accent) p.onAccent else p.textPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = if (accent) p.onAccent else p.textPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

/** Cover art, or (like desktop 1.8.1) a quiet music note instead of the logo when there is none. */
@Composable
fun ArtworkBox(
    artworkUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RadiusSm,
    audioUri: String? = null,
) {
    val p = Palette
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by rememberArtwork(artworkUri, sizePx, audioUri)
    Box(
        modifier = modifier.size(size).aspectRatio(1f).clip(shape).background(p.bgDeep),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = p.textMuted.copy(alpha = 0.7f), modifier = Modifier.size(size * 0.4f))
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

/**
 * Cover art for [uri], decoded at about [sizePx] and shared through [ArtworkLoader]'s memory cache.
 * [audioUri] lets songs without an album-art uri fall back to the picture embedded in the file.
 */
@Composable
fun rememberArtwork(uri: String?, sizePx: Int = 256, audioUri: String? = null): androidx.compose.runtime.State<ImageBitmap?> {
    val context = LocalContext.current
    val initial = remember(uri, audioUri, sizePx) { ArtworkLoader.cached(uri ?: audioUri, sizePx)?.asImageBitmap() }
    return produceState(initialValue = initial, uri, audioUri, sizePx) {
        if (value == null) {
            value = ArtworkLoader.load(context.applicationContext, uri, sizePx, audioUri)?.asImageBitmap()
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
