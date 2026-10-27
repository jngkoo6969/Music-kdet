package com.example

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AlbumItem
import com.example.data.ArtistItem
import com.example.data.Playlist
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.ScreenDestination
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.EditLyricsDialog
import com.example.ui.components.EditMetadataDialog
import com.example.ui.components.LocalSongActions
import com.example.ui.components.MiniPlayer
import com.example.ui.components.SongActions
import com.example.ui.components.NowPlayingSheet
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.ArtistDetailScreen
import com.example.ui.screens.ArtistsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SongListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppleMusicRed
import com.example.ui.theme.MyApplicationTheme

data class NavTabItem(
    val destination: ScreenDestination,
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onStop() {
        super.onStop()
        viewModel.playbackManager.saveSession()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                SongActionsProvider(viewModel) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

/** Gives every song row the same three-dot menu actions. */
@Composable
fun SongActionsProvider(viewModel: MusicViewModel, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val favoriteIds = remember(allSongs) { allSongs.filter { it.isFavorite }.map { it.id }.toSet() }

    val actions = remember(favoriteIds) {
        SongActions(
            favoriteIds = favoriteIds,
            onPlayNext = { viewModel.playNext(it) },
            onPlayLater = { viewModel.addToQueue(it) },
            onAddToPlaylist = { viewModel.showAddToPlaylist(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onEditMetadata = { viewModel.showEditMetadata(it) },
            onShare = { shareSong(context, it) },
            onDelete = { viewModel.deleteSong(it) }
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSongActions provides actions) {
        content()
    }
}

private fun shareSong(context: android.content.Context, song: Song) {
    try {
        val file = song.filePath?.let { java.io.File(it) }
        val uri: android.net.Uri = try {
            if (file != null && file.exists()) {
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                android.net.Uri.parse(song.uriString)
            }
        } catch (e: Exception) {
            android.net.Uri.parse(song.uriString)
        }
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = song.mimeType?.takeIf { it.startsWith("audio/") } ?: "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "${song.title} - ${song.displayArtist}")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Share ${song.title}").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Couldn't share this song", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainAppScreen(viewModel: MusicViewModel) {
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
    val isLyricsViewVisible by viewModel.isLyricsViewVisible.collectAsStateWithLifecycle()
    val isQueueViewVisible by viewModel.isQueueViewVisible.collectAsStateWithLifecycle()

    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val recentlyPlayedSongs by viewModel.recentlyPlayedSongs.collectAsStateWithLifecycle()
    val recentlyAddedSongs by viewModel.recentlyAddedSongs.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val albums by viewModel.allAlbums.collectAsStateWithLifecycle()
    val artists by viewModel.allArtists.collectAsStateWithLifecycle()
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()

    val playbackState by viewModel.playbackStateLite.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val parsedLyrics by viewModel.parsedLyrics.collectAsStateWithLifecycle()
    val activeLyricIndex by viewModel.activeLyricIndex.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val songSort by viewModel.songSort.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    // Dialog states
    val songToEditMetadata by viewModel.songToEditMetadata.collectAsStateWithLifecycle()
    val songToEditLyrics by viewModel.songToEditLyrics.collectAsStateWithLifecycle()
    val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsStateWithLifecycle()
    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsStateWithLifecycle()
    val songForCustomArt by viewModel.songForCustomArt.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Always use the freshest copy of the playing song (artwork/metadata edits show immediately)
    val liveCurrentSong = remember(playbackState.currentSong, songs) {
        songs.firstOrNull { it.id == playbackState.currentSong?.id } ?: playbackState.currentSong
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToastMessage()
        }
    }

    // Permission launcher for device audio scanning & notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (audioGranted) {
            viewModel.scanDeviceMusic()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    // Photo picker for custom song artwork
    val songPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && songForCustomArt != null) {
            viewModel.applyCustomArtworkToSong(songForCustomArt!!, uri)
        }
    }

    LaunchedEffect(songForCustomArt) {
        if (songForCustomArt != null) {
            songPhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    // Handle Android system back gesture
    BackHandler(enabled = isNowPlayingExpanded || currentDestination !is ScreenDestination.Library) {
        when {
            isNowPlayingExpanded -> viewModel.setNowPlayingExpanded(false)
            else -> viewModel.navigateBack()
        }
    }

    // Scrollable top menu (no background)
    val topTabs = remember {
        listOf(
            NavTabItem(ScreenDestination.Library, "Library", Icons.Default.LibraryMusic, "top_library"),
            NavTabItem(ScreenDestination.Artists, "Artists", Icons.Default.Person, "top_artists"),
            NavTabItem(ScreenDestination.Search, "Search", Icons.Default.Search, "top_search"),
            NavTabItem(ScreenDestination.Albums, "Albums", Icons.Default.Album, "top_albums"),
            NavTabItem(ScreenDestination.Favorites, "Favorites", Icons.Default.Favorite, "top_favorites"),
            NavTabItem(ScreenDestination.Recent, "Recent", Icons.Default.History, "top_recent"),
            NavTabItem(ScreenDestination.Settings, "Settings", Icons.Default.Settings, "top_settings")
        )
    }

    val isTopLevelDestination = when (currentDestination) {
        is ScreenDestination.Library,
        is ScreenDestination.Albums,
        is ScreenDestination.Artists,
        is ScreenDestination.Playlists,
        is ScreenDestination.Favorites,
        is ScreenDestination.Recent,
        is ScreenDestination.Search,
        is ScreenDestination.Settings -> true
        else -> false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isTopLevelDestination) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topTabs.forEach { tab ->
                        val isSelected = currentDestination::class == tab.destination::class
                        val tint = if (isSelected) AppleMusicRed else MaterialTheme.colorScheme.onSurfaceVariant
                        Row(
                            modifier = Modifier
                                .clickable { viewModel.navigateTo(tab.destination) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag(tab.testTag),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                color = tint,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isTopLevelDestination) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    // Floating MiniPlayer above Bottom Navigation
                    if (playbackState.currentSong != null) {
                        val livePlayback by viewModel.playbackState.collectAsStateWithLifecycle()
                        MiniPlayer(
                            currentSong = liveCurrentSong,
                            isPlaying = playbackState.isPlaying,
                            currentPositionMs = livePlayback.currentPositionMs,
                            durationMs = playbackState.durationMs,
                            isCrossfading = playbackState.isCrossfading,
                            onPlayPause = { viewModel.playPause() },
                            onSkipNext = { viewModel.skipNext() },
                            onClick = { viewModel.setNowPlayingExpanded(true) }
                        )
                    }

                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val dest = currentDestination) {
                is ScreenDestination.Library -> {
                    LibraryScreen(
                        songs = songs,
                        onNavigate = { viewModel.navigateTo(it) },
                        onPlaySong = { song, list -> viewModel.playSong(song, list) },
                        sortOption = songSort,
                        onSortChange = { viewModel.setSongSort(it) }
                    )
                }
                is ScreenDestination.Favorites -> {
                    SongListScreen(
                        title = "Favorites",
                        emptyText = "No favorites yet. Tap the heart on a song to add it.",
                        songs = favoriteSongs,
                        onPlaySong = { song, list -> viewModel.playSong(song, list) }
                    )
                }
                is ScreenDestination.Recent -> {
                    SongListScreen(
                        title = "Recent",
                        emptyText = "Nothing played yet.",
                        songs = recentlyPlayedSongs,
                        onPlaySong = { song, list -> viewModel.playSong(song, list) }
                    )
                }
                is ScreenDestination.Albums -> {
                    AlbumsScreen(
                        albums = albums,
                        onSelectAlbum = { viewModel.navigateTo(ScreenDestination.AlbumDetail(it)) }
                    )
                }
                is ScreenDestination.AlbumDetail -> {
                    AlbumDetailScreen(
                        album = albums.firstOrNull { it.title == dest.album.title } ?: dest.album,
                        onBack = { viewModel.navigateBack() },
                        onPlayAll = { viewModel.playQueue(it, 0) },
                        onShuffleAll = {
                            viewModel.toggleShuffle()
                            viewModel.playQueue(it, 0)
                        },
                        onPlaySong = { song, list -> viewModel.playSong(song, list) },
                        onChangeArtwork = { albumItem, uri ->
                            viewModel.applyCustomArtworkToAlbum(albumItem, uri)
                        },
                        formatDuration = { viewModel.formatDuration(it) }
                    )
                }
                is ScreenDestination.Artists -> {
                    ArtistsScreen(
                        artists = artists,
                        onSelectArtist = { viewModel.navigateTo(ScreenDestination.ArtistDetail(it)) }
                    )
                }
                is ScreenDestination.ArtistDetail -> {
                    ArtistDetailScreen(
                        artist = artists.firstOrNull { it.name == dest.artist.name } ?: dest.artist,
                        onBack = { viewModel.navigateBack() },
                        onPlayAll = { viewModel.playQueue(it, 0) },
                        onShuffleAll = {
                            viewModel.toggleShuffle()
                            viewModel.playQueue(it, 0)
                        },
                        onPlaySong = { song, list -> viewModel.playSong(song, list) },
                        formatDuration = { viewModel.formatDuration(it) }
                    )
                }
                is ScreenDestination.Playlists -> {
                    PlaylistsScreen(
                        playlists = playlists,
                        favoriteSongs = favoriteSongs,
                        recentlyPlayedSongs = recentlyPlayedSongs,
                        mostPlayedSongs = mostPlayedSongs,
                        onCreatePlaylist = { viewModel.setShowCreatePlaylist(true) },
                        onSelectPlaylist = { viewModel.navigateTo(ScreenDestination.PlaylistDetail(it)) },
                        onSelectFavorites = {
                            viewModel.navigateTo(
                                ScreenDestination.PlaylistDetail(
                                    Playlist(name = "Favorites", description = "Your starred tracks")
                                )
                            )
                        },
                        onSelectRecentlyPlayed = {
                            viewModel.navigateTo(
                                ScreenDestination.PlaylistDetail(
                                    Playlist(name = "Recently Played", description = "History of played tracks")
                                )
                            )
                        },
                        onSelectMostPlayed = {
                            viewModel.navigateTo(
                                ScreenDestination.PlaylistDetail(
                                    Playlist(name = "Most Played", description = "Your top listened tracks")
                                )
                            )
                        }
                    )
                }
                is ScreenDestination.PlaylistDetail -> {
                    val playlistSongs by viewModel.repository.getSongsForPlaylist(dest.playlist.id)
                        .collectAsStateWithLifecycle(
                            initialValue = when (dest.playlist.name) {
                                "Favorites" -> favoriteSongs
                                "Recently Played" -> recentlyPlayedSongs
                                "Most Played" -> mostPlayedSongs
                                else -> emptyList()
                            }
                        )

                    PlaylistDetailScreen(
                        playlist = dest.playlist,
                        songs = playlistSongs,
                        onBack = { viewModel.navigateBack() },
                        onPlayAll = { viewModel.playQueue(it, 0) },
                        onShuffleAll = {
                            viewModel.toggleShuffle()
                            viewModel.playQueue(it, 0)
                        },
                        onPlaySong = { song, list -> viewModel.playSong(song, list) },
                        onRemoveSong = { songId ->
                            viewModel.removeSongFromPlaylist(dest.playlist.id, songId)
                        },
                        onDeletePlaylist = {
                            viewModel.deletePlaylist(dest.playlist.id)
                        },
                        formatDuration = { viewModel.formatDuration(it) }
                    )
                }
                is ScreenDestination.Search -> {
                    SearchScreen(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        songs = songs,
                        onPlaySong = { song, list -> viewModel.playSong(song, list) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToPlaylist = { viewModel.showAddToPlaylist(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onEditMetadata = { viewModel.showEditMetadata(it) },
                        formatDuration = { viewModel.formatDuration(it) },
                        sortOption = songSort,
                        onSortChange = { viewModel.setSongSort(it) }
                    )
                }
                is ScreenDestination.Settings -> {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { viewModel.setThemeMode(it) },
                        crossfadeDurationSec = playbackState.crossfadeDurationSec,
                        onCrossfadeDurationChange = { viewModel.setCrossfadeSec(it) },
                        manualFadeDurationMs = playbackState.manualFadeDurationMs,
                        onManualFadeDurationChange = { viewModel.setManualFadeMs(it) },
                        songs = songs,
                        onRescanDevice = { viewModel.scanDeviceMusic() },
                        formatDuration = { viewModel.formatDuration(it) }
                    )
                }
            }
        }
    }

    // Fullscreen Animated Now Playing Modal Sheet
    AnimatedVisibility(
        visible = isNowPlayingExpanded && playbackState.currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        val livePlayback by viewModel.playbackState.collectAsStateWithLifecycle()
        NowPlayingSheet(
            playbackState = livePlayback.copy(currentSong = liveCurrentSong),
            queue = queue,
            currentIndex = currentIndex,
            parsedLyrics = parsedLyrics,
            activeLyricIndex = activeLyricIndex,
            isLyricsViewVisible = isLyricsViewVisible,
            isQueueViewVisible = isQueueViewVisible,
            onCollapse = { viewModel.setNowPlayingExpanded(false) },
            onPlayPause = { viewModel.playPause() },
            onSkipNext = { viewModel.skipNext() },
            onSkipPrevious = { viewModel.skipPrevious() },
            onSeekTo = { viewModel.seekTo(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onToggleLyricsView = { viewModel.toggleLyricsView() },
            onToggleQueueView = { viewModel.toggleQueueView() },
            onPlayTrackAt = { index ->
                if (index in queue.indices) {
                    viewModel.playSong(queue[index])
                }
            },
            onRemoveFromQueue = { viewModel.removeFromQueue(it) },
            onClearQueue = { viewModel.clearQueue() },
            onEditMetadata = { viewModel.showEditMetadata(it) },
            onEditLyrics = { viewModel.showEditLyrics(it) },
            onChangeArtwork = { viewModel.setSongForCustomArt(it) }
        )
    }

    // Dialogs
    songToEditMetadata?.let { song ->
        EditMetadataDialog(
            song = song,
            onDismiss = { viewModel.showEditMetadata(null) },
            onSave = { songId, title, artist, album, year ->
                viewModel.saveSongMetadata(songId, title, artist, album, year)
            }
        )
    }

    songToEditLyrics?.let { song ->
        EditLyricsDialog(
            song = song,
            onDismiss = { viewModel.showEditLyrics(null) },
            onSave = { songId, lyrics ->
                viewModel.saveLyrics(songId, lyrics)
            }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.setShowCreatePlaylist(false) },
            onCreate = { name, desc ->
                viewModel.createPlaylist(name, desc)
            }
        )
    }

    songToAddToPlaylist?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { viewModel.showAddToPlaylist(null) },
            onSelectPlaylist = { playlistId ->
                viewModel.addSongToPlaylist(playlistId, song.id)
            },
            onCreateNewPlaylist = {
                viewModel.showAddToPlaylist(null)
                viewModel.setShowCreatePlaylist(true)
            }
        )
    }
}
