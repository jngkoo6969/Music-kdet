package com.example.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.data.Song
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Tempo + beat grid of one end of a song.
 *
 * Beats happen at  anchorMs + k * periodMs  (k = any integer), in the song's own timeline.
 * confidence is "how much stronger the beats are than the average of the track" (noise scores ~2).
 * barOffset says which of the 4 beats in a bar is the downbeat ("beat 1"): downbeats happen at
 *   anchorMs + (barOffset + 4 * m) * periodMs.  -1 = unknown (the accents were too even to tell).
 */
data class BeatInfo(
    val bpm: Double,
    val periodMs: Double,
    val anchorMs: Double,
    val confidence: Double,
    val barOffset: Int = -1
)

/**
 * Everything Automix needs to know about a song. All of it is computed on the phone, offline.
 *
 *  - audibleStartMs / audibleEndMs : where the music really starts / stops (skips silent intro / outro).
 *                                    audibleEndMs = -1 when unknown.
 *  - head : beat grid measured in the first ~45 s  (used when the song is the INCOMING one)
 *  - tail : beat grid measured in the last  ~45 s  (used when the song is the OUTGOING one)
 */
data class TrackAnalysis(
    val audibleStartMs: Long,
    val audibleEndMs: Long,
    val head: BeatInfo?,
    val tail: BeatInfo?
)

class TrackAnalyzer(private val context: Context) {

