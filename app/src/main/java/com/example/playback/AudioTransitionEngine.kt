package com.example.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.example.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where the music is actually audible (skips silent intro / outro). endMs = -1 if unknown. */
data class AudibleBounds(val startMs: Long, val endMs: Long)

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCrossfading: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isShuffle: Boolean = false,
    val crossfadeDurationSec: Int = 4, // 0 (Off), 2, 4, 6, 8, 12
    val manualFadeDurationMs: Long = 400L,
    val volume: Float = 1.0f,
    val errorMessage: String? = null
)

class AudioTransitionEngine(
    private val context: Context,
    private val onSongCompleted: () -> Unit,
    private val getNextSong: () -> Song?,
    private val onSongChanged: (Song) -> Unit
) {
    private val tag = "AudioTransitionEngine"
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private var playerA: MediaPlayer? = null
    private var playerB: MediaPlayer? = null
    private var activePlayerIndex: Int = 0 // 0 for A, 1 for B
    private var isPreparedB = false
    private var preloadedSongId: Long? = null

    private var crossfadeJob: Job? = null
    private var tickerJob: Job? = null
    private var manualTransitionJob: Job? = null
    private var manualPreSwap = false          // true while manual fade has not yet switched players
    private var pauseAfterManual = false       // user hit pause during the manual fade-out

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var hasAudioFocus = false

    // True only when WE were playing and the system took focus temporarily (call, voice assistant...).
    // Without this flag the app resumed by itself even after the user had paused it.
    private var resumeOnFocusGain = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnFocusGain = false
                pauseInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = _playbackState.value.isPlaying
                pauseInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!_playbackState.value.isCrossfading) activePlayer?.setVolume(0.25f, 0.25f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (!_playbackState.value.isCrossfading) {
                    activePlayer?.setVolume(_playbackState.value.volume, _playbackState.value.volume)
                }
                if (resumeOnFocusGain && !_playbackState.value.isPlaying && _playbackState.value.currentSong != null) {
                    resumeOnFocusGain = false
                    resume()
                }
                resumeOnFocusGain = false
            }
        }
    }

    init {
        initializePlayers()
        startTicker()
    }

    private fun initializePlayers() {
        playerA?.release()
        playerB?.release()

        playerA = createPlayer("PlayerA")
        playerB = createPlayer("PlayerB")
        activePlayerIndex = 0
    }

    private fun createPlayer(name: String): MediaPlayer {
        return MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            setOnErrorListener { mp, what, extra ->
                Log.e(tag, "$name error: what=$what, extra=$extra")
                _playbackState.value = _playbackState.value.copy(
                    errorMessage = "Playback error ($what, $extra)",
                    isPlaying = false
                )
                true
            }
            setOnCompletionListener { mp ->
                Log.d(tag, "$name completed playback")
                handlePlayerCompletion(mp)
            }
        }
    }

    private val activePlayer: MediaPlayer?
        get() = if (activePlayerIndex == 0) playerA else playerB

    private val standbyPlayer: MediaPlayer?
        get() = if (activePlayerIndex == 0) playerB else playerA

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = audioManager?.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
            hasAudioFocus = false
        }
    }

    /**
     * Cancels any running crossfade / manual fade and puts BOTH players back in a clean state.
     * Without this, the standby player keeps playing in the background (the "can't pause" bug).
     */
    private fun abortTransitions() {
        val wasBusy = crossfadeJob?.isActive == true ||
            manualTransitionJob?.isActive == true ||
            _playbackState.value.isCrossfading
        crossfadeJob?.cancel()
        manualTransitionJob?.cancel()
        crossfadeJob = null
        manualTransitionJob = null
        manualPreSwap = false
        pauseAfterManual = false
        if (wasBusy) {
            try { standbyPlayer?.reset() } catch (e: Exception) { /* ignore */ }
            isPreparedB = false
            preloadedSongId = null
            val v = _playbackState.value.volume
            try { activePlayer?.setVolume(v, v) } catch (e: Exception) { /* ignore */ }
            _playbackState.value = _playbackState.value.copy(isCrossfading = false)
        }
    }

    fun playSong(song: Song, startPositionMs: Long = 0L) {
        abortTransitions()

        engineScope.launch {
            if (!requestAudioFocus()) {
                Log.w(tag, "Failed to get audio focus")
            }

            try {
                val player = activePlayer ?: return@launch
                player.reset()
                player.setVolume(_playbackState.value.volume, _playbackState.value.volume)
                setDataSource(player, song)
                player.prepare()

                if (startPositionMs > 0 && startPositionMs < player.duration) {
                    player.seekTo(startPositionMs.toInt())
                }

                player.start()

                _playbackState.value = _playbackState.value.copy(
                    currentSong = song,
                    isPlaying = true,
                    currentPositionMs = player.currentPosition.toLong(),
                    durationMs = player.duration.toLong(),
                    isCrossfading = false,
                    errorMessage = null
                )

                onSongChanged(song)
                preloadNextTrack()
            } catch (e: Exception) {
                Log.e(tag, "Error playing song: ${e.message}", e)
                _playbackState.value = _playbackState.value.copy(
                    errorMessage = "Cannot play ${song.title}: ${e.localizedMessage}",
                    isPlaying = false
                )
            }
        }
    }

    /**
     * Smoothly transitions to next/previous song with configurable manual fade duration.
     */
    fun transitionToSong(nextSong: Song, isNext: Boolean = true) {
        val manualFadeMs = _playbackState.value.manualFadeDurationMs
        if (manualFadeMs <= 0 || !_playbackState.value.isPlaying || activePlayer == null) {
            playSong(nextSong)
            return
        }

        abortTransitions()

        manualPreSwap = true
        manualTransitionJob = engineScope.launch {
            val currentPlayer = activePlayer ?: return@launch
            val nextP = standbyPlayer ?: return@launch
            val maxVol = _playbackState.value.volume

            try {
                nextP.reset()
                nextP.setVolume(0.0f, 0.0f)
                setDataSource(nextP, nextSong)
                nextP.prepare()

                val steps = 15
                val stepDelay = (manualFadeMs / 2) / steps

                for (i in 1..steps) {
                    val vol = (1.0f - (i.toFloat() / steps)) * maxVol
                    currentPlayer.setVolume(vol, vol)
                    delay(stepDelay)
                }

                currentPlayer.pause()
                currentPlayer.setVolume(maxVol, maxVol)
                try { currentPlayer.reset() } catch (e: Exception) { /* ignore */ }

                activePlayerIndex = if (activePlayerIndex == 0) 1 else 0
                manualPreSwap = false

                if (pauseAfterManual) {
                    // User pressed pause during the fade-out: land on the new song, paused.
                    pauseAfterManual = false
                    nextP.setVolume(maxVol, maxVol)
                    _playbackState.value = _playbackState.value.copy(
                        currentSong = nextSong,
                        isPlaying = false,
                        durationMs = nextP.duration.toLong(),
                        currentPositionMs = 0L,
                        isCrossfading = false,
                        errorMessage = null
                    )
                    onSongChanged(nextSong)
                    return@launch
                }

                nextP.start()
                _playbackState.value = _playbackState.value.copy(
                    currentSong = nextSong,
                    isPlaying = true,
                    durationMs = nextP.duration.toLong(),
                    currentPositionMs = 0L,
                    isCrossfading = false,
                    errorMessage = null
                )
                onSongChanged(nextSong)

                for (i in 1..steps) {
                    val vol = (i.toFloat() / steps) * maxVol
                    nextP.setVolume(vol, vol)
                    delay(stepDelay)
                }
                nextP.setVolume(maxVol, maxVol)

                preloadNextTrack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Manual fade transition error, fallback to direct play", e)
                manualPreSwap = false
                playSong(nextSong)
            }
        }
    }

    private fun setDataSource(player: MediaPlayer, song: Song) {
        if (!song.filePath.isNullOrBlank() && File(song.filePath).exists()) {
            player.setDataSource(song.filePath)
            return
        }

        val uri = Uri.parse(song.uriString)
        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null && File(path).exists()) {
                player.setDataSource(path)
                return
            }
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                player.setDataSource(pfd.fileDescriptor)
                return
            }
        } catch (e: Exception) {
            Log.w(tag, "openFileDescriptor failed for ${song.uriString}, trying default setDataSource", e)
        }

        player.setDataSource(context, uri)
    }

    /** Tap on play/pause. Decides from what the player is REALLY doing, so one tap is always enough. */
    fun togglePlayPause() {
        val reallyPlaying = try { activePlayer?.isPlaying == true } catch (e: Exception) { false }
        val fadingOut = manualTransitionJob?.isActive == true && manualPreSwap && _playbackState.value.isPlaying
        if (reallyPlaying || fadingOut) pause() else resume()
    }

    fun resume() {
        val player = activePlayer ?: return
        pauseAfterManual = false
        resumeOnFocusGain = false
        requestAudioFocus()
        try {
            if (!player.isPlaying) player.start()
            // If we paused in the middle of an Automix overlap, bring the other song back too.
            if (_playbackState.value.isCrossfading) {
                standbyPlayer?.let { if (!it.isPlaying) it.start() }
            }
        } catch (e: Exception) {
            Log.e(tag, "Resume error", e)
        }
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
    }

    /** User pause: also gives up audio focus so calls / voice assistants can't restart us. */
    fun pause() {
        resumeOnFocusGain = false
        pauseInternal()
        abandonAudioFocus()
    }

    private fun pauseInternal() {
        // Pause pressed while a manual skip fade-out is still running
        if (manualTransitionJob?.isActive == true && manualPreSwap) {
            pauseAfterManual = true
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
            return
        }

        try {
            activePlayer?.let { if (it.isPlaying) it.pause() }
            // During an overlap BOTH players are playing: pause both.
            if (_playbackState.value.isCrossfading) {
                standbyPlayer?.let { if (it.isPlaying) it.pause() }
            }
        } catch (e: Exception) {
            Log.e(tag, "Pause error", e)
        }
        // Always sync the state (it used to stay "playing" when the player had already stopped,
        // which is why the button needed two taps).
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    /** Loads a song ready to play but paused (used to restore the last session after the app was closed). */
    fun prepareSongPaused(song: Song, startPositionMs: Long) {
        abortTransitions()
        engineScope.launch {
            try {
                val player = activePlayer ?: return@launch
                player.reset()
                player.setVolume(_playbackState.value.volume, _playbackState.value.volume)
                setDataSource(player, song)
                player.prepare()
                val dur = player.duration.toLong()
                val pos = if (startPositionMs in 1 until dur) startPositionMs else 0L
                if (pos > 0) player.seekTo(pos.toInt())
                _playbackState.value = _playbackState.value.copy(
                    currentSong = song,
                    isPlaying = false,
                    currentPositionMs = pos,
                    durationMs = dur,
                    isCrossfading = false,
                    errorMessage = null
                )
                preloadNextTrack()
            } catch (e: Exception) {
                Log.e(tag, "Could not restore last song: ${e.message}")
            }
        }
    }

    fun seekTo(positionMs: Long) {
        // Seeking cancels a running crossfade so the two songs can't get out of sync.
        if (_playbackState.value.isCrossfading) abortTransitions()
        activePlayer?.let { player ->
            try {
                player.seekTo(positionMs.toInt())
                _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
            } catch (e: Exception) {
                Log.e(tag, "Seek error", e)
            }
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
    }

    fun setShuffle(shuffle: Boolean) {
        _playbackState.value = _playbackState.value.copy(isShuffle = shuffle)
        preloadNextTrack()
    }

    fun setCrossfadeDurationSec(seconds: Int) {
        _playbackState.value = _playbackState.value.copy(crossfadeDurationSec = seconds)
    }

    fun setManualFadeDurationMs(millis: Long) {
        _playbackState.value = _playbackState.value.copy(manualFadeDurationMs = millis)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        _playbackState.value = _playbackState.value.copy(volume = clamped)
        activePlayer?.setVolume(clamped, clamped)
    }


    // ---------------------------------------------------------------------
    // AUTOMIX analysis: find first / last audible moment of a song
    // ---------------------------------------------------------------------
    private val boundsCache = ConcurrentHashMap<Long, AudibleBounds>()
    private val boundsInFlight = ConcurrentHashMap<Long, Boolean>()

    private fun analyzeAsync(song: Song) {
        if (boundsCache.containsKey(song.id)) return
        if (boundsInFlight.putIfAbsent(song.id, true) != null) return
        engineScope.launch(Dispatchers.IO) {
            try { getBounds(song) } finally { boundsInFlight.remove(song.id) }
        }
    }

    private fun getBounds(song: Song): AudibleBounds {
        boundsCache[song.id]?.let { return it }
        val head = scanAudible(song, tail = false)
        val tail = scanAudible(song, tail = true)
        val bounds = AudibleBounds(startMs = head ?: 0L, endMs = tail ?: -1L)
        boundsCache[song.id] = bounds
        Log.d(tag, "Automix bounds for ${song.title}: start=${bounds.startMs} end=${bounds.endMs}")
        return bounds
    }

    private fun scanAudible(song: Song, tail: Boolean): Long? {
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

            val windowStartMs = if (tail) (durationMs - 15_000L).coerceAtLeast(0L) else 0L
            val windowEndMs = if (tail) durationMs else minOf(12_000L, durationMs)

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
            val threshold = 700 // about -33 dB on a 16-bit scale
            var guard = 0

            while (!outputDone && guard++ < 20_000) {
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
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val sb = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                        var peak = 0
                        while (sb.hasRemaining()) {
                            val v = abs(sb.get().toInt())
                            if (v > peak) peak = v
                        }
                        val tMs = info.presentationTimeUs / 1000
                        val chunkMs = info.size * 1000L / (2L * channels.coerceAtLeast(1) * sampleRate.coerceAtLeast(1))
                        if (tMs >= windowStartMs && peak > threshold) {
                            if (first < 0) first = tMs
                            last = tMs + chunkMs
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }

            return if (first < 0) null else if (tail) last else first
        } catch (e: Exception) {
            Log.w(tag, "Automix scan failed for ${song.title}: ${e.message}")
            return null
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            try { codec?.release() } catch (e: Exception) { }
            try { extractor.release() } catch (e: Exception) { }
        }
    }

    private fun preloadNextTrack() {
        val nextSong = getNextSong() ?: return
        analyzeAsync(nextSong)
        if (nextSong.id == preloadedSongId && isPreparedB) return

        engineScope.launch(Dispatchers.IO) {
            try {
                val nextP = standbyPlayer ?: return@launch
                nextP.reset()
                nextP.setVolume(0.0f, 0.0f)
                setDataSource(nextP, nextSong)
                nextP.prepare()
                isPreparedB = true
                preloadedSongId = nextSong.id
                Log.d(tag, "Preloaded next track: ${nextSong.title}")
            } catch (e: Exception) {
                Log.e(tag, "Failed to preload next track: ${e.message}")
                isPreparedB = false
                preloadedSongId = null
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = engineScope.launch {
            while (isActive) {
                val player = activePlayer
                if (player != null && _playbackState.value.isPlaying) {
                    try {
                        val currentPos = player.currentPosition.toLong()
                        val duration = player.duration.toLong()

                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = currentPos,
                            durationMs = duration
                        )

                        // Automix trigger: the overlap ENDS where the outgoing song stops being audible
                        val currentSong = _playbackState.value.currentSong
                        if (currentSong != null) analyzeAsync(currentSong)
                        val bounds = currentSong?.let { boundsCache[it.id] }
                        val endMs = bounds?.endMs
                            ?.takeIf { it > 0 && it <= duration }
                            ?: duration

                        val crossfadeSec = _playbackState.value.crossfadeDurationSec
                        val crossfadeMs = crossfadeSec * 1000L

                        if (crossfadeSec > 0 &&
                            duration > (crossfadeMs + 2000L) &&
                            (endMs - currentPos) <= crossfadeMs &&
                            !_playbackState.value.isCrossfading &&
                            _playbackState.value.repeatMode != RepeatMode.ONE
                        ) {
                            startCrossfade(endMs - currentPos)
                        }
                    } catch (e: Exception) {
                        // ignore transitory illegal state during track switch
                    }
                }
                delay(250)
            }
        }
    }

    private fun swapToNextSong(nextP: MediaPlayer, nextSong: Song) {
        activePlayerIndex = if (activePlayerIndex == 0) 1 else 0
        _playbackState.value = _playbackState.value.copy(
            currentSong = nextSong,
            durationMs = try { nextP.duration.toLong() } catch (e: Exception) { 0L },
            currentPositionMs = try { nextP.currentPosition.toLong() } catch (e: Exception) { 0L }
        )
        onSongChanged(nextSong)
    }

    /**
     * iPhone-style automatic crossfade:
     *  - equal-power curve (no volume dip in the middle)
     *  - progress follows the OLD song's playback position, so pausing freezes the fade
     *  - title / artwork switch at the midpoint of the fade
     */
    private fun startCrossfade(remainingDurationMs: Long) {
        val nextSong = getNextSong() ?: return
        val currentP = activePlayer ?: return
        val nextP = standbyPlayer ?: return
        val startPos = try { currentP.currentPosition.toLong() } catch (e: Exception) { return }
        val totalMs = remainingDurationMs.coerceAtLeast(1000L)

        _playbackState.value = _playbackState.value.copy(isCrossfading = true)
        Log.d(tag, "Initiating crossfade to: ${nextSong.title} (remaining=${remainingDurationMs}ms)")

        crossfadeJob?.cancel()
        crossfadeJob = engineScope.launch {
            try {
                // Automix: begin the incoming song where its music actually starts
                val nextBounds = withContext(Dispatchers.IO) { getBounds(nextSong) }

                if (!isPreparedB || preloadedSongId != nextSong.id) {
                    nextP.reset()
                    nextP.setVolume(0.0f, 0.0f)
                    setDataSource(nextP, nextSong)
                    nextP.prepare()
                }

                nextP.setVolume(0.0f, 0.0f)
                if (nextBounds.startMs in 150L..8_000L) nextP.seekTo(nextBounds.startMs.toInt())
                // If the user already paused, resume() will start it later.
                if (_playbackState.value.isPlaying) nextP.start()

                var swapped = false

                while (isActive) {
                    val pos = try { currentP.currentPosition.toLong() } catch (e: Exception) { startPos + totalMs }
                    val oldSongEnded = _playbackState.value.isPlaying &&
                        try { !currentP.isPlaying } catch (e: Exception) { true }

                    val fraction = if (oldSongEnded) 1.0f
                    else ((pos - startPos).toFloat() / totalMs).coerceIn(0.0f, 1.0f)

                    // Equal-power curve
                    val angle = fraction * (PI.toFloat() / 2f)
                    val master = _playbackState.value.volume
                    val fadeOutVol = cos(angle) * master
                    val fadeInVol = sin(angle) * master
                    currentP.setVolume(fadeOutVol, fadeOutVol)
                    nextP.setVolume(fadeInVol, fadeInVol)

                    if (!swapped && fraction >= 0.5f) {
                        swapped = true
                        swapToNextSong(nextP, nextSong)
                    }

                    if (fraction >= 1.0f) break
                    delay(40)
                }

                // Crossfade finished
                try { currentP.pause() } catch (e: Exception) { /* ignore */ }
                try { currentP.reset() } catch (e: Exception) { /* ignore */ }
                currentP.setVolume(1.0f, 1.0f)
                val master = _playbackState.value.volume
                nextP.setVolume(master, master)
                if (!swapped) swapToNextSong(nextP, nextSong)

                isPreparedB = false
                preloadedSongId = null
                _playbackState.value = _playbackState.value.copy(isCrossfading = false)
                preloadNextTrack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Crossfade execution failed", e)
                _playbackState.value = _playbackState.value.copy(isCrossfading = false)
            }
        }
    }

    private fun handlePlayerCompletion(player: MediaPlayer) {
        if (player != activePlayer) {
            Log.d(tag, "Completion ignored from non-active player")
            return
        }

        if (_playbackState.value.isCrossfading) {
            // Already handled by crossfade completion
            return
        }

        if (_playbackState.value.repeatMode == RepeatMode.ONE) {
            activePlayer?.let { p ->
                p.seekTo(0)
                p.start()
                _playbackState.value = _playbackState.value.copy(isPlaying = true, currentPositionMs = 0L)
            }
            return
        }

        onSongCompleted()
    }

    fun release() {
        abandonAudioFocus()
        tickerJob?.cancel()
        abortTransitions()
        playerA?.release()
        playerB?.release()
        playerA = null
        playerB = null
    }
}
