package com.example.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Everything the listener can tune about the SOUND (not the transitions).
 *
 *  bands       : 5 gains in dB (-12..+12) at 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz.
 *                They are mapped onto however many bands the phone's equalizer really has.
 *  bass        : 0..100, an extra low shelf (clean, works on every phone, unlike the hardware "bass boost")
 *  stereoWidth : 0..100, Android's Virtualizer (mostly audible on headphones)
 *  leveling    : volume leveling - every song is brought to a similar loudness (also during Automix)
 */
data class SoundSettings(
    val eqEnabled: Boolean = true,
    val preset: String = "Flat",
    val bands: List<Float> = List(5) { 0f },
    val bass: Int = 0,
    val stereoWidth: Int = 0,
    val leveling: Boolean = true
)

object EqPresets {
    val bandHz = listOf(60, 230, 910, 3600, 14000)
    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    val all: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Warm" to listOf(2.5f, 1.5f, 0f, -1f, -2.5f),
        "Bass Boost" to listOf(6f, 4f, 0.5f, 0f, 0f),
        "Bass Reducer" to listOf(-5f, -3f, 0f, 0f, 0f),
        "Vocal" to listOf(-2f, -1f, 2.5f, 3f, 1f),
        "Treble Boost" to listOf(0f, 0f, 0f, 3f, 5f),
        "Rock" to listOf(4f, 2f, -1.5f, 2.5f, 4f),
        "Pop" to listOf(-1f, 2f, 3.5f, 1.5f, -1f),
        "Electronic" to listOf(5f, 2.5f, -0.5f, 2f, 4.5f),
        "Hip-Hop" to listOf(5f, 3.5f, 0f, 1f, 2f),
        "Acoustic" to listOf(3f, 2f, 1f, 2.5f, 3f),
        "Classical" to listOf(3f, 1.5f, -1f, 2f, 3f),
        "Low Volume" to listOf(4.5f, 2f, 0f, 1.5f, 3.5f)
    )

    /** Name of the preset that matches these gains, or "Custom". */
    fun nameFor(bands: List<Float>): String =
        all.entries.firstOrNull { (_, v) -> v.indices.all { abs(v[it] - bands.getOrElse(it) { 0f }) < 0.05f } }?.key
            ?: "Custom"
}

/**
 * One Equalizer + Virtualizer + LoudnessEnhancer per MediaPlayer (each player has its own audio session),
 * so that during an Automix blend the two songs can be shaped independently:
 *
 *  - "bass swap": the incoming song enters with its low end cut and gets it back at the middle of the blend
 *    while the outgoing song loses its low end - the way a DJ avoids two kick drums / basslines at once.
 *  - level matching: every song carries its own gain, so a quiet song doesn't get run over by a loud one.
 *
 * All calls are made from the main thread. Every effect is optional: phones that lack one simply skip it.
 */
class AudioEffectsManager(context: Context) {

