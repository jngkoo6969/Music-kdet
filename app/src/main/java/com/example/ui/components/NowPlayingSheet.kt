package com.example.ui.components

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Song
import com.example.lyrics.ParsedLyrics
import com.example.playback.PlaybackState
import com.example.playback.RepeatMode
import com.example.ui.theme.AppleMusicPink
import com.example.ui.theme.AppleMusicPurple
import com.example.ui.theme.AppleMusicRed

@Composable
fun NowPlayingSheet(
    playbackState: PlaybackState,
    queue: List<Song>,
    currentIndex: Int,
    parsedLyrics: ParsedLyrics,
    activeLyricIndex: Int,
    isLyricsViewVisible: Boolean,
    isQueueViewVisible: Boolean,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLyricsView: () -> Unit,
    onToggleQueueView: () -> Unit,
    onPlayTrackAt: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onEditMetadata: (Song) -> Unit,
    onEditLyrics: (Song) -> Unit,
    onChangeArtwork: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    var showMenu by remember { mutableStateOf(false) }
    var artworkHeightPx by remember { mutableIntStateOf(0) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    // System media volume (follows the phone's volume buttons too)
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    LaunchedEffect(isDraggingVolume) {
        if (!isDraggingVolume) {
            // Let the system apply the new level before we read it back, so the bar holds where it was set.
            delay(600)
            while (true) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                delay(400)
            }
        }
    }

    // Seek bar: keep showing the released position until the player has actually caught up with the seek.
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingSeekMs) {
        if (pendingSeekMs != null) {
            delay(1500)
            pendingSeekMs = null
        }
    }
    LaunchedEffect(playbackState.currentPositionMs) {
        val pending = pendingSeekMs
        if (pending != null && kotlin.math.abs(playbackState.currentPositionMs - pending) < 1200L) {
            pendingSeekMs = null
        }
    }

    val currentPosition = when {
        isDraggingSlider -> sliderDragPosition.toLong()
        pendingSeekMs != null -> pendingSeekMs!!
        else -> playbackState.currentPositionMs
    }
    val duration = playbackState.durationMs.coerceAtLeast(1L)
    val remainingMs = (duration - currentPosition).coerceAtLeast(0L)

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2C0B18),
            Color(0xFF14081E),
            Color(0xFF0A0A0C),
            Color(0xFF000000)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient)
            // Swallow taps on empty areas so they never reach the song list / mini player behind.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("now_playing_sheet")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Full-screen artwork / lyrics / queue area (Apple Music style)
            Box(
                modifier = Modifier
                    // 55/45 split (ART_FRACTION); lyrics/queue keep the taller area.
                    .weight(if (isLyricsViewVisible || isQueueViewVisible) 3f else ART_FRACTION)
                    .fillMaxWidth()
                    .onSizeChanged { artworkHeightPx = it.height }
            ) {
                when {
                    isLyricsViewVisible -> {
                        SyncedLyricsView(
                            lyrics = parsedLyrics,
                            activeLineIndex = activeLyricIndex,
                            onSeekTo = onSeekTo,
                            onEditLyrics = { onEditLyrics(song) },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(top = 56.dp, start = 24.dp, end = 24.dp)
                        )
                    }
                    isQueueViewVisible -> {
                        UpNextQueueView(
                            queue = queue,
                            currentIndex = currentIndex,
                            onPlayTrackAt = onPlayTrackAt,
                            onRemoveFromQueue = onRemoveFromQueue,
                            onClearQueue = onClearQueue,
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(top = 56.dp, start = 24.dp, end = 24.dp)
                        )
                    }
                    else -> {
                        var dragOffset by remember { mutableFloatStateOf(0f) }
                        var dragDownOffset by remember { mutableFloatStateOf(0f) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, dragAmount ->
                                            dragOffset += dragAmount
                                        },
                                        onDragEnd = {
                                            if (dragOffset < -120) {
                                                onSkipNext()
                                            } else if (dragOffset > 120) {
                                                onSkipPrevious()
                                            }
                                            dragOffset = 0f
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dragAmount ->
                                            dragDownOffset += dragAmount
                                        },
                                        onDragEnd = {
                                            if (dragDownOffset > 160f) onCollapse()
                                            dragDownOffset = 0f
                                        },
                                        onDragCancel = { dragDownOffset = 0f }
                                    )
                                }
                        ) {
                            // Sharp album art that dissolves with a progressive blur (see AlbumBackdrop.kt)
                            AlbumArtTop(
                                artworkUri = song.artworkUri,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Soft shade at the top so the close button stays readable
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)
                                        )
                                    )
                            )
                        }
                    }
                }

                // Navigation on top: grab handle - swipe down (or tap) to close
                var handleDrag by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .height(40.dp)
                        .clickable { onCollapse() }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount -> handleDrag += dragAmount },
                                onDragEnd = {
                                    if (handleDrag > 40f) onCollapse()
                                    handleDrag = 0f
                                },
                                onDragCancel = { handleDrag = 0f }
                            )
                        }
                        .testTag("now_playing_collapse"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.75f))
                    )
                }
            }

            Box(modifier = Modifier.weight(if (isLyricsViewVisible || isQueueViewVisible) 2f else 1f - ART_FRACTION).fillMaxWidth()) {
            // Lower half: flipped, stretched, progressively blurred copy of the art's bottom
            if (!isLyricsViewVisible && !isQueueViewVisible && artworkHeightPx > 0) {
                AlbumReflection(
                    artworkUri = song.artworkUri,
                    artHeight = with(LocalDensity.current) { artworkHeightPx.toDp() }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
            // Song Info & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = AppleMusicRed,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp).testTag("now_playing_more")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Song Metadata") },
                            onClick = {
                                showMenu = false
                                onEditMetadata(song)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit / Import Lyrics") },
                            onClick = {
                                showMenu = false
                                onEditLyrics(song)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change Album Artwork") },
                            onClick = {
                                showMenu = false
                                onChangeArtwork(song)
                            }
                        )
                    }
                }

                IconButton(
                    onClick = { onToggleFavorite(song) },
                    modifier = Modifier.size(48.dp).testTag("now_playing_favorite")
                ) {
                    Icon(
                        imageVector = if (LocalSongActions.current.favoriteIds.contains(song.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (LocalSongActions.current.favoriteIds.contains(song.id)) "Favorited" else "Favorite",
                        tint = if (LocalSongActions.current.favoriteIds.contains(song.id)) AppleMusicRed else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Seeker / Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpandingSlider(
                    value = currentPosition.toFloat(),
                    onValueChange = {
                        isDraggingSlider = true
                        sliderDragPosition = it
                    },
                    onValueChangeFinished = {
                        val target = sliderDragPosition.toLong()
                        pendingSeekMs = target
                        isDraggingSlider = false
                        onSeekTo(target)
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.testTag("now_playing_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "-${formatTime(remainingMs)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // Primary Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(64.dp).testTag("now_playing_previous")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Song",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(80.dp).testTag("now_playing_play_pause")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(64.dp).testTag("now_playing_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Song",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            // Volume control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                ExpandingSlider(
                    value = volume,
                    onValueChange = {
                        isDraggingVolume = true
                        volume = it
                        val step = it.roundToInt().coerceIn(0, maxVolume)
                        if (step != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
                        }
                    },
                    onValueChangeFinished = {
                        // Snap to the level that was set and hold it there.
                        volume = volume.roundToInt().coerceIn(0, maxVolume).toFloat()
                        isDraggingVolume = false
                    },
                    valueRange = 0f..maxVolume.toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("now_playing_volume")
                )
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Secondary Controls: Shuffle, Repeat, Lyrics, Queue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp).testTag("now_playing_shuffle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffle) AppleMusicRed else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Synced Lyrics button
                IconButton(
                    onClick = onToggleLyricsView,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_lyrics_button")
                ) {
                    Icon(
                        imageVector = if (isLyricsViewVisible) Icons.Default.FormatQuote else Icons.Default.ChatBubbleOutline,
                        contentDescription = "Lyrics",
                        tint = if (isLyricsViewVisible) AppleMusicRed else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Up Next Queue button
                IconButton(
                    onClick = onToggleQueueView,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_queue_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (isQueueViewVisible) AppleMusicRed else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Repeat Mode
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier.size(48.dp).testTag("now_playing_repeat")
                ) {
                    val icon = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val tint = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> Color.White.copy(alpha = 0.6f)
                        RepeatMode.ALL, RepeatMode.ONE -> AppleMusicRed
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat Mode",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
