package com.example.ui.components

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Album background style (Apple Music look):
 *
 *  - TOP    : the album art as a true 1:1 square, full width, never faded or blurred.
 *  - BOTTOM : one smooth colour taken from the art itself. It starts inside the lower part of
 *             the square (FADE_FRACTION of its height) and gets stronger until it is solid at
 *             the square's bottom edge, so the picture melts into the controls with no glow.
 */
private const val FADE_FRACTION = 0.30f
private const val ART_FADE_MS = 900

private val TINT = Color(10, 10, 14)

// Eased (smooth-step) ramp: 0 at the top of the colour layer, 1 at the bottom of the square.
// Position values are fractions of the fade zone.
private val FADE_CURVE = listOf(
    0.00f to 0.00f, 0.15f to 0.03f, 0.30f to 0.12f, 0.50f to 0.35f,
    0.70f to 0.68f, 0.85f to 0.90f, 1.00f to 1.00f
)

// =============================================================================
// TOP: the square picture
// =============================================================================
@Composable
fun AlbumArtTop(
    artworkUri: String?,
    modifier: Modifier = Modifier
) {
    ArtworkView(
        artworkUri = artworkUri,
        modifier = modifier,
        cornerRadius = 0.dp,
        fadeMillis = ART_FADE_MS
    )
}

// =============================================================================
// BOTTOM: solid colour sampled from the art
// =============================================================================

/** Average colour of the lower part of the art, toned down so white text stays readable. */
@Composable
private fun rememberArtworkColor(artworkUri: String?): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(TINT) }
    LaunchedEffect(artworkUri) {
        target = if (artworkUri.isNullOrBlank()) TINT else withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(if (artworkUri.startsWith("/")) File(artworkUri) else Uri.parse(artworkUri))
                    .size(48)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                val bmp = (result?.drawable as? BitmapDrawable)?.bitmap
                    ?: return@runCatching TINT
                // average the bottom 40 % of the picture
                val w = bmp.width
                val h = bmp.height
                val startY = (h * 0.6f).toInt().coerceAtMost(h - 1)
                var r = 0L; var g = 0L; var b = 0L; var n = 0L
                for (y in startY until h) for (x in 0 until w) {
                    val px = bmp.getPixel(x, y)
                    r += (px shr 16) and 0xFF
                    g += (px shr 8) and 0xFF
                    b += px and 0xFF
                    n++
                }
                if (n == 0L) return@runCatching TINT
                var cr = r / 255f / n
                var cg = g / 255f / n
                var cb = b / 255f / n
                // keep it dark enough for white text, and never pitch black
                val lum = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                val maxLum = 0.36f
                val minLum = 0.10f
                val k = when {
                    lum > maxLum -> maxLum / lum
                    lum < minLum && lum > 0.001f -> minLum / lum
                    else -> 1f
                }
                cr = (cr * k).coerceIn(0f, 1f)
                cg = (cg * k).coerceIn(0f, 1f)
                cb = (cb * k).coerceIn(0f, 1f)
                Color(cr, cg, cb)
            }.getOrDefault(TINT)
        }
    }
    val animated by animateColorAsState(target, tween(ART_FADE_MS), label = "artwork_color")
    return animated
}

/**
 * Place this inside the controls area (the box right below the square art).
 * It reaches up over the bottom FADE_FRACTION of the art and fades the sampled colour in.
 *
 * @param artHeight the height of the square art (= the screen width)
 */
@Composable
fun AlbumReflection(
    artworkUri: String?,
    artHeight: Dp,
    modifier: Modifier = Modifier
) {
    val base = rememberArtworkColor(artworkUri)
    val deeper = Color(base.red * 0.80f, base.green * 0.80f, base.blue * 0.80f)

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val fadePx = (artHeight.roundToPx() * FADE_FRACTION).roundToInt()
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth, constraints.maxHeight + fadePx)
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(0, -fadePx)
                }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fadePx = artHeight.toPx() * FADE_FRACTION
                val zone = (fadePx / size.height).coerceIn(0.01f, 1f)
                val stops = Array(FADE_CURVE.size + 1) { i ->
                    if (i < FADE_CURVE.size)
                        FADE_CURVE[i].first * zone to Color.Black.copy(alpha = FADE_CURVE[i].second)
                    else 1f to Color.Black
                }
                drawRect(brush = Brush.verticalGradient(colorStops = stops), blendMode = BlendMode.DstIn)
            }
            .background(Brush.verticalGradient(listOf(base, base, deeper)))
    )
}
