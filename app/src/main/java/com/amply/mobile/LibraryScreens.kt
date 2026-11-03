package com.amply.mobile

import com.amply.mobile.util.plural
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amply.mobile.domain.Playlist
import com.amply.mobile.domain.PlaylistType
import com.amply.mobile.domain.Song
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuPressed
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import com.amply.mobile.playlist.RulePlaylist
import com.amply.mobile.playlist.describe
import com.amply.mobile.ui.theme.neuRaised
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor

@Composable
fun SearchScreen(
    query: String,
    results: List<Song>,
    currentSongId: Long?,
    onQuery: (String) -> Unit,
    onSong: (Song) -> Unit,
    onMore: (Song) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Search", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            NeuSearchField(query = query, onQuery = onQuery, placeholder = "Songs, artists, albums, genres")
        }
        if (query.isNotBlank() && results.isEmpty()) {
            item {
                Text("No songs match \"$query\"", color = Palette.textSecondary, modifier = Modifier.padding(top = 24.dp))
            }
        }
        items(results, key = { it.id }) { song ->
            SongListRow(song = song, onClick = { onSong(song) }, onPlay = { onSong(song) }, onMore = { onMore(song) }, isCurrent = song.id == currentSongId)
        }
    }
}

enum class LibraryFilter(val label: String) { All("Songs"), Artists("Artists"), Albums("Albums"), Playlists("Playlists"), Liked("Liked"), Genres("Genres") }

enum class SongSort(val label: String) { Title("Title"), Artist("Artist"), RecentlyAdded("Recently added"), MostPlayed("Most played") }

fun List<Song>.sortedBy(sort: SongSort): List<Song> = when (sort) {
    SongSort.Title -> sortedBy { it.title.lowercase() }
    SongSort.Artist -> sortedWith(compareBy<Song> { it.artist.lowercase() }.thenBy { it.album.lowercase() }.thenBy { it.track })
    SongSort.RecentlyAdded -> sortedByDescending { it.addedAtSec }
    SongSort.MostPlayed -> sortedWith(compareByDescending<Song> { it.playCount }.thenByDescending { it.lastPlayedAtSec ?: 0L })
}

data class SongGroup(val key: String, val title: String, val subtitle: String, val songs: List<Song>)

