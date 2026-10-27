package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.Song
import com.example.ui.theme.AppleMusicRed

/** Everything a song's three-dot menu can do. Provided once in MainActivity. */
data class SongActions(
    val favoriteIds: Set<Long> = emptySet(),
    val onPlayNext: (Song) -> Unit = {},
    val onPlayLater: (Song) -> Unit = {},
    val onAddToPlaylist: (Song) -> Unit = {},
    val onToggleFavorite: (Song) -> Unit = {},
    val onEditMetadata: (Song) -> Unit = {},
    val onShare: (Song) -> Unit = {},
    val onDelete: (Song) -> Unit = {}
)

val LocalSongActions = compositionLocalOf { SongActions() }

/** The three-dot button shown on every song row. */
@Composable
fun SongMenuButton(song: Song, modifier: Modifier = Modifier) {
    val actions = LocalSongActions.current
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isFavorite = actions.favoriteIds.contains(song.id)

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Song options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Play Next") },
                leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) },
                onClick = { expanded = false; actions.onPlayNext(song) }
            )
            DropdownMenuItem(
                text = { Text("Play Later (Add to Queue)") },
                leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                onClick = { expanded = false; actions.onPlayLater(song) }
            )
            DropdownMenuItem(
                text = { Text("Add to Playlist") },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                onClick = { expanded = false; actions.onAddToPlaylist(song) }
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) AppleMusicRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { expanded = false; actions.onToggleFavorite(song) }
            )
            DropdownMenuItem(
                text = { Text("Edit Info") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { expanded = false; actions.onEditMetadata(song) }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = { expanded = false; actions.onShare(song) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = Color(0xFFFF3B30)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF3B30)) },
                onClick = { expanded = false; confirmDelete = true }
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete song?") },
            text = { Text("\"${song.title}\" will be removed from your library and from the queue. Files you imported into the app are deleted too; music on your phone's storage is not touched.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDelete(song) }) {
                    Text("Delete", color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------
// Sorting
// ---------------------------------------------------------------------

enum class SongSortOption(val label: String) {
    DATE_ADDED("Date Added"),
    TITLE("Name"),
    ARTIST("Artist"),
    ALBUM("Album"),
    MOST_PLAYED("Most Played")
}

fun List<Song>.sortedFor(option: SongSortOption): List<Song> = when (option) {
    SongSortOption.DATE_ADDED -> sortedByDescending { it.addedTimestamp }
    SongSortOption.TITLE -> sortedBy { it.title.lowercase() }
    SongSortOption.ARTIST -> sortedWith(
        compareBy<Song>({ it.displayArtist.lowercase() }, { it.album.lowercase() }, { it.trackNumber ?: 0 }, { it.title.lowercase() })
    )
    SongSortOption.ALBUM -> sortedWith(
        compareBy<Song>({ it.album.lowercase() }, { it.trackNumber ?: 0 }, { it.title.lowercase() })
    )
    SongSortOption.MOST_PLAYED -> sortedWith(
        compareByDescending<Song> { it.playCount }.thenBy { it.title.lowercase() }
    )
}

@Composable
fun SortButton(selected: SongSortOption, onSelected: (SongSortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = "Sort",
                tint = AppleMusicRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(selected.label, color = AppleMusicRed)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SongSortOption.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AppleMusicRed)
                        }
                    },
                    onClick = { expanded = false; onSelected(option) }
                )
            }
        }
    }
}

/** A song row drawn as a rounded "block" card. */
@Composable
fun Modifier.songBlock(onClick: () -> Unit): Modifier {
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    return this
        .padding(vertical = 3.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(color)
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp)
}
