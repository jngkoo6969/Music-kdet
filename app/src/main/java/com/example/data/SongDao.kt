package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE lastPlayedTimestamp IS NOT NULL ORDER BY lastPlayedTimestamp DESC LIMIT 50")
    fun getRecentlyPlayedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY addedTimestamp DESC LIMIT 50")
    fun getRecentlyAddedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 50")
    fun getMostPlayedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun getSongById(id: Long): Flow<Song?>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongByIdSync(id: Long): Song?

    @Query("SELECT * FROM songs WHERE uriString = :uri LIMIT 1")
    suspend fun getSongByUri(uri: String): Song?

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC, title COLLATE NOCASE ASC")
    fun getSongsByAlbum(album: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR lyrics LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchSongs(query: String): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>): List<Long>

    @Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: Long)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET addedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateAddedTimestamp(id: Long, timestamp: Long)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE id = :id")
    suspend fun recordPlay(id: Long, timestamp: Long)

    @Query("UPDATE songs SET lyrics = :lyrics WHERE id = :id")
    suspend fun updateLyrics(id: Long, lyrics: String?)

    @Query("UPDATE songs SET artworkUri = :artworkUri WHERE id = :id")
    suspend fun updateArtwork(id: Long, artworkUri: String?)

    @Query("UPDATE songs SET artworkUri = :artworkUri WHERE album = :album OR (:album = 'Unknown Album' AND TRIM(album) = '')")
    suspend fun updateAlbumArtwork(album: String, artworkUri: String?)

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album, year = :year WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, artist: String, album: String, year: Int?)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()
}
