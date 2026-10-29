package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ImageStorageManager(private val context: Context) {

    private val imagesDir: File by lazy {
        File(context.filesDir, "drink_images").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun saveCroppedBitmap(bitmap: Bitmap, prefix: String = "drink"): String = withContext(Dispatchers.IO) {
        val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
        val destFile = File(imagesDir, fileName)

        // Scale to maximum 512x512 to keep storage lightweight and fast
        val maxDim = 512
        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        FileOutputStream(destFile).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }

        destFile.absolutePath
    }

    suspend fun deleteImage(filePath: String?) = withContext(Dispatchers.IO) {
        if (filePath.isNullOrBlank()) return@withContext
        try {
            val file = File(filePath)
            if (file.exists() && file.parentFile?.absolutePath == imagesDir.absolutePath) {
                file.delete()
            }
        } catch (_: Exception) {
            // Ignore deletion failures
        }
    }

    suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                // Decode bounds first to prevent OutOfMemory
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)

                val maxDimension = 1200
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                // Decode with sample size
                context.contentResolver.openInputStream(uri)?.use { secondStream ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeStream(secondStream, null, decodeOptions)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun transformBitmap(
        source: Bitmap,
        rotationDegrees: Float,
        cropRectNorm: CropRectNormalized
    ): Bitmap {
        var bmp = source
        if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }

        val left = (cropRectNorm.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val top = (cropRectNorm.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val width = (cropRectNorm.width * bmp.width).toInt().coerceIn(1, bmp.width - left)
        val height = (cropRectNorm.height * bmp.height).toInt().coerceIn(1, bmp.height - top)

        return Bitmap.createBitmap(bmp, left, top, width, height)
    }
}

data class CropRectNormalized(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)
