package com.example.playback

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One planned transition.
 *
 *  startAMs    : position in the OUTGOING song where the incoming song must start (on a beat)
 *  overlapMs   : length of the blend, measured on the outgoing song (a whole number of beats)
 *  startBMs    : position in the INCOMING song where it begins (on one of its beats)
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
    private const val MIN_CONFIDENCE = 5.0

    /** Largest tempo change we are willing to apply to the incoming song during the blend (10 %). */
    private const val MAX_STRETCH = 0.10

    /** The incoming song may not start later than this, otherwise we'd skip too much of it. */
    private const val MAX_ENTRY_MS = 15_000.0

    fun plan(a: TrackAnalysis, b: TrackAnalysis, durationAMs: Long, crossfadeMs: Long): MixPlan {
        val endA = a.audibleEndMs.takeIf { it > 0 && it <= durationAMs } ?: durationAMs

        // Fallback = the old behaviour: equal-power fade that finishes where the music stops,
        // incoming song starts where its music actually starts.
        val fallback = MixPlan(
            startAMs = (endA - crossfadeMs).coerceAtLeast(0L),
            overlapMs = crossfadeMs,
            startBMs = b.audibleStartMs.takeIf { it in 150L..8_000L } ?: 0L,
            speedB = 1f,
            beatMatched = false
        )

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

        // B must play at speed = pB / pA for its beats to land on A's beats.
        val speed = pB / pA
        if (abs(speed - 1.0) > MAX_STRETCH) return fallback

        // Blend length = a whole number of A's beats, as close as possible to the chosen duration.
        val beats = max(2, (crossfadeMs / pA).roundToInt())
        val overlap = beats * pA

        // Start on the A-beat that makes the blend finish about where A's music ends.
        val k = floor((endA - overlap + 0.5 * pA - ta.anchorMs) / pA)
        val startA = max(0.0, ta.anchorMs + k * pA)

        // Enter B on its first beat after its music starts.
        val m0 = ceil((b.audibleStartMs - 0.25 * pB - hb.anchorMs) / pB)
        var startB = hb.anchorMs + m0 * pB
        if (startB < 0.0) startB += ceil(-startB / pB) * pB
        if (startB > MAX_ENTRY_MS) return fallback

        return MixPlan(
            startAMs = startA.toLong(),
            overlapMs = overlap.toLong(),
            startBMs = startB.toLong(),
            speedB = speed.toFloat(),
            beatMatched = true
        )
    }
}
