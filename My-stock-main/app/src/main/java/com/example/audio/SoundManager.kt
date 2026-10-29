package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.model.ShopSettings
import com.example.data.model.SoundEvent
import com.example.data.model.SoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundManager(private val context: Context) {

    private val sampleRate = 22050
    private val scope = CoroutineScope(Dispatchers.Default)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun playSoundForEvent(event: SoundEvent, settings: ShopSettings) {
        if (!settings.masterSoundEnabled || settings.masterVolume <= 0f) return

        val config = settings.soundEvents[event] ?: return
        if (!config.enabled || config.volume <= 0f) return

        val effectiveVolume = (config.volume * settings.masterVolume).coerceIn(0f, 2.0f)
        playSample(config.soundType, effectiveVolume)

        if (settings.vibrationEnabled) {
            triggerHaptic(event)
        }
    }

    fun previewSound(soundType: SoundType, volume: Float, settings: ShopSettings) {
        if (!settings.masterSoundEnabled || settings.masterVolume <= 0f) return
        val effectiveVolume = (volume * settings.masterVolume).coerceIn(0f, 2.0f)
        playSample(soundType, effectiveVolume)
    }

    fun triggerHaptic(event: SoundEvent) {
        try {
            vibrator?.let { vib ->
                if (!vib.hasVibrator()) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = when (event) {
                        SoundEvent.ORDER_PAID -> VibrationEffect.createWaveform(
                            longArrayOf(0, 50, 40, 80),
                            intArrayOf(0, 180, 0, 255),
                            -1
                        )
                        SoundEvent.ERROR -> VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                        SoundEvent.ADD_DRINK -> VibrationEffect.createOneShot(25, 120)
                        SoundEvent.REMOVE_DRINK -> VibrationEffect.createOneShot(20, 80)
                        SoundEvent.NAV_TAP -> VibrationEffect.createOneShot(15, 60)
                    }
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(if (event == SoundEvent.ORDER_PAID) 100 else 30)
                }
            }
        } catch (_: Exception) {
            // Ignore vibration exceptions on unsupported hardware
        }
    }

    private fun playSample(soundType: SoundType, volumeScale: Float) {
        scope.launch {
            try {
                val pcmData = generateSoundPcm(soundType, volumeScale)
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, pcmData.size * 2)

                val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                        bufferSize,
                        AudioTrack.MODE_STATIC,
                        android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                }

                audioTrack.write(pcmData, 0, pcmData.size)
                audioTrack.play()
                // Wait until playback ends then release
                val durationMs = (pcmData.size.toDouble() / sampleRate * 1000).toLong() + 50
                kotlinx.coroutines.delay(durationMs)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                android.util.Log.e("SoundManager", "Sound playback failed", e)
            }
        }
    }

    private fun generateSoundPcm(soundType: SoundType, volumeScale: Float): ShortArray {
        val gain = (volumeScale.coerceIn(0.05f, 2.0f) * 22000).toInt()
        return when (soundType) {
            SoundType.POP -> {
                // Short pitch drop 550Hz -> 180Hz over 50ms
                val duration = (sampleRate * 0.055).toInt()
                val samples = ShortArray(duration)
                var phase = 0.0
                for (i in 0 until duration) {
                    val t = i.toDouble() / duration
                    val freq = 550.0 - 370.0 * t
                    phase += 2 * PI * freq / sampleRate
                    val envelope = (1.0 - t) * exp(-2.5 * t)
                    samples[i] = (sin(phase) * envelope * gain).toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
            SoundType.CLICK -> {
                // Snappy 18ms transient
                val duration = (sampleRate * 0.02).toInt()
                val samples = ShortArray(duration)
                var phase = 0.0
                for (i in 0 until duration) {
                    val t = i.toDouble() / duration
                    phase += 2 * PI * 1400.0 / sampleRate
                    val envelope = exp(-15.0 * t)
                    samples[i] = (sin(phase) * envelope * gain).toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
            SoundType.BUBBLE -> {
                // Ascending sweep 320Hz -> 780Hz with resonant body
                val duration = (sampleRate * 0.09).toInt()
                val samples = ShortArray(duration)
                var phase = 0.0
                for (i in 0 until duration) {
                    val t = i.toDouble() / duration
                    val freq = 320.0 + 460.0 * (t * t)
                    phase += 2 * PI * freq / sampleRate
                    val envelope = sin(PI * t) * (1.0 - 0.3 * t)
                    samples[i] = (sin(phase) * envelope * gain).toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
            SoundType.COIN -> {
                // Two tones: B5 (987Hz) for 60ms, then E6 (1318Hz) for 160ms
                val duration1 = (sampleRate * 0.06).toInt()
                val duration2 = (sampleRate * 0.16).toInt()
                val samples = ShortArray(duration1 + duration2)
                var phase = 0.0
                for (i in 0 until duration1) {
                    phase += 2 * PI * 987.77 / sampleRate
                    val env = 1.0 - (i.toDouble() / duration1) * 0.2
                    samples[i] = (sin(phase) * env * gain * 0.9).toInt().coerceIn(-32767, 32767).toShort()
                }
                phase = 0.0
                for (i in 0 until duration2) {
                    val t = i.toDouble() / duration2
                    phase += 2 * PI * 1318.51 / sampleRate
                    val env = exp(-3.5 * t)
                    samples[duration1 + i] = (sin(phase) * env * gain).toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
            SoundType.CHIME -> {
                // Triad chord (C6 1046Hz + E6 1318Hz + G6 1568Hz) ring
                val duration = (sampleRate * 0.35).toInt()
                val samples = ShortArray(duration)
                var p1 = 0.0
                var p2 = 0.0
                var p3 = 0.0
                for (i in 0 until duration) {
                    val t = i.toDouble() / duration
                    p1 += 2 * PI * 1046.5 / sampleRate
                    p2 += 2 * PI * 1318.5 / sampleRate
                    p3 += 2 * PI * 1567.98 / sampleRate
                    val env = exp(-3.0 * t)
                    val s = (sin(p1) * 0.4 + sin(p2) * 0.35 + sin(p3) * 0.25) * env * gain
                    samples[i] = s.toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
            SoundType.BELL -> {
                // Bell resonance (880Hz + 1760Hz harmonic)
                val duration = (sampleRate * 0.5).toInt()
                val samples = ShortArray(duration)
                var p1 = 0.0
                var p2 = 0.0
                for (i in 0 until duration) {
                    val t = i.toDouble() / duration
                    p1 += 2 * PI * 880.0 / sampleRate
                    p2 += 2 * PI * 1760.0 / sampleRate
                    val env = exp(-2.8 * t)
                    val s = (sin(p1) * 0.7 + sin(p2) * 0.3) * env * gain
                    samples[i] = s.toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
            SoundType.TADA -> {
                // Fanfare: G5 (784Hz), C6 (1046Hz), E6 (1318Hz), G6 (1568Hz sustained)
                val n1 = (sampleRate * 0.08).toInt()
                val n2 = (sampleRate * 0.08).toInt()
                val n3 = (sampleRate * 0.08).toInt()
                val n4 = (sampleRate * 0.26).toInt()
                val total = n1 + n2 + n3 + n4
                val samples = ShortArray(total)

                var phase = 0.0
                fun fillNote(start: Int, len: Int, freq: Double, sustain: Boolean) {
                    phase = 0.0
                    for (i in 0 until len) {
                        val t = i.toDouble() / len
                        phase += 2 * PI * freq / sampleRate
                        val env = if (sustain) exp(-2.5 * t) else (1.0 - 0.2 * t)
                        samples[start + i] = (sin(phase) * env * gain).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }
                fillNote(0, n1, 784.0, false)
                fillNote(n1, n2, 1046.5, false)
                fillNote(n1 + n2, n3, 1318.5, false)
                fillNote(n1 + n2 + n3, n4, 1568.0, true)
                samples
            }
            SoundType.LOW_POP -> {
                // Deeper pop 260Hz -> 85Hz in 65ms
                val duration = (sampleRate * 0.065).toInt()
                val samples = ShortArray(duration)
                var phase = 0.0
                for (i in 0 until duration) {
                    val t = i.toDouble() / duration
                    val freq = 260.0 - 175.0 * t
                    phase += 2 * PI * freq / sampleRate
                    val env = (1.0 - t) * exp(-2.0 * t)
                    samples[i] = (sin(phase) * env * gain).toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
            SoundType.BEEP -> {
                val duration = (sampleRate * 0.1).toInt()
                val samples = ShortArray(duration)
                var phase = 0.0
                for (i in 0 until duration) {
                    val t = i.toDouble() / duration
                    phase += 2 * PI * 880.0 / sampleRate
                    val env = if (t < 0.05) t / 0.05 else (1.0 - t)
                    samples[i] = (sin(phase) * env * gain * 0.85).toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
            SoundType.BUZZ -> {
                // Error buzz: low dissonant dual frequencies 150Hz + 158Hz with square edge
                val duration = (sampleRate * 0.18).toInt()
                val samples = ShortArray(duration)
                var p1 = 0.0
                var p2 = 0.0
                for (i in 0 until duration) {
                    val t = i.toDouble() / duration
                    p1 += 2 * PI * 150.0 / sampleRate
                    p2 += 2 * PI * 158.0 / sampleRate
                    val wave = if (sin(p1) > 0) 0.5 else -0.5
                    val beat = sin(p2) * 0.3
                    val env = (1.0 - 0.2 * t) * (if (t < 0.05) t / 0.05 else 1.0)
                    val s = (wave + beat) * env * gain * 0.8
                    samples[i] = s.toInt().coerceIn(-32767, 32767).toShort()
                }
                samples
            }
        }
    }
}
 
