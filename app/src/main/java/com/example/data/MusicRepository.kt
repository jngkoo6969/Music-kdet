package com.example.data

import android.content.Context
import android.net.Uri
import com.example.lyrics.LyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class AlbumItem(
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val songCount: Int,
    val totalDurationMs: Long,
    val year: Int?,
    val songs: List<Song>
)

data class ArtistItem(
    val name: String,
    val artworkUri: String?,
    val songCount: Int,
    val albumCount: Int,
    val songs: List<Song>
)

class MusicRepository(
    private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {
    private val importer = AudioFileImporter(context)

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val favoriteSongs: Flow<List<Song>> = songDao.getFavoriteSongs()
    val recentlyPlayedSongs: Flow<List<Song>> = songDao.getRecentlyPlayedSongs()
    val recentlyAddedSongs: Flow<List<Song>> = songDao.getRecentlyAddedSongs()
    val mostPlayedSongs: Flow<List<Song>> = songDao.getMostPlayedSongs()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    val allAlbums: Flow<List<AlbumItem>> = allSongs.map { songs ->
        songs.groupBy { it.album.ifBlank { "Unknown Album" } }
            .map { (albumTitle, albumSongs) ->
                val primaryArtist = albumSongs.firstOrNull()?.displayArtist ?: "Unknown Artist"
                val artwork = albumSongs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                val totalDuration = albumSongs.sumOf { it.durationMs }
                val year = albumSongs.firstOrNull { it.year != null }?.year
                AlbumItem(
                    title = albumTitle,
                    artist = primaryArtist,
                    artworkUri = artwork,
                    songCount = albumSongs.size,
                    totalDurationMs = totalDuration,
                    year = year,
                    songs = albumSongs.sortedBy { it.trackNumber ?: 0 }
                )
            }.sortedBy { it.title.lowercase() }
    }

    val allArtists: Flow<List<ArtistItem>> = allSongs.map { songs ->
        songs.groupBy { it.displayArtist }
            .map { (artistName, artistSongs) ->
                val artwork = artistSongs.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                val albumCount = artistSongs.map { it.album }.distinct().size
                ArtistItem(
                    name = artistName,
                    artworkUri = artwork,
                    songCount = artistSongs.size,
                    albumCount = albumCount,
                    songs = artistSongs.sortedBy { it.title.lowercase() }
                )
            }.sortedBy { it.name.lowercase() }
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> =
        playlistDao.getSongsForPlaylist(playlistId)

    fun searchSongs(query: String): Flow<List<Song>> =
        songDao.searchSongs(query)

    suspend fun toggleFavorite(song: Song) = withContext(Dispatchers.IO) {
        // Read the REAL value from the database: the Song object passed in can be an old copy
        // (e.g. from the queue), which is why favorites seemed to do nothing.
        val current = songDao.getSongByIdSync(song.id)?.isFavorite ?: song.isFavorite
        songDao.updateFavorite(song.id, !current)
    }

    suspend fun recordPlay(song: Song) = withContext(Dispatchers.IO) {
        songDao.recordPlay(song.id, System.currentTimeMillis())
    }

    suspend fun updateMetadata(songId: Long, title: String, artist: String, album: String, year: Int?) =
        withContext(Dispatchers.IO) {
            songDao.updateMetadata(songId, title, artist, album, year)
        }

    suspend fun updateLyrics(songId: Long, lyrics: String?) = withContext(Dispatchers.IO) {
        songDao.updateLyrics(songId, lyrics)
    }

    suspend fun updateSongArtwork(songId: Long, artworkUri: String?) = withContext(Dispatchers.IO) {
        songDao.updateArtwork(songId, artworkUri)
    }

    suspend fun updateAlbumArtwork(album: String, artworkUri: String?) = withContext(Dispatchers.IO) {
        songDao.updateAlbumArtwork(album, artworkUri)
    }

    suspend fun importAudioUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0
        val base = System.currentTimeMillis()
        uris.forEachIndexed { index, uri ->
            val song = importer.importAudioUri(uri)
            if (song != null) {
                // Newest batch on top, and inside a batch the first picked song stays first
                songDao.insertSong(song.copy(addedTimestamp = base + (uris.size - index)))
                count++
            }
        }
        count
    }

    suspend fun scanDeviceMusic(): Int = withContext(Dispatchers.IO) {
        val scanned = importer.scanDeviceMediaStore()
        if (scanned.isNotEmpty()) {
            val existing = songDao.getAllSongs().first()
            val existingByUri = existing.associateBy { it.uriString }
            val existingUris = existingByUri.keys
            val newSongs = scanned.filter { it.uriString !in existingUris }
            // Fix the date of songs that were scanned before with the wrong "now" time
            scanned.forEach { sc ->
                val old = existingByUri[sc.uriString]
                if (old != null && old.addedTimestamp != sc.addedTimestamp) {
                    songDao.updateAddedTimestamp(old.id, sc.addedTimestamp)
                }
            }
            if (newSongs.isNotEmpty()) {
                songDao.insertSongs(newSongs)
            }
        }
        scanned.size
    }

    suspend fun saveCustomArtwork(imageUri: Uri): String? = withContext(Dispatchers.IO) {
        importer.saveCustomArtwork(imageUri)
    }

    suspend fun readLyricsFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        importer.readLyricsFromUri(uri)
    }

    suspend fun createPlaylist(name: String, description: String? = null): Long =
        withContext(Dispatchers.IO) {
            playlistDao.insertPlaylist(Playlist(name = name, description = description))
        }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        val maxOrder = playlistDao.getMaxSortOrder(playlistId) ?: -1
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                sortOrder = maxOrder + 1
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.deleteSong(song)
        // Only delete files the app imported itself (inside app storage). Songs found on the
        // phone's storage are just removed from the library, never erased.
        if (song.filePath != null) {
            try {
                val file = File(song.filePath)
                if (file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    file.delete()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Initializes beautiful offline starter tracks if the library is empty.
     */
    suspend fun ensureDemoTracksIfEmpty() = withContext(Dispatchers.IO) {
        val existing = songDao.getAllSongs().first()
        if (existing.isNotEmpty()) return@withContext

        val demoDir = File(context.filesDir, "demo_tracks").apply { mkdirs() }
        val artworkDir = File(context.filesDir, "artwork").apply { mkdirs() }

        // Track 1: Neon Horizon (Synthwave)
        val file1 = File(demoDir, "neon_horizon.wav")
        WavSynthesizer.createSampleWav(
            file = file1,
            durationSeconds = 42,
            baseFrequencies = listOf(220.0, 261.63, 196.0, 164.81), // A3, C4, G3, E3
            tempoBpm = 110
        )
        val artFile1 = File(artworkDir, "neon_horizon_art.png")
        WavSynthesizer.createSampleArtwork(
            file = artFile1,
            title = "Neon Horizon",
            gradientColors = intArrayOf(
                android.graphics.Color.parseColor("#FA2D48"),
                android.graphics.Color.parseColor("#7928CA"),
                android.graphics.Color.parseColor("#00172D")
            )
        )
        val lyrics1 = """
            [00:00.00]Neon lights across the skyline
            [00:05.00]Driving through the midnight air
            [00:10.50]Feel the rhythm in the bassline
            [00:15.50]Electric memories everywhere
            [00:21.00]Fade into the velvet sunset
            [00:26.50]Every moment shining bright
            [00:31.50]Lost inside this synthwave daydream
            [00:37.00]Dancing under neon light
        """.trimIndent()

        // Track 2: Acoustic Sunset (Warm acoustic/folk)
        val file2 = File(demoDir, "acoustic_sunset.wav")
        WavSynthesizer.createSampleWav(
            file = file2,
            durationSeconds = 38,
            baseFrequencies = listOf(261.63, 329.63, 220.0, 174.61), // C4, E4, A3, F3
            tempoBpm = 92
        )
        val artFile2 = File(artworkDir, "acoustic_sunset_art.png")
        WavSynthesizer.createSampleArtwork(
            file = artFile2,
            title = "Acoustic Sunset",
            gradientColors = intArrayOf(
                android.graphics.Color.parseColor("#FF512F"),
                android.graphics.Color.parseColor("#F09819"),
                android.graphics.Color.parseColor("#7B1FA2")
            )
        )
        val lyrics2 = """
            [00:00.00]Gentle breeze upon the ocean
            [00:05.50]Golden rays upon the shore
            [00:11.00]Every chord a sweet devotion
            [00:16.50]Finding peace forevermore
            [00:22.00]Listen to the acoustic whispers
            [00:28.00]As the evening turns to gold
            [00:33.50]Stories of the summer sunset
        """.trimIndent()

        // Track 3: Lo-Fi Reverie (Relaxing chillhop)
        val file3 = File(demoDir, "lofi_reverie.wav")
        WavSynthesizer.createSampleWav(
            file = file3,
            durationSeconds = 45,
            baseFrequencies = listOf(174.61, 220.0, 261.63, 196.0), // F3, A3, C4, G3
            tempoBpm = 82
        )
        val artFile3 = File(artworkDir, "lofi_reverie_art.png")
        WavSynthesizer.createSampleArtwork(
            file = artFile3,
            title = "Lo-Fi Reverie",
            gradientColors = intArrayOf(
                android.graphics.Color.parseColor("#4A00E0"),
                android.graphics.Color.parseColor("#8E2DE2"),
                android.graphics.Color.parseColor("#141E30")
            )
        )
        val lyrics3 = """
            [00:00.00]Raindrops tapping on the windowpane
            [00:06.00]Warm coffee and relaxing haze
            [00:12.00]Soft melodies wash away the rain
            [00:18.00]Lost in mellow quiet days
            [00:24.00]Lo-fi beats drifting softly
            [00:30.00]Shadows dancing on the floor
            [00:36.00]Peaceful rhythms guide you home
        """.trimIndent()

        val sampleSongs = listOf(
            Song(
                title = "Neon Horizon",
                artist = "Aura Soundscape",
                album = "Retro Dreams",
                durationMs = 42000L,
                uriString = Uri.fromFile(file1).toString(),
                filePath = file1.absolutePath,
                artworkUri = artFile1.absolutePath,
                lyrics = lyrics1,
                year = 2024,
                trackNumber = 1,
                isFavorite = true,
                fileSize = file1.length()
            ),
            Song(
                title = "Acoustic Sunset",
                artist = "Sierra Bloom",
                album = "Golden Hour",
                durationMs = 38000L,
                uriString = Uri.fromFile(file2).toString(),
                filePath = file2.absolutePath,
                artworkUri = artFile2.absolutePath,
                lyrics = lyrics2,
                year = 2024,
                trackNumber = 2,
                isFavorite = false,
                fileSize = file2.length()
            ),
            Song(
                title = "Lo-Fi Reverie",
                artist = "Kairo Pulse",
                album = "Midnight Sessions",
                durationMs = 45000L,
                uriString = Uri.fromFile(file3).toString(),
                filePath = file3.absolutePath,
                artworkUri = artFile3.absolutePath,
                lyrics = lyrics3,
                year = 2024,
                trackNumber = 3,
                isFavorite = true,
                fileSize = file3.length()
            )
        )

        songDao.insertSongs(sampleSongs)

        // Create initial default playlist
        val playlistId = playlistDao.insertPlaylist(
            Playlist(name = "Chill Vibes", description = "Smooth transitions and relaxing melodies")
        )
        sampleSongs.forEachIndexed { index, song ->
            val insertedSong = songDao.getSongByUri(song.uriString)
            if (insertedSong != null) {
                playlistDao.addSongToPlaylist(
                    PlaylistSongCrossRef(playlistId = playlistId, songId = insertedSong.id, sortOrder = index)
                )
            }
        }
    }
}
