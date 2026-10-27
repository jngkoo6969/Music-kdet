package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AppleMusicPink
import com.example.ui.theme.AppleMusicPurple
import com.example.ui.theme.AppleMusicRed
import java.io.File

@Composable
fun ArtworkView(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    elevation: Dp = 0.dp,
    contentDescription: String = "Album Artwork",
    fadeMillis: Int = 0
) {
    val shape = RoundedCornerShape(cornerRadius)

    val shadowModifier = if (elevation > 0.dp) {
        modifier.shadow(elevation, shape, clip = false)
    } else {
        modifier
    }

    Box(
        modifier = shadowModifier
            .clip(shape)
            .background(Color(0xFF1E1E24)),
        contentAlignment = Alignment.Center
    ) {
        if (fadeMillis > 0) {
            // Only the big players fade between songs; list rows stay cheap and fast.
            Crossfade(
                targetState = artworkUri,
                animationSpec = tween(durationMillis = fadeMillis),
                label = "artwork_fade"
            ) { uri -> ArtworkImage(uri, contentDescription) }
        } else {
            ArtworkImage(artworkUri, contentDescription)
        }
    }
}

@Composable
private fun ArtworkImage(uri: String?, contentDescription: String) {
    if (uri.isNullOrBlank()) {
        ArtworkPlaceholder()
        return
    }
    var failed by remember(uri) { mutableStateOf(false) }
    if (failed) {
        ArtworkPlaceholder()
        return
    }
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(if (uri.startsWith("/")) File(uri) else Uri.parse(uri))
            .crossfade(false)
            .build()
    }
    // AsyncImage (not SubcomposeAsyncImage): much cheaper inside scrolling lists.
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        onError = { failed = true }
    )
}

@Composable
fun ArtworkPlaceholder(modifier: Modifier = Modifier) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            AppleMusicRed,
            AppleMusicPink,
            AppleMusicPurple
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxSize(0.48f)
        )
    }
}