    private val tag = "AudioEffects"
    private val prefs = context.getSharedPreferences("sound_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()

    private class Chain(val eq: Equalizer?, val virt: Virtualizer?, val enhancer: LoudnessEnhancer?)

    private val chains = arrayOfNulls<Chain>(2)
    private val bassCut = floatArrayOf(0f, 0f)
    private val trackGainDb = floatArrayOf(0f, 0f)

    /** Bumped whenever a player's volume scale changed, so the engine knows to re-apply the volume. */
    @Volatile
    var scaleVersion = 0
        private set

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    fun attach(index: Int, audioSessionId: Int) {
        release(index)
        if (audioSessionId == 0) return
        val eq = try { Equalizer(0, audioSessionId).also { it.enabled = true } } catch (e: Throwable) { log("Equalizer", e); null }
        val virt = try { Virtualizer(0, audioSessionId) } catch (e: Throwable) { log("Virtualizer", e); null }
        val enh = try { LoudnessEnhancer(audioSessionId) } catch (e: Throwable) { log("LoudnessEnhancer", e); null }
        chains[index] = Chain(eq, virt, enh)
        applyAll(index)
    }

    private fun log(name: String, e: Throwable) = Log.w(tag, "$name not available: ${e.message}")

    private fun release(index: Int) {
        chains[index]?.let {
            try { it.eq?.release() } catch (e: Throwable) { }
            try { it.virt?.release() } catch (e: Throwable) { }
            try { it.enhancer?.release() } catch (e: Throwable) { }
        }
        chains[index] = null
    }

    fun release() {
        release(0)
        release(1)
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    fun setSettings(s: SoundSettings) {
        val fixed = s.copy(
            bands = List(5) { s.bands.getOrElse(it) { 0f }.coerceIn(-12f, 12f) },
            bass = s.bass.coerceIn(0, 100),
            stereoWidth = s.stereoWidth.coerceIn(0, 100)
        )
        _settings.value = fixed
        save(fixed)
        applyAll(0)
        applyAll(1)
        scaleVersion++
    }

    private fun load(): SoundSettings {
        val bands = (prefs.getString("bands", null) ?: "0,0,0,0,0").split(",")
            .mapNotNull { it.toFloatOrNull() }.let { l -> List(5) { l.getOrElse(it) { 0f } } }
        return SoundSettings(
            eqEnabled = prefs.getBoolean("eq_enabled", true),
            preset = EqPresets.nameFor(bands),
            bands = bands,
            bass = prefs.getInt("bass", 0),
            stereoWidth = prefs.getInt("width", 0),
            leveling = prefs.getBoolean("leveling", true)
        )
    }

    private fun save(s: SoundSettings) {
        prefs.edit()
            .putBoolean("eq_enabled", s.eqEnabled)
            .putString("bands", s.bands.joinToString(","))
            .putInt("bass", s.bass)
            .putInt("width", s.stereoWidth)
            .putBoolean("leveling", s.leveling)
            .apply()
    }

    // ------------------------------------------------------------------
    // Applying to the native effects
    // ------------------------------------------------------------------

    private fun applyAll(index: Int) {
        applyEq(index)
        applyVirtualizer(index)
        applyEnhancer(index)
    }

    /** Gains (dB) of the 5 logical bands including the extra bass shelf. */
    private fun logicalGains(): FloatArray {
        val s = _settings.value
        val g = FloatArray(5)
        if (s.eqEnabled) for (i in 0..4) g[i] = s.bands.getOrElse(i) { 0f }
        g[0] += s.bass * 0.07f
        g[1] += s.bass * 0.035f
        return g
    }

    private fun gainAt(g: FloatArray, hz: Double): Float {
        val f = EqPresets.bandHz
        if (hz <= f.first()) return g.first()
        if (hz >= f.last()) return g.last()
        for (i in 0 until f.size - 1) {
            if (hz <= f[i + 1]) {
                val t = (ln(hz) - ln(f[i].toDouble())) / (ln(f[i + 1].toDouble()) - ln(f[i].toDouble()))
                return (g[i] + (g[i + 1] - g[i]) * t).toFloat()
            }
        }
        return g.last()
    }

    private fun applyEq(index: Int) {
        val eq = chains[index]?.eq ?: return
        try {
            val range = eq.bandLevelRange
            val lo = range[0].toInt()
            val hi = range[1].toInt()
            val g = logicalGains()
            val cut = bassCut[index]
            for (b in 0 until eq.numberOfBands.toInt()) {
                val hz = eq.getCenterFreq(b.toShort()) / 1000.0
                var level = gainAt(g, hz) * 100f
                val weight = when {
                    hz <= 120.0 -> 1f
                    hz <= 450.0 -> 0.5f
                    else -> 0f
                }
                level += (lo - level) * cut * weight
                eq.setBandLevel(b.toShort(), level.roundToInt().coerceIn(lo, hi).toShort())
            }
        } catch (e: Throwable) {
            Log.w(tag, "Could not set equalizer: ${e.message}")
        }
    }

    private fun applyVirtualizer(index: Int) {
        val v = chains[index]?.virt ?: return
        try {
            val width = _settings.value.stereoWidth
            if (width > 0 && v.strengthSupported) {
                v.setStrength((width * 10).coerceIn(0, 1000).toShort())
                v.enabled = true
            } else {
                v.enabled = false
            }
        } catch (e: Throwable) {
            Log.w(tag, "Could not set stereo width: ${e.message}")
        }
    }

    private fun applyEnhancer(index: Int) {
        val e = chains[index]?.enhancer ?: return
        try {
            val boostMb = (max(0f, trackGainDb[index]) * 100f).roundToInt()
            e.setTargetGain(boostMb)
            e.enabled = boostMb > 0
        } catch (ex: Throwable) {
            Log.w(tag, "Could not set loudness gain: ${ex.message}")
        }
    }

    // ------------------------------------------------------------------
    // Automix hooks
    // ------------------------------------------------------------------

    /**
     * 0 = normal low end, 1 = low end fully removed (bass swap during a blend).
     * Only touches the native equalizer when the value moved noticeably.
     */
    fun setBassCut(index: Int, cut: Float) {
        val c = cut.coerceIn(0f, 1f)
        val old = bassCut[index]
        if (abs(c - old) < 0.03f && c != 0f && c != 1f) return
        if (c == old) return
        bassCut[index] = c
        applyEq(index)
    }

    fun clearBassCuts() {
        setBassCut(0, 0f)
        setBassCut(1, 0f)
    }

    /**
     * Gives one player the level correction for the song it now holds.
     * loudnessDb = measured RMS level of the song in dBFS (null = not analyzed yet -> no correction).
     */
    fun setTrackLoudness(index: Int, loudnessDb: Double?) {
        val g = if (!_settings.value.leveling || loudnessDb == null) 0f
        else (TARGET_LOUDNESS_DB - loudnessDb).toFloat().coerceIn(-10f, 4f)
        if (abs(g - trackGainDb[index]) > 0.05f) {
            trackGainDb[index] = g
            applyEnhancer(index)
            scaleVersion++
        }
    }

    /** Extra headroom so EQ boosts don't clip: roughly 70 % of the largest boost, at most 9 dB. */
    private fun headroomDb(): Float = min(9f, max(0f, (logicalGains().maxOrNull() ?: 0f)) * 0.7f)

    /**
     * Linear factor applied on top of MediaPlayer.setVolume():
     * attenuation for songs louder than the target + headroom for the equalizer boost.
     * (Songs quieter than the target are lifted by the LoudnessEnhancer instead.)
     */
    fun volumeScale(index: Int): Float {
        val db = min(0f, trackGainDb[index]) - headroomDb()
        return 10f.pow(db / 20f)
    }

    companion object {
        /** Typical modern music measures around -14 .. -9 dBFS RMS; this is a comfortable middle. */
        const val TARGET_LOUDNESS_DB = -12.0
    }
}