    private val tag = "TrackAnalyzer"
    private val prefs = context.getSharedPreferences("automix_analysis", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, TrackAnalysis>()

    private fun key(song: Song) = "v${VERSION}_${song.id}_${song.fileSize}"

    /** Returns the saved analysis if this song was already analyzed (in memory or from a previous launch). */
    fun cached(song: Song): TrackAnalysis? {
        val k = key(song)
        memory[k]?.let { return it }
        val stored = prefs.getString(k, null) ?: return null
        val decoded = decode(stored) ?: return null
        memory[k] = decoded
        return decoded
    }

    /** Blocking. Call from a background thread. Decodes ~45 s from each end of the file. */
    @Synchronized
    fun analyze(song: Song): TrackAnalysis {
        cached(song)?.let { return it }

        val head = scan(song, tail = false)
        val tail = scan(song, tail = true)

        val result = TrackAnalysis(
            audibleStartMs = head?.firstAudibleMs?.takeIf { it >= 0 } ?: 0L,
            audibleEndMs = tail?.lastAudibleMs?.takeIf { it >= 0 } ?: -1L,
            head = head?.let { beatsOf(it, tail = false) },
            tail = tail?.let { beatsOf(it, tail = true) }
        )

        val k = key(song)
        memory[k] = result
        try { prefs.edit().putString(k, encode(result)).apply() } catch (e: Exception) { /* cache only */ }
        Log.d(
            tag,
            "Analyzed '${song.title}': audible=${result.audibleStartMs}..${result.audibleEndMs} " +
                "head=${result.head?.bpm?.toInt()}bpm(${result.head?.confidence?.toInt()}) " +
                "tail=${result.tail?.bpm?.toInt()}bpm(${result.tail?.confidence?.toInt()})"
        )
        return result
    }

    // ------------------------------------------------------------------
    // Decoding one window (first / last 45 s) -> audible bounds + energy envelope
    // ------------------------------------------------------------------

    private class Scan(
        val firstAudibleMs: Long,
        val lastAudibleMs: Long,
        val low: FloatArray,   // RMS of the low band (kick / bass), one value per hop
        val full: FloatArray,  // RMS of the whole signal, one value per hop
        val hops: Int,
        val hopMs: Double,
        val envStartMs: Double
    )

    private fun scan(song: Song, tail: Boolean): Scan? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (!song.filePath.isNullOrBlank() && File(song.filePath).exists()) {
                extractor.setDataSource(song.filePath)
            } else {
                extractor.setDataSource(context, Uri.parse(song.uriString), null)
            }

            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) { trackIndex = i; break }
            }
            if (trackIndex < 0) return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) / 1000 else return null

            val windowStartMs = if (tail) (durationMs - WINDOW_MS).coerceAtLeast(0L) else 0L
            val windowEndMs = if (tail) durationMs else min(WINDOW_MS, durationMs)

            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            if (windowStartMs > 0) extractor.seekTo(windowStartMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var first = -1L
            var last = -1L
            var guard = 0

            // envelope accumulators
            val low = FloatArray(MAX_HOPS)
            val full = FloatArray(MAX_HOPS)
            var hops = 0
            var hopSamples = 0
            var lpAlpha = 0f
            var lp = 0f
            var ssLow = 0.0
            var ssFull = 0.0
            var inHop = 0
            var envStartMs = -1.0

            while (!outputDone && guard++ < 40_000) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (size < 0 || extractor.sampleTime > windowEndMs * 1000) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 5_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (outIdx >= 0) {
                    val out = codec.getOutputBuffer(outIdx)
                    if (out != null && info.size > 0) {
                        val ch = channels.coerceAtLeast(1)
                        val sr = sampleRate.coerceAtLeast(8000)
                        if (hopSamples == 0) {
                            hopSamples = (sr * HOP_SECONDS).toInt().coerceAtLeast(1)
                            lpAlpha = (1.0 - exp(-2.0 * PI * LOW_BAND_HZ / sr)).toFloat()
                        }
                        if (envStartMs < 0) envStartMs = info.presentationTimeUs / 1000.0

                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val sb = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                        var peak = 0
                        var acc = 0
                        var chIdx = 0
                        while (sb.hasRemaining()) {
                            val v = sb.get().toInt()
                            val av = abs(v)
                            if (av > peak) peak = av
                            acc += v
                            chIdx++
                            if (chIdx == ch) {
                                val m = acc.toFloat() / ch
                                acc = 0
                                chIdx = 0
                                lp += lpAlpha * (m - lp)
                                ssFull += (m * m).toDouble()
                                ssLow += (lp * lp).toDouble()
                                if (++inHop == hopSamples) {
                                    if (hops < MAX_HOPS) {
                                        full[hops] = sqrt(ssFull / hopSamples).toFloat()
                                        low[hops] = sqrt(ssLow / hopSamples).toFloat()
                                        hops++
                                    }
                                    ssFull = 0.0
                                    ssLow = 0.0
                                    inHop = 0
                                }
                            }
                        }
                        val tMs = info.presentationTimeUs / 1000
                        val chunkMs = info.size * 1000L / (2L * ch * sr)
                        if (tMs >= windowStartMs && peak > AUDIBLE_THRESHOLD) {
                            if (first < 0) first = tMs
                            last = tMs + chunkMs
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }

            val hopMs = if (hopSamples > 0) hopSamples * 1000.0 / sampleRate.coerceAtLeast(8000) else HOP_SECONDS * 1000.0
            return Scan(first, last, low, full, hops, hopMs, max(envStartMs, 0.0))
        } catch (e: Exception) {
            Log.w(tag, "Analysis scan failed for ${song.title}: ${e.message}")
            return null
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            try { codec?.release() } catch (e: Exception) { }
            try { extractor.release() } catch (e: Exception) { }
        }
    }

    private fun beatsOf(scan: Scan, tail: Boolean): BeatInfo? {
        if (scan.firstAudibleMs < 0 || scan.lastAudibleMs < 0 || scan.hops < BeatDetector.MIN_HOPS) return null
        val envEnd = scan.envStartMs + scan.hops * scan.hopMs
        // The grid is measured on the part of the window that will really be used for the mix:
        // the last seconds before the music stops (outgoing) / the first seconds after it starts (incoming).
        val regionStart: Double
        val regionEnd: Double
        if (tail) {
            regionEnd = min(scan.lastAudibleMs.toDouble(), envEnd)
            regionStart = max(scan.envStartMs, regionEnd - REGION_MS)
        } else {
            regionStart = max(scan.firstAudibleMs.toDouble(), scan.envStartMs)
            regionEnd = min(regionStart + REGION_MS, envEnd)
        }
        return BeatDetector.detect(
            scan.low, scan.full, scan.hops, scan.hopMs, scan.envStartMs, regionStart, regionEnd
        )
    }

    // ------------------------------------------------------------------
    // Tiny text format for the on-disk cache
    // ------------------------------------------------------------------

    private fun encodeBeat(b: BeatInfo?) =
        if (b == null) "-" else "${b.bpm},${b.periodMs},${b.anchorMs},${b.confidence},${b.barOffset}"

    private fun decodeBeat(s: String): BeatInfo? {
        if (s == "-") return null
        val p = s.split(",")
        if (p.size != 5) return null
        return BeatInfo(p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toInt())
    }

    private fun encode(a: TrackAnalysis) =
        "${a.audibleStartMs};${a.audibleEndMs};${encodeBeat(a.head)};${encodeBeat(a.tail)}"

    private fun decode(s: String): TrackAnalysis? = try {
        val p = s.split(";")
        if (p.size != 4) null
        else TrackAnalysis(p[0].toLong(), p[1].toLong(), decodeBeat(p[2]), decodeBeat(p[3]))
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Bump when the algorithm changes so old cached results are ignored. */
        private const val VERSION = 3
        private const val WINDOW_MS = 45_000L   // longer window = steadier tempo estimate
        private const val REGION_MS = 28_000.0
        private const val HOP_SECONDS = 0.010
        private const val LOW_BAND_HZ = 150.0
        private const val MAX_HOPS = 6_000
        private const val AUDIBLE_THRESHOLD = 700 // about -33 dB on a 16-bit scale
    }
}

