package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlbumItem
import com.example.data.ArtistItem
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.data.Playlist
import com.example.data.Song
import com.example.lyrics.LyricsParser
import com.example.lyrics.ParsedLyrics
import com.example.playback.PlaybackManager
import com.example.playback.PlaybackState
import com.example.ui.components.SongSortOption
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ScreenDestination {
    object Library : ScreenDestination()
    object Albums : ScreenDestination()
    object Artists : ScreenDestination()
    object Playlists : ScreenDestination()
    object Favorites : ScreenDestination()
    object Recent : ScreenDestination()
    object Search : ScreenDestination()
    object Settings : ScreenDestination()
    data class AlbumDetail(val album: AlbumItem) : ScreenDestination()
    data class ArtistDetail(val artist: ArtistItem) : ScreenDestination()
    data class PlaylistDetail(val playlist: Playlist) : ScreenDestination()
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getInstance(application)
    val repository = MusicRepository(application, database.songDao(), database.playlistDao())
    val playbackManager = PlaybackManager.getInstance(application)

    // UI Navigation State
    private val _currentDestination = MutableStateFlow<ScreenDestination>(ScreenDestination.Library)
    val currentDestination: StateFlow<ScreenDestination> = _currentDestination.asStateFlow()

    private val _destinationBackStack = mutableListOf<ScreenDestination>()

    // Now Playing Sheet & Sub-views
    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isLyricsViewVisible = MutableStateFlow(false)
    val isLyricsViewVisible: StateFlow<Boolean> = _isLyricsViewVisible.asStateFlow()

    private val _isQueueViewVisible = MutableStateFlow(false)
    val isQueueViewVisible: StateFlow<Boolean> = _isQueueViewVisible.asStateFlow()

    // Dialogs & Sheets
    private val _songToEditMetadata = MutableStateFlow<Song?>(null)
    val songToEditMetadata: StateFlow<Song?> = _songToEditMetadata.asStateFlow()

    private val _songToEditLyrics = MutableStateFlow<Song?>(null)
    val songToEditLyrics: StateFlow<Song?> = _songToEditLyrics.asStateFlow()

    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    val songToAddToPlaylist: StateFlow<Song?> = _songToAddToPlaylist.asStateFlow()

    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog: StateFlow<Boolean> = _showCreatePlaylistDialog.asStateFlow()

    private val _songForCustomArt = MutableStateFlow<Song?>(null)
    val songForCustomArt: StateFlow<Song?> = _songForCustomArt.asStateFlow()

    private val _albumForCustomArt = MutableStateFlow<AlbumItem?>(null)
    val albumForCustomArt: StateFlow<AlbumItem?> = _albumForCustomArt.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Song sorting (remembered between launches)
    private val uiPrefs = application.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE)
    private val _songSort = MutableStateFlow(
        try {
            SongSortOption.valueOf(uiPrefs.getString("song_sort", SongSortOption.TITLE.name) ?: SongSortOption.TITLE.name)
        } catch (e: Exception) { SongSortOption.TITLE }
    )
    val songSort: StateFlow<SongSortOption> = _songSort.asStateFlow()

    fun setSongSort(option: SongSortOption) {
        _songSort.value = option
        uiPrefs.edit().putString("song_sort", option.name).apply()
    }

    // Theme Mode
    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    // Status message (e.g. "Imported 12 songs")
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Library data
    val allSongs: StateFlow<List<Song>> = repository.allSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val recentlyPlayedSongs: StateFlow<List<Song>> = repository.recentlyPlayedSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val recentlyAddedSongs: StateFlow<List<Song>> = repository.recentlyAddedSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val mostPlayedSongs: StateFlow<List<Song>> = repository.mostPlayedSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allAlbums: StateFlow<List<AlbumItem>> = repository.allAlbums.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allArtists: StateFlow<List<ArtistItem>> = repository.allArtists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // Playback state from engine
    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState
    val queue: StateFlow<List<Song>> = playbackManager.queue
    val currentIndex: StateFlow<Int> = playbackManager.currentIndex

    // Real-time Lyrics parsing & active line tracking
    // Same state but without the position, so the whole app does not redraw 4x per second
    val playbackStateLite: StateFlow<PlaybackState> = playbackState
        .map { it.copy(currentPositionMs = 0L) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackState())

    val parsedLyrics: StateFlow<ParsedLyrics> = playbackState
        .map { it.currentSong?.lyrics }
        .distinctUntilChanged()
        .map { LyricsParser.parse(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ParsedLyrics(emptyList(), false))

    val activeLyricIndex: StateFlow<Int> = combine(parsedLyrics, playbackState) { lyrics, state ->
        LyricsParser.findActiveLineIndex(lyrics, state.currentPositionMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    init {
        playbackManager.setOnSongPlayedListener { song ->
            viewModelScope.launch {
                repository.recordPlay(song)
            }
        }

        viewModelScope.launch {
            repository.ensureDemoTracksIfEmpty()
        }

        // Bring back the last song / queue / position after the app was closed
        viewModelScope.launch {
            val library = repository.allSongs.first { it.isNotEmpty() }
            playbackManager.restoreSession(library)
        }
    }

    fun navigateTo(destination: ScreenDestination) {
        if (_currentDestination.value != destination) {
            _destinationBackStack.add(_currentDestination.value)
            _currentDestination.value = destination
        }
    }

    fun navigateBack(): Boolean {
        if (_destinationBackStack.isNotEmpty()) {
            _currentDestination.value = _destinationBackStack.removeAt(_destinationBackStack.size - 1)
            return true
        }
        return false
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
        if (!expanded) {
            _isLyricsViewVisible.value = false
            _isQueueViewVisible.value = false
        }
    }

    fun toggleLyricsView() {
        _isLyricsViewVisible.value = !_isLyricsViewVisible.value
        if (_isLyricsViewVisible.value) {
            _isQueueViewVisible.value = false
        }
    }

    fun toggleQueueView() {
        _isQueueViewVisible.value = !_isQueueViewVisible.value
        if (_isQueueViewVisible.value) {
            _isLyricsViewVisible.value = false
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    // Playback controls
    fun playSong(song: Song, contextQueue: List<Song>? = null) {
        val targetQueue = when {
            !contextQueue.isNullOrEmpty() -> contextQueue
            allSongs.value.isNotEmpty() -> allSongs.value
            else -> listOf(song)
        }
        val index = targetQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playbackManager.playQueue(targetQueue, index)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        playbackManager.playQueue(songs, startIndex)
    }

    fun playPause() = playbackManager.playPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)
    fun toggleRepeat() = playbackManager.toggleRepeat()
    fun toggleShuffle() = playbackManager.toggleShuffle()
    fun setCrossfadeSec(seconds: Int) = playbackManager.setCrossfadeDuration(seconds)
    fun setManualFadeMs(millis: Long) = playbackManager.setManualFadeDuration(millis)
    fun addToQueue(song: Song) = playbackManager.addToQueue(song)
    fun playNext(song: Song) = playbackManager.playNextInQueue(song)
    fun removeFromQueue(index: Int) = playbackManager.removeFromQueue(index)
    fun reorderQueue(from: Int, to: Int) = playbackManager.reorderQueue(from, to)
    fun clearQueue() = playbackManager.clearQueue()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    // Imports & Scans
    fun importAudioUris(uris: List<Uri>) {
        viewModelScope.launch {
            val count = repository.importAudioUris(uris)
            _toastMessage.value = if (count > 0) "Imported $count song(s) successfully" else "No valid audio files imported"
        }
    }

    fun scanDeviceMusic() {
        viewModelScope.launch {
            val count = repository.scanDeviceMusic()
            _toastMessage.value = if (count > 0) "Found and indexed $count song(s)" else "No new songs found on device"
        }
    }

    // Artwork & Metadata & Lyrics editing
    fun showEditMetadata(song: Song?) { _songToEditMetadata.value = song }
    fun showEditLyrics(song: Song?) { _songToEditLyrics.value = song }
    fun showAddToPlaylist(song: Song?) { _songToAddToPlaylist.value = song }
    fun setShowCreatePlaylist(show: Boolean) { _showCreatePlaylistDialog.value = show }
    fun setSongForCustomArt(song: Song?) { _songForCustomArt.value = song }
    fun setAlbumForCustomArt(album: AlbumItem?) { _albumForCustomArt.value = album }

    fun saveSongMetadata(songId: Long, title: String, artist: String, album: String, year: Int?) {
        viewModelScope.launch {
            repository.updateMetadata(songId, title, artist, album, year)
            _songToEditMetadata.value = null
            _toastMessage.value = "Updated metadata for $title"
        }
    }

    fun saveLyrics(songId: Long, lyrics: String?) {
        viewModelScope.launch {
            repository.updateLyrics(songId, lyrics)
            _songToEditLyrics.value = null
            _toastMessage.value = "Lyrics saved"
        }
    }

    fun applyCustomArtworkToSong(song: Song, imageUri: Uri) {
        viewModelScope.launch {
            val savedPath = repository.saveCustomArtwork(imageUri)
            if (savedPath != null) {
                repository.updateSongArtwork(song.id, savedPath)
                _toastMessage.value = "Custom artwork applied to ${song.title}"
            }
            _songForCustomArt.value = null
        }
    }

    fun applyCustomArtworkToAlbum(album: AlbumItem, imageUri: Uri) {
        viewModelScope.launch {
            val savedPath = repository.saveCustomArtwork(imageUri)
            if (savedPath != null) {
                repository.updateAlbumArtwork(album.title, savedPath)
                _toastMessage.value = "Custom artwork applied to album ${album.title}"
            }
            _albumForCustomArt.value = null
        }
    }

    // Playlists
    fun createPlaylist(name: String, desc: String?) {
        viewModelScope.launch {
            repository.createPlaylist(name, desc)
            _showCreatePlaylistDialog.value = false
            _toastMessage.value = "Created playlist '$name'"
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            _songToAddToPlaylist.value = null
            _toastMessage.value = "Added to playlist"
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            _toastMessage.value = "Removed from playlist"
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_currentDestination.value is ScreenDestination.PlaylistDetail) {
                navigateBack()
            }
            _toastMessage.value = "Deleted playlist"
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            playbackManager.removeSongById(song.id)
            repository.deleteSong(song)
            _toastMessage.value = "Deleted ${song.title}"
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
