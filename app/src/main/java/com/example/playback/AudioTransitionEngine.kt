package com.example.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.coroutines.resume

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

    // Automix: offline analysis (tempo / beats / audible bounds) and tempo matching state
    private val analyzer = TrackAnalyzer(context)
    private val analysisInFlight = ConcurrentHashMap<Long, Boolean>()
    private var speedRampJob: Job? = null
    private val targetSpeed = floatArrayOf(1f, 1f)       // speed we want each player to run at
    private val speedDirty = booleanArrayOf(false, false)  // true = native speed must be (re)written
    private val speedTouched = booleanArrayOf(false, false) // true = this player once ran at a non-1.0 speed
    private var crossfadeFailedForId: Long? = null

    private companion object {
        /** Start preparing the blend this long before it is due (park B on its beat, wait for A's beat). */
        const val PREROLL_MS = 2_500L
        /** MediaPlayer.start() takes a few tens of ms to become audible; start that much early. */
        const val START_LATENCY_MS = 40L
        /** After the blend, the incoming song eases from the matched speed back to 1.0x over this time. */
        const val SPEED_RAMP_MS = 6_000L
    }

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
        speedRampJob?.cancel()
        speedRampJob = null
        for (i in 0..1) {
            if (speedTouched[i]) {
                targetSpeed[i] = 1f
                speedDirty[i] = true
                flushSpeed(i)
            }
        }
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
                flushSpeed(activePlayerIndex)
                crossfadeFailedForId = null

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
                flushSpeed(activePlayerIndex)
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
            flushSpeed(0)
            flushSpeed(1)
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
        if (_playbackState.value.isCrossfading || crossfadeJob?.isActive == true) abortTransitions()
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
    // AUTOMIX analysis (tempo, beats, audible bounds) - 100 % on-device, works offline
    // ---------------------------------------------------------------------
    private fun analyzeAsync(song: Song) {
        if (analyzer.cached(song) != null) return
        if (analysisInFlight.putIfAbsent(song.id, true) != null) return
        engineScope.launch(Dispatchers.IO) {
            try { analyzer.analyze(song) } finally { analysisInFlight.remove(song.id) }
        }
    }

    // ---------------------------------------------------------------------
    // Tempo matching: playback speed of a player (pitch is preserved)
    // ---------------------------------------------------------------------
    private fun playerAt(index: Int): MediaPlayer? = if (index == 0) playerA else playerB

    private fun setTargetSpeed(index: Int, speed: Float) {
        targetSpeed[index] = speed
        speedDirty[index] = true
        if (abs(speed - 1f) > 0.001f) speedTouched[index] = true
        flushSpeed(index)
    }

    /**
     * Writes the wanted speed to the native player. Only done while the player is really playing:
     * on some Android versions setPlaybackParams() on a paused / prepared player starts it by itself.
     */
    private fun flushSpeed(index: Int) {
        if (!speedDirty[index]) return
        val p = playerAt(index) ?: return
        try {
            if (!p.isPlaying) return
            p.playbackParams = PlaybackParams().setSpeed(targetSpeed[index]).setPitch(1.0f)
            if (abs(targetSpeed[index] - 1f) < 0.001f) {
                speedDirty[index] = false
                speedTouched[index] = false
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not set playback speed: ${e.message}")
        }
    }

    /** Eases a player from its current (matched) speed back to normal speed. */
    private fun startSpeedRamp(index: Int) {
        speedRampJob?.cancel()
        val from = targetSpeed[index]
        if (abs(from - 1f) < 0.002f) {
            setTargetSpeed(index, 1f)
            return
        }
        speedRampJob = engineScope.launch {
            val stepMs = 200L
            val steps = (SPEED_RAMP_MS / stepMs).toInt()
            for (i in 1..steps) {
                while (isActive && !_playbackState.value.isPlaying) delay(stepMs)
                setTargetSpeed(index, from + (1f - from) * (i.toFloat() / steps))
                delay(stepMs)
            }
            setTargetSpeed(index, 1f)
        }
    }

    /** Seeks and waits until the player really is there (so start() can't begin from the old position). */
    private suspend fun seekAndWait(player: MediaPlayer, positionMs: Long) {
        withTimeoutOrNull(1_500L) {
            suspendCancellableCoroutine<Unit> { cont ->
                player.setOnSeekCompleteListener { mp ->
                    mp.setOnSeekCompleteListener(null)
                    if (cont.isActive) cont.resume(Unit)
                }
                cont.invokeOnCancellation {
                    try { player.setOnSeekCompleteListener(null) } catch (e: Exception) { /* ignore */ }
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        player.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST) // sample-accurate
                    } else {
                        player.seekTo(positionMs.toInt())
                    }
                } catch (e: Exception) {
                    try { player.setOnSeekCompleteListener(null) } catch (e2: Exception) { /* ignore */ }
                    if (cont.isActive) cont.resume(Unit)
                }
            }
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

                        // Automix trigger. We start a little BEFORE the blend is due so the incoming song
                        // can be parked on one of its beats and released exactly on one of ours.
                        val currentSong = _playbackState.value.currentSong
                        if (currentSong != null) analyzeAsync(currentSong)
                        val analysis = currentSong?.let { analyzer.cached(it) }
                        val endMs = analysis?.audibleEndMs
                            ?.takeIf { it > 0 && it <= duration }
                            ?: duration

                        val crossfadeSec = _playbackState.value.crossfadeDurationSec
                        val crossfadeMs = crossfadeSec * 1000L

                        if (crossfadeSec > 0 &&
                            duration > (crossfadeMs + 2000L) &&
                            (endMs - currentPos) <= crossfadeMs + PREROLL_MS &&
                            crossfadeJob?.isActive != true &&
                            !_playbackState.value.isCrossfading &&
                            currentSong?.id != crossfadeFailedForId &&
                            _playbackState.value.repeatMode != RepeatMode.ONE
                        ) {
                            startCrossfade(crossfadeMs)
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
        crossfadeFailedForId = null
        activePlayerIndex = if (activePlayerIndex == 0) 1 else 0
        _playbackState.value = _playbackState.value.copy(
            currentSong = nextSong,
            durationMs = try { nextP.duration.toLong() } catch (e: Exception) { 0L },
            currentPositionMs = try { nextP.currentPosition.toLong() } catch (e: Exception) { 0L }
        )
        onSongChanged(nextSong)
    }

    /**
     * iPhone-style Automix, entirely offline:
     *  1. both songs were analyzed on the phone (tempo, beat grid, where the music starts / stops)
     *  2. the incoming song is prepared and parked on one of ITS beats
     *  3. it is released exactly when the outgoing song reaches one of ITS beats
     *  4. the incoming song is time-stretched (pitch preserved) so both tempos match,
     *     then eased back to normal speed after the blend
     *  5. equal-power fade (no volume dip); progress follows the OUTGOING song's position,
     *     so pausing freezes the fade
     *  6. title / artwork switch at the midpoint of the fade
     * If beat info is missing or the tempos are too far apart it falls back to a plain equal-power crossfade.
     */
    private fun startCrossfade(crossfadeMs: Long) {
        val nextSong = getNextSong() ?: return
        val currentSong = _playbackState.value.currentSong ?: return
        val currentP = activePlayer ?: return
        val nextP = standbyPlayer ?: return
        val nextIndex = if (activePlayerIndex == 0) 1 else 0

        crossfadeJob?.cancel()
        crossfadeJob = engineScope.launch {
            try {
                val durationA = try { currentP.duration.toLong() } catch (e: Exception) { return@launch }
                val plan = withContext(Dispatchers.IO) {
                    MixPlanner.plan(
                        analyzer.analyze(currentSong),
                        analyzer.analyze(nextSong),
                        durationA,
                        crossfadeMs
                    )
                }
                Log.d(tag, "Automix plan to '${nextSong.title}': $plan")

                // Park the incoming song on its entry beat (silent)
                if (!isPreparedB || preloadedSongId != nextSong.id) {
                    nextP.reset()
                    setDataSource(nextP, nextSong)
                    nextP.prepare()
                }
                nextP.setVolume(0.0f, 0.0f)
                if (plan.startBMs >= 20L) seekAndWait(nextP, plan.startBMs)

                // Wait for the outgoing song to reach its beat (nothing advances while paused)
                val triggerPos = plan.startAMs - START_LATENCY_MS
                while (isActive) {
                    val pos = try { currentP.currentPosition.toLong() } catch (e: Exception) { return@launch }
                    if (pos >= triggerPos) break
                    val remaining = triggerPos - pos
                    delay(if (remaining > 100L) 60L else 4L)
                }

                // Release the incoming song. If the user already paused, resume() will start it later.
                if (_playbackState.value.isPlaying) nextP.start()
                _playbackState.value = _playbackState.value.copy(isCrossfading = true)
                if (plan.beatMatched) setTargetSpeed(nextIndex, plan.speedB)

                var swapped = false
                var lastCorrection = 0L

                while (isActive) {
                    val pos = try { currentP.currentPosition.toLong() } catch (e: Exception) { plan.startAMs + plan.overlapMs }
                    val playing = _playbackState.value.isPlaying
                    val oldSongEnded = playing && try { !currentP.isPlaying } catch (e: Exception) { true }

                    val fraction = if (oldSongEnded) 1.0f
                    else ((pos - plan.startAMs).toFloat() / plan.overlapMs).coerceIn(0.0f, 1.0f)

                    // Equal-power curve
                    val angle = fraction * (PI.toFloat() / 2f)
                    val master = _playbackState.value.volume
                    val fadeOutVol = cos(angle) * master
                    val fadeInVol = sin(angle) * master
                    currentP.setVolume(fadeOutVol, fadeOutVol)
                    nextP.setVolume(fadeInVol, fadeInVol)

                    // Keep the two beat grids locked: gently nudge the incoming speed if it drifts
                    if (plan.beatMatched && playing && fraction < 1.0f) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastCorrection >= 500L) {
                            lastCorrection = now
                            val posB = try { nextP.currentPosition.toLong() } catch (e: Exception) { -1L }
                            if (posB >= 0L) {
                                val expectedB = plan.startBMs + (pos - plan.startAMs) * plan.speedB
                                val err = posB - expectedB // > 0 means the incoming song is ahead
                                val speed = if (abs(err) > 25f) {
                                    plan.speedB * (1f + (-err / 2500f).coerceIn(-0.015f, 0.015f))
                                } else plan.speedB
                                setTargetSpeed(nextIndex, speed)
                            }
                        }
                    }

                    if (!swapped && fraction >= 0.5f) {
                        swapped = true
                        swapToNextSong(nextP, nextSong)
                    }

                    if (fraction >= 1.0f) break
                    delay(40)
                }

                // Blend finished
                try { currentP.pause() } catch (e: Exception) { /* ignore */ }
                try { currentP.reset() } catch (e: Exception) { /* ignore */ }
                currentP.setVolume(1.0f, 1.0f)
                val master = _playbackState.value.volume
                nextP.setVolume(master, master)
                if (!swapped) swapToNextSong(nextP, nextSong)

                isPreparedB = false
                preloadedSongId = null
                _playbackState.value = _playbackState.value.copy(isCrossfading = false)
                if (plan.beatMatched) startSpeedRamp(nextIndex)
                preloadNextTrack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Crossfade execution failed", e)
                crossfadeFailedForId = currentSong.id
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
