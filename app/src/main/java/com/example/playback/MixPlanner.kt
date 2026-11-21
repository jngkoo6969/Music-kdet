package com.example.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One planned transition.
 *
 *  startAMs    : position in the OUTGOING song where the incoming song must start (on a beat / bar line)
 *  overlapMs   : length of the blend, measured on the outgoing song (a whole number of beats or bars)
 *  startBMs    : position in the INCOMING song where it begins (on one of its beats / bar lines)
 *  speedB      : playback speed given to the incoming song so its tempo equals the outgoing tempo
 *                (1.0 = untouched). The engine eases it back to 1.0 after the blend.
 *  beatMatched : false = plain equal-power crossfade (no usable beat info / tempos too different)
 */
data class MixPlan(
    val startAMs: Long,
    val overlapMs: Long,
    val startBMs: Long,
    val speedB: Float,
    val beatMatched: Boolean
)

object MixPlanner {

    /** Below this the beat grid is not trusted (random noise scores about 2). */
    private const val MIN_CONFIDENCE = 3.5

    /** Largest tempo change we are willing to apply to the incoming song during the blend (12 %). */
    private const val MAX_STRETCH = 0.12

    /** The incoming song may not start later than this, otherwise we'd skip too much of it. */
    private const val MAX_ENTRY_MS = 15_000.0

    /**
     * The engine starts looking for the blend CROSSFADE + PREROLL (2.5 s) before the music ends.
     * A bar-rounded blend may start a bit earlier than planned, but never earlier than this slack,
     * otherwise the engine would reach the start point before it has even prepared the next song.
     */
    private const val EARLY_SLACK_MS = 1_500.0

    /** When the tempos clash (can't be matched) a long blend sounds like mud, so keep it short. */
    private const val CLASH_FADE_MS = 3_000L

    /**
     * AUTO mode: finds the best blend length (4..20 s) for THIS pair of songs.
     *
     * For every candidate length the two songs are laid over each other exactly as the crossfade would play them
     * (end of the outgoing song against the start of the incoming one) and scored for crowding:
     *  - "voice vs voice": both songs strong in the voice band at the same moment
     *  - "dense vs dense" : both songs at full energy at the same moment
     * The middle of the blend counts most, because that is where both songs are loud.
     * The longest length that stays calm wins. So:
     *  - long fade-out / instrumental outro + slow build-up intro  -> long blend (up to 20 s)
     *  - one song bare while the other sings                       -> as long as the bare part lasts
     *  - two full-on songs                                         -> short blend (4 s, or 6 s when beat-matched)
     * Beat-matched pairs get a little more tolerance, since overlapping on the beat sounds intentional.
     * No analysis yet -> 8 s.
     */
    fun autoFadeMs(a: TrackAnalysis?, b: TrackAnalysis?): Long {
        val outro = a?.outroProfile
        val intro = b?.introProfile
        if (a == null || b == null || outro == null || intro == null) return 8_000L

        val matched = canBeatMatch(a, b)
        val tolerance = if (matched) 0.50 else 0.40
        var best = if (matched) 6 else 4
        for (len in 4..20) {
            if (clashScore(outro, intro, len) <= tolerance) best = max(best, len)
        }
        return best * 1000L
    }

    /** 0 = the two songs leave each other room, 1 = both are at full voice + energy the whole blend. */
    private fun clashScore(outro: EdgeProfile, intro: EdgeProfile, len: Int): Double {
        fun at(x: FloatArray, i: Int) = if (i in x.indices) min(1f, x[i]).toDouble() else 1.0
        var sum = 0.0
        var weights = 0.0
        for (i in 0 until len) {
            val u = len - 1 - i // second counted from the END of the outgoing song
            val v = i           // second counted from the START of the incoming song
            val x = (i + 0.5) / len
            val wOut = cos(PI * x / 2).let { it * it }
            val pairWeight = 4.0 * wOut * (1.0 - wOut) // peaks in the middle, where both are loud
            val voices = at(outro.voc, u) * at(intro.voc, v)
            val energy = at(outro.act, u) * at(intro.act, v)
            sum += pairWeight * (0.6 * voices + 0.4 * energy)
            weights += pairWeight
        }
        return if (weights > 0.0) sum / weights else 1.0
    }

    /** Can the engine line the two songs up on the beat (trusted grids, tempos within the stretch limit)? */
    private fun canBeatMatch(a: TrackAnalysis, b: TrackAnalysis): Boolean {
        val ta = a.tail ?: return false
        val hb = b.head ?: return false
        if (ta.confidence < MIN_CONFIDENCE || hb.confidence < MIN_CONFIDENCE) return false
        var bestDiff = Double.MAX_VALUE
        for (f in doubleArrayOf(0.5, 1.0, 2.0)) {
            bestDiff = min(bestDiff, abs(ln(hb.periodMs * f / ta.periodMs)))
        }
        return abs(exp(bestDiff) - 1.0) <= MAX_STRETCH + 0.005
    }