/**
 * Offline tempo / beat-phase estimator (no FFT, no network, a few hundred ms per window).
 *
 *  1. energy envelope (low band + full band) -> onset strength (log-compressed positive flux)
 *  2. autocorrelation proposes candidate beat periods (65..190 BPM)
 *  3. a fine "comb" search (period +-0.7 hop, step 0.02 hop) scores every candidate on beats AND half-beats
 *  4. the beat phase is re-measured locally on the region that will be used for the mix,
 *     so a tiny period error can't drift the grid by the time we get there
 */
internal object BeatDetector {

    const val MIN_HOPS = 400
    private const val MIN_BPM = 65.0
    private const val MAX_BPM = 190.0

    fun detect(
        low: FloatArray, full: FloatArray, n: Int,
        hopMs: Double, envStartMs: Double,
        regionStartMs: Double, regionEndMs: Double
    ): BeatInfo? {
        if (n < MIN_HOPS) return null
        val s = onsetStrength(low, full, n)
        var sum = 0.0
        for (i in 0 until n) sum += s[i]
        val meanS = sum / n
        if (meanS < 1e-6) return null

        val minP = 60000.0 / MAX_BPM / hopMs
        val maxP = 60000.0 / MIN_BPM / hopMs
        val maxLag = (2 * maxP).toInt() + 3
        if (maxLag >= n / 2) return null

        // 2. autocorrelation
        val ac = DoubleArray(maxLag + 1)
        for (lag in max(1, minP.toInt() - 1)..maxLag) {
            var d = 0.0
            for (i in 0 until n - lag) d += (s[i] * s[i + lag]).toDouble()
            ac[lag] = d / (n - lag)
        }
        val cands = ArrayList<Pair<Double, Int>>()
        for (lag in (minP.toInt() + 1) until maxP.toInt()) {
            if (ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1]) {
                val bpm = 60000.0 / (lag * hopMs)
                cands.add(Pair((ac[lag] + 0.5 * ac[2 * lag]) * prior(bpm), lag))
            }
        }
        if (cands.isEmpty()) return null
        cands.sortByDescending { it.first }

        // 3. fine comb search around the best candidates
        var bestScore = -1.0
        var bestMean = 0.0
        var bestP = 0.0
        var bestPhi = 0.0
        for (c in cands.take(8)) {
            var p = c.second - 0.7
            while (p <= c.second + 0.7) {
                val r = comb(s, n, p)
                val score = r[0] * prior(60000.0 / (p * hopMs))
                if (score > bestScore) {
                    bestScore = score
                    bestMean = r[0]
                    bestP = p
                    bestPhi = r[1]
                }
                p += 0.02
            }
        }
        if (bestP <= 0.0) return null

        // 4. phase re-measured near where the mix will happen
        val rs = (regionStartMs - envStartMs) / hopMs
        val re = (regionEndMs - envStartMs) / hopMs
        val anchorHops = localAnchor(s, n, bestP, rs, re) ?: bestPhi

        val bar = barOffset(s, n, bestP, anchorHops, rs, re)

