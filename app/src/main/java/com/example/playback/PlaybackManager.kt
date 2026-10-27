package com.example.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaybackManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow<Int>(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var originalQueue: List<Song> = emptyList()
    private var onSongPlayedCallback: ((Song) -> Unit)? = null

    private val engine = AudioTransitionEngine(
        context = context,
        onSongCompleted = { handleTrackFinished() },
        getNextSong = { peekNextSong() },
        onSongChanged = { song ->
            syncCurrentIndexForSong(song)
            onSongPlayedCallback?.invoke(song)
            updatePlaybackServiceNotification()
            saveSession()
        }
    )

    val playbackState: StateFlow<PlaybackState> = engine.playbackState

    private val prefs = context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)

    private var lastSavedQueueKey = ""

    init {
        // Restore saved Automix / skip-fade settings (they used to reset to 4 sec on every launch)
        engine.setCrossfadeDurationSec(prefs.getInt("automix_sec", 4))
        engine.setManualFadeDurationMs(prefs.getLong("manual_fade_ms", 400L))
        try {
            engine.setRepeatMode(RepeatMode.valueOf(prefs.getString("repeat", RepeatMode.ALL.name) ?: RepeatMode.ALL.name))
        } catch (e: Exception) { /* keep default */ }
        engine.setShuffle(prefs.getBoolean("shuffle", false))

        // Remember what is playing (position every 2 seconds while playing)
        scope.launch {
            while (true) {
                delay(2000)
                if (playbackState.value.isPlaying) saveSession()
            }
        }
    }

    /** Saves queue, current song, position, shuffle and repeat so the app can pick up where it left off. */
    fun saveSession() {
        val song = playbackState.value.currentSong ?: return
        val q = _queue.value
        if (q.isEmpty()) return
        val key = q.joinToString(",") { it.id.toString() } + "|" + originalQueue.joinToString(",") { it.id.toString() }
        val e = prefs.edit()
        if (key != lastSavedQueueKey) {
            e.putString("queue_ids", q.joinToString(",") { it.id.toString() })
            e.putString("original_ids", originalQueue.joinToString(",") { it.id.toString() })
            lastSavedQueueKey = key
        }
        e.putLong("current_id", song.id)
            .putInt("index", _currentIndex.value)
            .putLong("position_ms", playbackState.value.currentPositionMs)
            .putBoolean("shuffle", playbackState.value.isShuffle)
            .putString("repeat", playbackState.value.repeatMode.name)
            .apply()
    }

    /** Called once at launch with the library; loads the last song (paused) so it shows in the mini player. */
    fun restoreSession(library: List<Song>) {
        if (playbackState.value.currentSong != null || _queue.value.isNotEmpty()) return
        val byId = library.associateBy { it.id }
        fun ids(key: String): List<Song> =
            (prefs.getString(key, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.mapNotNull { byId[it] }

        val queueSongs = ids("queue_ids")
        if (queueSongs.isEmpty()) return
        originalQueue = ids("original_ids").ifEmpty { queueSongs }
        _queue.value = queueSongs

        val savedId = prefs.getLong("current_id", -1L)
        var idx = queueSongs.indexOfFirst { it.id == savedId }
        if (idx < 0) idx = prefs.getInt("index", 0).coerceIn(0, queueSongs.size - 1)
        _currentIndex.value = idx
        lastSavedQueueKey = ""
        engine.prepareSongPaused(queueSongs[idx], prefs.getLong("position_ms", 0L))
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun setOnSongPlayedListener(listener: (Song) -> Unit) {
        onSongPlayedCallback = listener
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        originalQueue = songs
        val validIndex = startIndex.coerceIn(0, songs.size - 1)

        val queueToSet: List<Song> = if (playbackState.value.isShuffle) {
            val current = songs[validIndex]
            val rest = songs.filterIndexed { index, _ -> index != validIndex }.shuffled()
            listOf(current) + rest
        } else {
            songs
        }

        _queue.value = queueToSet
        _currentIndex.value = if (playbackState.value.isShuffle) 0 else validIndex

        startServiceIfNeeded()
        engine.playSong(queueToSet[_currentIndex.value])
    }

    fun playSong(song: Song) {
        val existingIndex = _queue.value.indexOfFirst { it.id == song.id }
        if (existingIndex >= 0) {
            _currentIndex.value = existingIndex
            startServiceIfNeeded()
            engine.transitionToSong(song, isNext = true)
        } else {
            val list = _queue.value.toMutableList()
            if (list.isNotEmpty()) {
                list.add(song)
                originalQueue = originalQueue + song
                _queue.value = list
                _currentIndex.value = list.size - 1
                startServiceIfNeeded()
                engine.transitionToSong(song, isNext = true)
            } else {
                playQueue(listOf(song), 0)
            }
        }
    }

    fun playPause() {
        if (playbackState.value.currentSong != null) {
            val wasPlaying = playbackState.value.isPlaying
            engine.togglePlayPause()
            // After a restored session the foreground service isn't running yet
            if (!wasPlaying || !playbackState.value.isPlaying) startServiceIfNeeded()
        } else if (_queue.value.isNotEmpty()) {
            val index = _currentIndex.value.coerceAtLeast(0)
            startServiceIfNeeded()
            engine.playSong(_queue.value[index])
        }
        updatePlaybackServiceNotification()
        saveSession()
    }

    fun skipNext() {
        val nextSong = getNextSongToPlay()
        if (nextSong != null) {
            engine.transitionToSong(nextSong, isNext = true)
        } else if (_queue.value.isNotEmpty()) {
            _currentIndex.value = 0
            engine.transitionToSong(_queue.value[0], isNext = true)
        }
        updatePlaybackServiceNotification()
    }

    fun skipPrevious() {
        // If > 3 seconds in, seek to start of current song first
        if (playbackState.value.currentPositionMs > 3000L) {
            engine.seekTo(0L)
            return
        }

        val prevIndex = _currentIndex.value - 1
        if (prevIndex >= 0 && prevIndex < _queue.value.size) {
            _currentIndex.value = prevIndex
            val prevSong = _queue.value[prevIndex]
            engine.transitionToSong(prevSong, isNext = false)
        } else if (playbackState.value.repeatMode == RepeatMode.ALL && _queue.value.isNotEmpty()) {
            val lastIndex = _queue.value.size - 1
            _currentIndex.value = lastIndex
            engine.transitionToSong(_queue.value[lastIndex], isNext = false)
        } else {
            engine.seekTo(0L)
        }
        updatePlaybackServiceNotification()
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        saveSession()
    }

    fun toggleRepeat() {
        val nextMode = when (playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        engine.setRepeatMode(nextMode)
    }

    fun toggleShuffle() {
        val newShuffle = !playbackState.value.isShuffle
        engine.setShuffle(newShuffle)

        val currentSong: Song? = playbackState.value.currentSong
        if (newShuffle) {
            if (currentSong != null) {
                val remaining: List<Song> = originalQueue.filter { it.id != currentSong.id }.shuffled()
                _queue.value = listOf(currentSong) + remaining
                _currentIndex.value = 0
            } else {
                _queue.value = originalQueue.shuffled()
                _currentIndex.value = 0
            }
        } else {
            _queue.value = originalQueue
            if (currentSong != null) {
                _currentIndex.value = originalQueue.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
            }
        }
    }

    fun setCrossfadeDuration(seconds: Int) {
        engine.setCrossfadeDurationSec(seconds)
        prefs.edit().putInt("automix_sec", seconds).apply()
    }

    fun setManualFadeDuration(millis: Long) {
        engine.setManualFadeDurationMs(millis)
        prefs.edit().putLong("manual_fade_ms", millis).apply()
    }

    fun addToQueue(song: Song) {
        _queue.value = _queue.value + song
        if (originalQueue.isNotEmpty()) {
            originalQueue = originalQueue + song
        }
    }

    fun playNextInQueue(song: Song) {
        val currentIdx = _currentIndex.value
        val list = _queue.value.toMutableList()
        val insertAt = (currentIdx + 1).coerceAtMost(list.size)
        list.add(insertAt, song)
        _queue.value = list
    }

    fun removeFromQueue(index: Int) {
        val list = _queue.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _queue.value = list
            if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
            } else if (index == _currentIndex.value && list.isNotEmpty()) {
                val newIdx = index.coerceAtMost(list.size - 1)
                _currentIndex.value = newIdx
                engine.playSong(list[newIdx])
            }
        }
    }

    /** Removes every copy of a deleted song from the queue (and stops if nothing is left). */
    fun removeSongById(songId: Long) {
        val list = _queue.value
        for (i in list.indices.reversed()) {
            if (list[i].id == songId) removeFromQueue(i)
        }
        originalQueue = originalQueue.filter { it.id != songId }
        if (_queue.value.isEmpty() && playbackState.value.currentSong?.id == songId) {
            engine.pause()
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _queue.value = list

            val curr = _currentIndex.value
            if (curr == fromIndex) {
                _currentIndex.value = toIndex
            } else if (fromIndex < curr && toIndex >= curr) {
                _currentIndex.value = curr - 1
            } else if (fromIndex > curr && toIndex <= curr) {
                _currentIndex.value = curr + 1
            }
        }
    }

    fun clearQueue() {
        val currentSong: Song? = playbackState.value.currentSong
        if (currentSong != null) {
            _queue.value = listOf(currentSong)
            _currentIndex.value = 0
        } else {
            _queue.value = emptyList()
            _currentIndex.value = -1
        }
    }

    private fun handleTrackFinished() {
        val next = getNextSongToPlay()
        if (next != null) {
            engine.playSong(next)
        } else if (playbackState.value.repeatMode == RepeatMode.ALL && _queue.value.isNotEmpty()) {
            _currentIndex.value = 0
            engine.playSong(_queue.value[0])
        } else {
            engine.pause()
        }
    }

    private fun peekNextSong(): Song? {
        val nextIdx = _currentIndex.value + 1
        return if (nextIdx in _queue.value.indices) {
            _queue.value[nextIdx]
        } else if (playbackState.value.repeatMode == RepeatMode.ALL && _queue.value.isNotEmpty()) {
            _queue.value[0]
        } else null
    }

    private fun getNextSongToPlay(): Song? {
        val nextIdx = _currentIndex.value + 1
        return if (nextIdx in _queue.value.indices) {
            _currentIndex.value = nextIdx
            _queue.value[nextIdx]
        } else if (playbackState.value.repeatMode == RepeatMode.ALL && _queue.value.isNotEmpty()) {
            _currentIndex.value = 0
            _queue.value[0]
        } else null
    }

    private fun syncCurrentIndexForSong(song: Song) {
        val idx = _queue.value.indexOfFirst { it.id == song.id }
        if (idx >= 0) {
            _currentIndex.value = idx
        }
    }

    private fun startServiceIfNeeded() {
        try {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Background start not allowed: the service is already running in that case
        }
    }

    private fun updatePlaybackServiceNotification() {
        // The service observes playbackState itself now. This is only a safety ping, and it must
        // never throw: a crash here (background start restriction) used to break skipping.
        try {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_UPDATE_NOTIFICATION
            }
            context.startService(intent)
        } catch (e: Exception) {
            // service already running or start not allowed: nothing to do
        }
    }

    fun release() {
        engine.release()
    }
}
