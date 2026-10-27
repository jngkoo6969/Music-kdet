package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Song
import com.example.ui.components.ArtworkView
import com.example.ui.components.LocalSongActions
import com.example.ui.components.SongMenuButton
import com.example.ui.components.songBlock
import com.example.ui.components.SongSortOption
import com.example.ui.components.SortButton
import com.example.ui.components.sortedFor
import com.example.ui.theme.AppleMusicRed

enum class SearchFilter {
    ALL, TITLE, ARTIST, ALBUM, LYRICS
}

@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    songs: List<Song>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit,
    formatDuration: (Long) -> String,
    sortOption: SongSortOption = SongSortOption.TITLE,
    onSortChange: (SongSortOption) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }

    val filteredSongs = remember(searchQuery, selectedFilter, songs) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            songs
        } else {
            songs.filter { song ->
                when (selectedFilter) {
                    SearchFilter.ALL -> song.title.lowercase().contains(q) ||
                            song.artist.lowercase().contains(q) ||
                            song.album.lowercase().contains(q) ||
                            (song.lyrics != null && song.lyrics.lowercase().contains(q))
                    SearchFilter.TITLE -> song.title.lowercase().contains(q)
                    SearchFilter.ARTIST -> song.artist.lowercase().contains(q)
                    SearchFilter.ALBUM -> song.album.lowercase().contains(q)
                    SearchFilter.LYRICS -> song.lyrics != null && song.lyrics.lowercase().contains(q)
                }
            }
        }
    }

    val sortedSongs = remember(filteredSongs, sortOption) { filteredSongs.sortedFor(sortOption) }
    val liveFavorites = LocalSongActions.current.favoriteIds

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .testTag("search_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Search Input Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Artists, Songs, Lyrics, and more") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AppleMusicRed)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppleMusicRed,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(SearchFilter.values()) { filter ->
                val isSelected = filter == selectedFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppleMusicRed,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredSongs.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            SortButton(selected = sortOption, onSelected = onSortChange)
        }

        if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "No songs in library" else "No matching results for '$searchQuery'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sortedSongs, key = { it.id }) { song ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .songBlock { onPlaySong(song, sortedSongs) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkView(
                            artworkUri = song.artworkUri,
                            modifier = Modifier.size(50.dp),
                            cornerRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.displayArtist} • ${song.displayAlbum}",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onToggleFavorite(song) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (liveFavorites.contains(song.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (liveFavorites.contains(song.id)) AppleMusicRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        SongMenuButton(song)
                    }
                }
            }
        }
    }
}