        val periodMs = bestP * hopMs
        return BeatInfo(
            bpm = 60000.0 / periodMs,
            periodMs = periodMs,
            anchorMs = envStartMs + anchorHops * hopMs,
            confidence = bestMean / (meanS + 1e-9),
            barOffset = bar
        )
    }

    /**
     * Which of the 4 beats of a bar is "beat 1"? The downbeat usually carries the strongest attack
     * (kick + bass / chord change). Average the onset strength on every 4th beat inside the mix
     * region and take the strongest phase. If no phase clearly wins (e.g. a flat four-on-the-floor
     * kick) we return -1 and the planner just aligns on beats instead of bars.
     * Returns j in 0..3 meaning: the beat at (anchor + j * period) is a downbeat.
     */
    private fun barOffset(s: FloatArray, n: Int, p: Double, anchor: Double, rs: Double, re: Double): Int {
        if (p <= 0.0 || re - rs < 12 * p) return -1
        val sums = DoubleArray(4)
        val cnts = IntArray(4)
        var j = ceil((rs - anchor) / p).toInt()
        var guard = 0
        while (guard++ < 400) {
            val t = anchor + j * p
            if (t >= re) break
            val idx = (t + 0.5).toInt()
            if (idx in 1 until n - 1) {
                val v = max(s[idx - 1], max(s[idx], s[idx + 1])).toDouble()
                val slot = ((j % 4) + 4) % 4
                sums[slot] += v
                cnts[slot]++
            }
            j++
        }
        if (cnts.any { it < 3 }) return -1
        val means = DoubleArray(4) { sums[it] / cnts[it] }
        var best = 0
        for (i in 1 until 4) if (means[i] > means[best]) best = i
        var second = -1.0
        for (i in 0 until 4) if (i != best && means[i] > second) second = means[i]
        return if (means[best] > 1e-9 && means[best] >= 1.25 * second) best else -1
    }

    private fun prior(bpm: Double): Double {
        val x = ln(bpm / 115.0) / ln(2.0) / 0.9
        return exp(-0.5 * x * x)
    }

    private fun onsetStrength(low: FloatArray, full: FloatArray, n: Int): FloatArray {
        val on = DoubleArray(n)
        var prevL = ln(1.0 + 20.0 * low[0] / 32768.0)
        var prevF = ln(1.0 + 20.0 * full[0] / 32768.0)
        for (i in 1 until n) {
            val cl = ln(1.0 + 20.0 * low[i] / 32768.0)
            val cf = ln(1.0 + 20.0 * full[i] / 32768.0)
            on[i] = max(0.0, cl - prevL) + 0.7 * max(0.0, cf - prevF)
            prevL = cl
            prevF = cf
        }
        // subtract the local average (~0.5 s) so only real attacks remain
        val cs = DoubleArray(n + 1)
        for (i in 0 until n) cs[i + 1] = cs[i] + on[i]
        val o = FloatArray(n)
        for (i in 0 until n) {
            val lo = max(i - 25, 0)
            val hi = min(i + 25, n)
            val avg = (cs[hi] - cs[lo]) / (hi - lo)
            o[i] = max(0.0, on[i] - avg).toFloat()
        }
        // light smoothing so rounding to the hop grid doesn't lose a beat
        val s = o.copyOf()
        for (i in 1 until n - 1) s[i] = (o[i - 1] + 2f * o[i] + o[i + 1]) / 4f
        return s
    }

    /** Best phase for period p over the whole window. Returns [meanScore, phaseInHops]. */
    private fun comb(s: FloatArray, n: Int, p: Double): DoubleArray {
        var bestM = -1.0
        var bestPhi = 0.0
        var phi = 0.0
        while (phi < p) {
            var t = phi
            var sm = 0.0
            var sh = 0.0
            var cnt = 0
            while (t < n - 1 - p / 2) {
                sm += s[(t + 0.5).toInt()]
                sh += s[(t + p / 2 + 0.5).toInt()]
                cnt++
                t += p
            }
            val m = (sm + 0.5 * sh) / max(cnt, 1)
            if (m > bestM) {
                bestM = m
                bestPhi = phi
            }
            phi += 0.5
        }
        return doubleArrayOf(bestM, bestPhi)
    }

    /** Phase of the beat grid measured only inside [rs, re) (in hops). Returns a beat position inside that region. */
    private fun localAnchor(s: FloatArray, n: Int, p: Double, rs: Double, re: Double): Double? {
        if (re - rs < 4 * p) return null
        var bestScore = -1.0
        var bestA = rs
        var a = rs
        while (a < rs + p) {
            var t = a
            var sum = 0.0
            var cnt = 0
            while (t < re) {
                val idx = (t + 0.5).toInt()
                if (idx in 0 until n) sum += s[idx]
                val h = (t + p / 2 + 0.5).toInt()
                if (h in 0 until n) sum += 0.5 * s[h]
                cnt++
                t += p
            }
            val score = sum / max(cnt, 1)
            if (score > bestScore) {
                bestScore = score
                bestA = a
            }
            a += 0.25
        }
        return bestA
    }
}
