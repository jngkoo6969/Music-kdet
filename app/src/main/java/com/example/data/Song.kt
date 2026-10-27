package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [Index(value = ["uriString"])]
)
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val uriString: String,
    val filePath: String? = null,
    val artworkUri: String? = null,
    val lyrics: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0L,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    /** Artist name with a friendly fallback when blank. */
    val displayArtist: String
        get() = artist.ifBlank { "Unknown Artist" }

    /** Album name with a friendly fallback when blank. */
    val displayAlbum: String
        get() = album.ifBlank { "Unknown Album" }
}