    fun plan(a: TrackAnalysis, b: TrackAnalysis, durationAMs: Long, crossfadeMs: Long): MixPlan {
        val endA = a.audibleEndMs.takeIf { it > 0 && it <= durationAMs } ?: durationAMs
        val startBPlain = b.audibleStartMs.takeIf { it in 150L..8_000L } ?: 0L

        fun plainFade(fadeMs: Long) = MixPlan(
            startAMs = (endA - fadeMs).coerceAtLeast(0L),
            overlapMs = fadeMs,
            startBMs = startBPlain,
            speedB = 1f,
            beatMatched = false
        )

        // Fallback = equal-power fade that finishes where the music stops,
        // incoming song starts where its music actually starts.
        val fallback = plainFade(crossfadeMs)

        val ta = a.tail ?: return fallback
        val hb = b.head ?: return fallback
        if (ta.confidence < MIN_CONFIDENCE || hb.confidence < MIN_CONFIDENCE) return fallback

        val pA = ta.periodMs

        // Detectors often lock onto half / double tempo. Pick the octave of B that is closest to A.
        var pB = hb.periodMs
        var bestDiff = abs(ln(pB / pA))
        for (f in doubleArrayOf(0.5, 2.0)) {
            val c = hb.periodMs * f
            val d = abs(ln(c / pA))
            if (d < bestDiff) {
                bestDiff = d
                pB = c
            }
        }
        val octaveAdjusted = abs(pB - hb.periodMs) > 1e-6

        // B must play at speed = pB / pA for its beats to land on A's beats.
        val speed = pB / pA
        // Both grids are trustworthy but the tempos are too far apart to match: keep the blend short.
        if (abs(speed - 1.0) > MAX_STRETCH) {
            return plainFade(min(crossfadeMs, CLASH_FADE_MS))
        }

        // ---- 1. Try to line the songs up on BAR lines (beat 1 on beat 1) -------------------------
        val barA = pA * 4.0
        val barB = pB * 4.0
        if (ta.barOffset >= 0 && hb.barOffset >= 0 && !octaveAdjusted && barA <= crossfadeMs + EARLY_SLACK_MS) {
            val bars = max(1, (crossfadeMs / barA).roundToInt())
                .let { n -> if (n * barA > crossfadeMs + EARLY_SLACK_MS) max(1, n - 1) else n }
            val overlap = bars * barA
            val anchorA = ta.anchorMs + ta.barOffset * pA
            val anchorB = hb.anchorMs + hb.barOffset * pB
            val startA = place(anchorA, barA, overlap, endA, durationAMs, crossfadeMs)
            val startB = entry(anchorB, barB, b.audibleStartMs, pB)
            if (startA != null && startB != null) {
                return MixPlan(startA.toLong(), overlap.toLong(), startB.toLong(), speed.toFloat(), true)
            }
        }

        // ---- 2. Line up on BEATS ------------------------------------------------------------------
        val beats = max(2, (crossfadeMs / pA).roundToInt())
        val overlap = beats * pA
        val startA = place(ta.anchorMs, pA, overlap, endA, durationAMs, crossfadeMs) ?: return fallback
        val startB = entry(hb.anchorMs, pB, b.audibleStartMs, pB) ?: return fallback

        return MixPlan(
            startAMs = startA.toLong(),
            overlapMs = overlap.toLong(),
            startBMs = startB.toLong(),
            speedB = speed.toFloat(),
            beatMatched = true
        )
    }

    /**
     * Start position in the outgoing song: a grid point (anchor + k * unit) that makes the blend finish
     * about where A's music ends, never before the engine can be ready, and never past the end of the file.
     */
    private fun place(
        anchor: Double, unit: Double, overlap: Double,
        endA: Long, durationA: Long, crossfadeMs: Long
    ): Double? {
        if (unit <= 0.0) return null
        val earliest = endA - crossfadeMs - EARLY_SLACK_MS
        val latest = durationA - overlap - 100.0
        val k = floor((endA - overlap + 0.5 * unit - anchor) / unit)
        var start = anchor + k * unit
        var guard = 0
        while (start > latest && guard++ < 64) start -= unit
        while (start < earliest && guard++ < 128) start += unit
        if (start > latest || start < 0.0) return null
        return start
    }

    /** Entry point in the incoming song: the first grid point (beat or bar line) after its music starts. */
    private fun entry(anchor: Double, unit: Double, audibleStartMs: Long, beatMs: Double): Double? {
        if (unit <= 0.0) return null
        val m0 = ceil((audibleStartMs - 0.25 * beatMs - anchor) / unit)
        var startB = anchor + m0 * unit
        if (startB < 0.0) startB += ceil(-startB / unit) * unit
        if (startB > MAX_ENTRY_MS) return null
        return startB
    }
}
