package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as AndroidPlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.example.MainActivity
import com.example.R
import com.example.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Foreground service that owns the MediaSession + the media notification.
 *
 * It OBSERVES PlaybackManager.playbackState, so the lock-screen player, the notification shade
 * player, the Quick-Settings media card and the "dynamic island / Now Bar" style alerts always
 * show the real song and the real play/pause state. Skip buttons call the manager directly.
 */
class MusicPlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private val notificationId = 1001
    private val channelId = "music_playback_channel"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var isForeground = false

    // What we last told the system, so we only update when something really changed
    private var lastSongId = -1L
    private var lastPlaying: Boolean? = null
    private var lastDuration = -1L
    private var lastReportedPos = 0L
    private var lastReportedAt = 0L
    private var cachedArtwork: Bitmap? = null
    private var cachedArtworkSongId = -1L

    private val manager: PlaybackManager
        get() = PlaybackManager.getInstance(applicationContext)

    companion object {
        const val ACTION_START = "com.example.action.START"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.action.UPDATE_NOTIFICATION"
        const val ACTION_PLAY_PAUSE = "com.example.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREVIOUS = "com.example.action.PREVIOUS"
        const val ACTION_STOP = "com.example.action.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        // Must be foreground quickly after startForegroundService()
        goForeground(buildNotification(null, false))
        startObserving()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "MusicPlaybackService").apply {
            setCallback(object : MediaSession.Callback() {
                // Separate play / pause (the old code toggled for both, which could flip the wrong way)
                override fun onPlay() {
                    if (!manager.playbackState.value.isPlaying) manager.playPause()
                }

                override fun onPause() {
                    if (manager.playbackState.value.isPlaying) manager.playPause()
                }

                override fun onSkipToNext() = manager.skipNext()

                override fun onSkipToPrevious() = manager.skipPrevious()

                override fun onSeekTo(pos: Long) = manager.seekTo(pos)

                override fun onStop() {
                    if (manager.playbackState.value.isPlaying) manager.playPause()
                    stopSelfCompletely()
                }
            })
            isActive = true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Music Playback", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playing media notification and controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Make sure we are foreground even if the system restarted us
        if (!isForeground) goForeground(buildNotification(null, false))
        startObserving()

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> manager.playPause()
            ACTION_NEXT -> manager.skipNext()
            ACTION_PREVIOUS -> manager.skipPrevious()
            ACTION_STOP -> {
                if (manager.playbackState.value.isPlaying) manager.playPause()
                stopSelfCompletely()
                return START_NOT_STICKY
            }
            else -> Unit
        }
        // Force a refresh so a freshly started service is never stuck on the placeholder
        refresh(force = true)
        return START_NOT_STICKY
    }

    private fun startObserving() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            manager.playbackState.collect { refresh(force = false) }
        }
    }

    /** Pushes the current state to the MediaSession and the notification when needed. */
    private fun refresh(force: Boolean) {
        val state = manager.playbackState.value
        val song = state.currentSong ?: return

        val expectedPos = if (lastPlaying == true)
            lastReportedPos + (SystemClock.elapsedRealtime() - lastReportedAt) else lastReportedPos
        val seeked = abs(state.currentPositionMs - expectedPos) > 1500L

        val songChanged = song.id != lastSongId
        val playingChanged = state.isPlaying != lastPlaying
        val durationChanged = state.durationMs != lastDuration

        if (!force && !songChanged && !playingChanged && !durationChanged && !seeked) return

        lastSongId = song.id
        lastPlaying = state.isPlaying
        lastDuration = state.durationMs
        lastReportedPos = state.currentPositionMs
        lastReportedAt = SystemClock.elapsedRealtime()

        updateSession(song, state.isPlaying, state.currentPositionMs, state.durationMs)
        val notification = buildNotification(song, state.isPlaying)
        goForeground(notification)
        // Stay a foreground service while paused too. Detaching it made Android kill the
        // service/process after a short while, which removed the player from the notification/lock screen.
    }

    private fun goForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(notificationId, notification)
            }
            isForeground = true
        } catch (e: Exception) {
            // Not allowed right now (background start). Just post the notification instead.
            try {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(notificationId, notification)
            } catch (_: Exception) { }
        }
    }

    private fun updateSession(song: Song, isPlaying: Boolean, position: Long, duration: Long) {
        val session = mediaSession ?: return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, song.displayArtist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, song.displayAlbum)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        artworkFor(song)?.let {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, it)
        }
        session.setMetadata(metadata.build())

        val pb = AndroidPlaybackState.Builder()
            .setActions(
                AndroidPlaybackState.ACTION_PLAY or
                    AndroidPlaybackState.ACTION_PAUSE or
                    AndroidPlaybackState.ACTION_PLAY_PAUSE or
                    AndroidPlaybackState.ACTION_SKIP_TO_NEXT or
                    AndroidPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    AndroidPlaybackState.ACTION_SEEK_TO or
                    AndroidPlaybackState.ACTION_STOP
            )
            .setState(
                if (isPlaying) AndroidPlaybackState.STATE_PLAYING else AndroidPlaybackState.STATE_PAUSED,
                position,
                if (isPlaying) 1.0f else 0f,
                SystemClock.elapsedRealtime()
            )
        session.setPlaybackState(pb.build())
    }

    private fun servicePendingIntent(code: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, MusicPlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun action(iconRes: Int, title: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, iconRes), title, pi).build()

    private fun buildNotification(song: Song?, isPlaying: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId) else @Suppress("DEPRECATION") Notification.Builder(this)

        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(song?.title ?: "Music")
            .setContentText(song?.displayArtist ?: "")
            .setContentIntent(openApp)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setDeleteIntent(servicePendingIntent(4, ACTION_STOP))
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", servicePendingIntent(1, ACTION_PREVIOUS)))
            .addAction(
                action(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    servicePendingIntent(2, ACTION_PLAY_PAUSE)
                )
            )
            .addAction(action(android.R.drawable.ic_media_next, "Next", servicePendingIntent(3, ACTION_NEXT)))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        // MediaStyle tied to the session = the real media player look (artwork, seek bar, big buttons)
        val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { style.setMediaSession(it) }
        builder.setStyle(style)

        song?.let { s -> artworkFor(s)?.let { builder.setLargeIcon(it) } }
        return builder.build()
    }

    private fun artworkFor(song: Song): Bitmap? {
        if (cachedArtworkSongId == song.id) return cachedArtwork
        cachedArtwork = loadArtworkBitmap(song.artworkUri)
        cachedArtworkSongId = song.id
        return cachedArtwork
    }

    private fun loadArtworkBitmap(artworkUriString: String?): Bitmap? {
        if (artworkUriString.isNullOrBlank()) return null
        return try {
            if (artworkUriString.startsWith("/")) {
                BitmapFactory.decodeFile(artworkUriString)
            } else {
                val uri = Uri.parse(artworkUriString)
                if (uri.scheme == "file") {
                    BitmapFactory.decodeFile(uri.path)
                } else {
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun stopSelfCompletely() {
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
        isForeground = false
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep playing if music is on, otherwise clean up
        if (!manager.playbackState.value.isPlaying) stopSelfCompletely()
    }

    override fun onDestroy() {
        super.onDestroy()
        observeJob?.cancel()
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
