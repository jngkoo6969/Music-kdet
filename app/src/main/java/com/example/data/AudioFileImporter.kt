package com.example.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AudioFileImporter(private val context: Context) {
    private val tag = "AudioFileImporter"

    private val musicDir = File(context.filesDir, "music_library").apply { mkdirs() }
    private val artworkDir = File(context.filesDir, "artwork").apply { mkdirs() }

    /**
     * Imports an audio file from a Uri (picked from file picker).
     * Extracts ID3 tags and embedded artwork, copies file to app storage for 100% offline guarantee.
     */
    suspend fun importAudioUri(uri: Uri): Song? = withContext(Dispatchers.IO) {
        try {
            // Take persistable permission if available
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // If not supported by provider, proceed to copy
            }

            val displayName = queryDisplayName(uri) ?: "Audio_${System.currentTimeMillis()}"
            val extension = displayName.substringAfterLast('.', "mp3")
            val targetFile = File(musicDir, "${System.currentTimeMillis()}_${cleanFileName(displayName)}")

            // Copy to app private directory for persistent offline access
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                return@withContext null
            }

            // Extract metadata using MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(targetFile.absolutePath)
            } catch (e: Exception) {
                retriever.setDataSource(context, uri)
            }

            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            val title = if (!metaTitle.isNullOrBlank()) metaTitle else displayName.substringBeforeLast('.')
            val artist = if (!metaArtist.isNullOrBlank()) metaArtist else "Unknown Artist"
            val album = if (!metaAlbum.isNullOrBlank()) metaAlbum else "Unknown Album"
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val year = yearStr?.toIntOrNull()
            val trackNumber = trackStr?.split('/')?.firstOrNull()?.toIntOrNull()

            // Extract embedded artwork
            var artworkPath: String? = null
            val picBytes = retriever.embeddedPicture
            if (picBytes != null) {
                val artFile = File(artworkDir, "art_${System.currentTimeMillis()}.jpg")
                try {
                    FileOutputStream(artFile).use { out ->
                        out.write(picBytes)
                    }
                    artworkPath = artFile.absolutePath
                } catch (e: Exception) {
                    Log.e(tag, "Failed to save embedded artwork", e)
                }
            }

            retriever.release()

            Song(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                uriString = Uri.fromFile(targetFile).toString(),
                filePath = targetFile.absolutePath,
                artworkUri = artworkPath,
                year = year,
                trackNumber = trackNumber,
                mimeType = mimeType ?: "audio/$extension",
                fileSize = targetFile.length()
            )
        } catch (e: Exception) {
            Log.e(tag, "Error importing audio URI: $uri", e)
            null
        }
    }

    /**
     * Scans device MediaStore for audio files (MP3, M4A, FLAC, WAV, AAC, OGG).
     */
    suspend fun scanDeviceMediaStore(): List<Song> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val yearCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateAddedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val mediaId = it.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                    val title = it.getString(titleCol) ?: "Track $mediaId"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val duration = it.getLong(durationCol)
                    val albumId = it.getLong(albumIdCol)
                    val year = it.getInt(yearCol)
                    val size = it.getLong(sizeCol)
                    val mime = it.getString(mimeCol)
                    val filePath = if (dataCol != -1) it.getString(dataCol) else null
                    // Real date the file was added to the phone (seconds -> ms)
                    val addedMs = if (dateAddedCol != -1) it.getLong(dateAddedCol) * 1000L else System.currentTimeMillis()

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    result.add(
                        Song(
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            uriString = contentUri.toString(),
                            filePath = filePath,
                            artworkUri = albumArtUri,
                            year = if (year > 0) year else null,
                            mimeType = mime,
                            fileSize = size,
                            addedTimestamp = if (addedMs > 0) addedMs else System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed scanning media store", e)
        }
        result
    }

    /**
     * Saves a user-selected image from Photo Picker as persistent album artwork.
     */
    suspend fun saveCustomArtwork(imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val artFile = File(artworkDir, "custom_art_${System.currentTimeMillis()}.jpg")
            // Read the size first, then decode a downscaled copy (big photos are slow and can run out of memory)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            FileOutputStream(artFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
            artFile.absolutePath
        } catch (e: Exception) {
            Log.e(tag, "Failed to save custom artwork", e)
            null
        }
    }

    /**
     * Reads lyrics from a .lrc or .txt file Uri.
     */
    suspend fun readLyricsFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to read lyrics from Uri", e)
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun cleanFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
