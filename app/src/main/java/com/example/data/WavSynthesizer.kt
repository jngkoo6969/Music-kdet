package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object WavSynthesizer {

    /**
     * Synthesizes a pleasant melodic PCM 16-bit stereo WAV file with chord progressions.
     */
    fun createSampleWav(
        file: File,
        durationSeconds: Int = 40,
        baseFrequencies: List<Double>,
        tempoBpm: Int = 100
    ) {
        if (file.exists() && file.length() > 0) return

        val sampleRate = 44100
        val numChannels = 2
        val bitsPerSample = 16
        val totalSamples = sampleRate * durationSeconds
        val bytesPerSample = (bitsPerSample / 8) * numChannels
        val dataSize = totalSamples * bytesPerSample
        val totalFileSize = 36 + dataSize

        FileOutputStream(file).use { fos ->
            // RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToByteArray(totalFileSize))
            fos.write("WAVE".toByteArray())

            // fmt sub-chunk
            fos.write("fmt ".toByteArray())
            fos.write(intToByteArray(16)) // subchunk size
            fos.write(shortToByteArray(1)) // AudioFormat (1 = PCM)
            fos.write(shortToByteArray(numChannels.toShort()))
            fos.write(intToByteArray(sampleRate))
            fos.write(intToByteArray(sampleRate * bytesPerSample)) // byteRate
            fos.write(shortToByteArray(bytesPerSample.toShort())) // blockAlign
            fos.write(shortToByteArray(bitsPerSample.toShort()))

            // data sub-chunk
            fos.write("data".toByteArray())
            fos.write(intToByteArray(dataSize))

            val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            val beatSamples = (60.0 / tempoBpm * sampleRate).toInt()

            for (i in 0 until totalSamples) {
                val time = i.toDouble() / sampleRate
                val beatIndex = (i / beatSamples) % baseFrequencies.size
                val chordBase = baseFrequencies[beatIndex]

                // Chord notes: Root, Minor/Major Third, Fifth, Octave
                val f1 = chordBase
                val f2 = chordBase * 1.25 // Major third
                val f3 = chordBase * 1.5 // Fifth
                val f4 = chordBase * 2.0 // Octave

                // Envelope per beat
                val beatProgress = (i % beatSamples).toDouble() / beatSamples
                val envelope = (1.0 - beatProgress * 0.7) * (1.0 - Math.exp(-beatProgress * 20.0))

                // Left channel: warm synth chord
                val waveL = (
                    sin(2.0 * PI * f1 * time) * 0.4 +
                    sin(2.0 * PI * f2 * time) * 0.3 +
                    sin(2.0 * PI * f3 * time) * 0.2 +
                    sin(2.0 * PI * (f1 * 0.5) * time) * 0.3 // sub bass
                ) * envelope

                // Right channel: slight chorus detune and melody
                val waveR = (
                    sin(2.0 * PI * (f1 * 1.003) * time) * 0.4 +
                    sin(2.0 * PI * (f2 * 0.998) * time) * 0.3 +
                    sin(2.0 * PI * (f4) * time) * 0.25 +
                    sin(2.0 * PI * (f1 * 0.5) * time) * 0.3
                ) * envelope

                // Gentle overall fade-in and fade-out at beginning and end
                val overallFade = when {
                    time < 1.0 -> time
                    time > (durationSeconds - 2.0) -> (durationSeconds - time) / 2.0
                    else -> 1.0
                }.coerceIn(0.0, 1.0)

                val sampleL = ((waveL * overallFade * 0.6).coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                val sampleR = ((waveR * overallFade * 0.6).coerceIn(-1.0, 1.0) * 32767).toInt().toShort()

                if (buffer.remaining() < 4) {
                    fos.write(buffer.array(), 0, buffer.position())
                    buffer.clear()
                }
                buffer.putShort(sampleL)
                buffer.putShort(sampleR)
            }

            if (buffer.position() > 0) {
                fos.write(buffer.array(), 0, buffer.position())
            }
        }
    }

    /**
     * Generates a sleek, Apple Music inspired album cover bitmap with gradients and typography.
     */
    fun createSampleArtwork(
        file: File,
        title: String,
        gradientColors: IntArray
    ) {
        if (file.exists() && file.length() > 0) return

        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient background
        val shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            gradientColors, null, Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            isAntiAlias = true
            this.shader = shader
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Inner glowing glass circle
        val circlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            alpha = 35
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawCircle(size / 2f, size / 2f, 180f, circlePaint)

        // Abstract vinyl groove rings
        circlePaint.strokeWidth = 3f
        circlePaint.alpha = 20
        canvas.drawCircle(size / 2f, size / 2f, 130f, circlePaint)
        canvas.drawCircle(size / 2f, size / 2f, 90f, circlePaint)

        // Center dot
        val centerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            alpha = 180
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 24f, centerPaint)

        // Save to file
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
}