@Composable
fun LibraryScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    currentSongId: Long?,
    onSong: (Song, List<Song>) -> Unit,
    onFavorite: (Song) -> Unit,
    onMore: (Song) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onAddFolder: () -> Unit,
    onScan: () -> Unit,
    onShuffleAll: () -> Unit,
    ruleResults: List<Pair<RulePlaylist, List<Song>>> = emptyList(),
    isPro: Boolean = false,
    onNewRulePlaylist: () -> Unit = {},
    onEditRulePlaylist: (RulePlaylist) -> Unit = {},
    onPlayRuleSongs: (List<Song>, Boolean) -> Unit = { _, _ -> },
) {
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    var sort by rememberSaveable { mutableStateOf(SongSort.Title) }
    val sorted = remember(songs, sort) { songs.sortedBy(sort) }
    val liked = remember(sorted) { sorted.filter { it.favorite } }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val artists = remember(songs) {
        songs.groupBy { com.amply.mobile.stats.primaryArtist(it.artist) }
            .map { (artist, list) ->
                val ordered = list.sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.track })
                SongGroup(artist, artist, "${plural(list.size, "song")}  ·  ${plural(list.map { it.album }.distinct().size, "album")}", ordered)
            }
            .sortedBy { it.title.lowercase() }
    }
    val albums = remember(songs) {
        songs.groupBy { it.album.lowercase() to (it.albumId ?: 0L) }
            .map { (_, list) ->
                val first = list.first()
                SongGroup("${first.album}|${first.albumId}", first.album, "${first.artist}  ·  ${plural(list.size, "song")}", list.sortedBy { it.track })
            }
            .sortedBy { it.title.lowercase() }
    }
    val genres = remember(songs) { songs.groupBy { it.effectiveGenre }.toList().sortedByDescending { it.second.size } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.headlineLarge)
                    Text("${plural(songs.size, "song")} indexed", color = Palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (songs.isNotEmpty()) {
                    AmplyIconButton(Icons.Rounded.Shuffle, "Shuffle all songs", onShuffleAll, highlight = true)
                    Spacer(Modifier.width(10.dp))
                }
                LibraryActions(onAddFolder = onAddFolder, onScan = onScan)
            }
        }
        if (filter == LibraryFilter.All || filter == LibraryFilter.Liked) {
            item { SortMenu(sort) { sort = it } }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(LibraryFilter.entries) { item ->
                    AmplyChip(
                        label = item.label,
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
            LibraryFilter.All -> items(sorted, key = { it.id }) { song ->
                SongListRow(
                    song = song,
                    onClick = { onSong(song, sorted) },
                    onPlay = { onSong(song, sorted) },
                    onFavorite = { onFavorite(song) },
                    onMore = { onMore(song) },
                    isCurrent = song.id == currentSongId,
                )
            }
            LibraryFilter.Liked -> {
                if (liked.isEmpty() && songs.isNotEmpty()) {
                    item { Text("Tap the heart on a song to keep it here.", color = Palette.textSecondary) }
                }
                items(liked, key = { it.id }) { song ->
                    SongListRow(
                        song = song,
                        onClick = { onSong(song, liked) },
                        onPlay = { onSong(song, liked) },
                        onFavorite = { onFavorite(song) },
                        onMore = { onMore(song) },
                        isCurrent = song.id == currentSongId,
                    )
                }
            }
            LibraryFilter.Playlists -> {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Smart playlists", style = MaterialTheme.typography.titleLarge)
                            Text("Fill themselves from rules and stay up to date.", color = Palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!isPro) ProLock(Modifier.padding(end = 8.dp))
                        NeuButton("New", Icons.Rounded.Add, onNewRulePlaylist)
                    }
                }
                items(ruleResults, key = { it.first.id }) { (rule, ruleSongs) ->
                    RulePlaylistRow(rule, ruleSongs, onPlay = { onPlayRuleSongs(ruleSongs, false) }, onEdit = { onEditRulePlaylist(rule) })
                }
                item { Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                items(playlists, key = { it.id }) { playlist ->
                PlaylistListRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    onPlay = { onPlayPlaylist(playlist) },
                    artworkUri = playlist.songIds.firstNotNullOfOrNull { id -> songsById[id]?.artworkUri } ?: playlist.artworkUri,
                )
            }
            }
            LibraryFilter.Artists -> items(artists, key = { it.key }) { group ->
                GroupRow(group, round = true) { group.songs.firstOrNull()?.let { onSong(it, group.songs) } }
            }
            LibraryFilter.Albums -> items(albums, key = { it.key }) { group ->
                GroupRow(group, round = false) { group.songs.firstOrNull()?.let { onSong(it, group.songs) } }
            }
            // A genre row plays that genre; it used to start only its first song.
            LibraryFilter.Genres -> items(genres, key = { it.first }) { (genre, genreSongs) ->
                GenreRow(genre = genre, count = genreSongs.size, onClick = { genreSongs.firstOrNull()?.let { onSong(it, genreSongs) } })
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    songs: List<Song>,
    currentSongId: Long?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSong: (Song) -> Unit,
    onMore: (Song) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmplyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(plural(songs.size, "song"), color = Palette.textSecondary)
                }
                if (playlist?.type == PlaylistType.Custom) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist") }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AmplyIconButton(Icons.Rounded.PlayArrow, "Play playlist", onPlay, highlight = true)
                    AmplyIconButton(Icons.Rounded.Shuffle, "Shuffle playlist", onShuffle)
                    playlist?.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Palette.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No songs yet. Long-press any song and choose \"Add to playlist\".", color = Palette.textSecondary)
                }
            }
        }
        items(songs, key = { it.id }) { song ->
            SongListRow(song = song, onClick = { onSong(song) }, onPlay = { onSong(song) }, onMore = { onMore(song) }, isCurrent = song.id == currentSongId)
        }
    }
    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("The songs stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** The desktop's pressed-in search field. */
@Composable
fun NeuSearchField(query: String, onQuery: (String) -> Unit, placeholder: String) {
    val p = Palette
    Row(
        Modifier.fillMaxWidth().neuPressed(CircleShape, small = false).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = p.textMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (query.isEmpty()) Text(placeholder, color = p.textMuted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.textPrimary),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            NeuIconButton(Icons.Rounded.Close, "Clear search", { onQuery("") }, size = 34.dp, flat = true, tint = p.textMuted)
        }
    }
}

@Composable
private fun RulePlaylistRow(playlist: RulePlaylist, songs: List<Song>, onPlay: () -> Unit, onEdit: () -> Unit) {
    val p = Palette
    Row(
        Modifier.fillMaxWidth().neuRaised(RadiusMd).clip(RadiusMd).clickable(onClickLabel = "Play ${playlist.name}", onClick = onPlay).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(songs.firstOrNull()?.artworkUri, 46.dp, shape = RadiusSm, audioUri = songs.firstOrNull()?.contentUri)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${plural(songs.size, "song")}  ·  ${playlist.describe()}", color = p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NeuIconButton(Icons.Rounded.Edit, "Edit ${playlist.name}", onEdit, size = 36.dp, flat = true, tint = p.textMuted)
    }
}

/** An artist or album: cover, name, counts. Tapping plays it in album and track order. */
@Composable
private fun GroupRow(group: SongGroup, round: Boolean, onPlay: () -> Unit) {
    val p = Palette
    val cover = group.songs.firstOrNull { it.artworkUri != null } ?: group.songs.firstOrNull()
    Row(
        Modifier.fillMaxWidth().clip(RadiusMd).clickable(onClickLabel = "Play ${group.title}", onClick = onPlay).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkBox(cover?.artworkUri, 50.dp, shape = if (round) CircleShape else RadiusSm, audioUri = cover?.contentUri)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(group.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(group.subtitle, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NeuIconButton(Icons.Rounded.PlayArrow, "Play ${group.title}", onPlay, size = 38.dp)
    }
}

@Composable
private fun SortMenu(sort: SongSort, onSort: (SongSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(CircleShape).clickable(onClickLabel = "Change sort order") { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eyebrow("Sort")
            Spacer(Modifier.width(8.dp))
            Text(sort.label, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.textMuted)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.bg) {
            SongSort.entries.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option.label, color = if (option == sort) Palette.accent else Palette.textPrimary) },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                )
            }
        }
    }
}
