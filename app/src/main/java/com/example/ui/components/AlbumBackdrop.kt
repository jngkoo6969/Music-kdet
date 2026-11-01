package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import java.io.FileInputStream
import java.io.InputStream
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

// Kept only so an older NowPlayingSheet.kt still compiles. Not used by this file.
const val ART_FRACTION = 0.55f

private const val FADE_FRACTION = 0.30f
private const val ART_FADE_MS = 900

private val TINT = Color(10, 10, 14)

// Eased (smooth-step) ramp. First = position inside the fade zone (0..1), second = colour strength.
private val FADE_POSITIONS = floatArrayOf(0.00f, 0.15f, 0.30f, 0.50f, 0.70f, 0.85f, 1.00f)
private val FADE_ALPHAS = floatArrayOf(0.00f, 0.03f, 0.12f, 0.35f, 0.68f, 0.90f, 1.00f)

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

private fun openArtwork(context: Context, uriString: String): InputStream? {
    if (uriString.startsWith("/")) return FileInputStream(uriString)
    val uri = Uri.parse(uriString)
    if (uri.scheme == "file") {
        val path = uri.path ?: return null
        return FileInputStream(path)
    }
    return context.contentResolver.openInputStream(uri)
}

/** Average colour of the lower part of the art, toned down so white text stays readable. */
private suspend fun sampleArtworkColor(context: Context, uriString: String?): Color {
    if (uriString.isNullOrBlank()) return TINT
    return withContext(Dispatchers.IO) {
        try {
            // 1) read the size only
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            val s1 = openArtwork(context, uriString)
            if (s1 != null) {
                s1.use { BitmapFactory.decodeStream(it, null, bounds) }
            }
            var sample = 1
            while (bounds.outWidth / sample > 96 && bounds.outHeight / sample > 96) sample *= 2

            // 2) decode a small copy
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val s2 = openArtwork(context, uriString)
            val bmp = if (s2 != null) s2.use { BitmapFactory.decodeStream(it, null, opts) } else null
            if (bmp == null) {
                TINT
            } else {
                // average the bottom 40 % of the picture
                val w = bmp.width
                val h = bmp.height
                var startY = (h * 0.6f).toInt()
                if (startY > h - 1) startY = h - 1
                if (startY < 0) startY = 0
                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                var count = 0L
                for (y in startY until h) {
                    for (x in 0 until w) {
                        val px = bmp.getPixel(x, y)
                        sumR += ((px shr 16) and 0xFF).toLong()
                        sumG += ((px shr 8) and 0xFF).toLong()
                        sumB += (px and 0xFF).toLong()
                        count++
                    }
                }
                if (count == 0L) {
                    TINT
                } else {
                    var cr = sumR.toFloat() / 255f / count.toFloat()
                    var cg = sumG.toFloat() / 255f / count.toFloat()
                    var cb = sumB.toFloat() / 255f / count.toFloat()
                    // keep it dark enough for white text, and never pitch black
                    val lum = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
                    val maxLum = 0.36f
                    val minLum = 0.10f
                    var k = 1f
                    if (lum > maxLum) k = maxLum / lum
                    else if (lum < minLum && lum > 0.001f) k = minLum / lum
                    cr = (cr * k).coerceIn(0f, 1f)
                    cg = (cg * k).coerceIn(0f, 1f)
                    cb = (cb * k).coerceIn(0f, 1f)
                    Color(red = cr, green = cg, blue = cb, alpha = 1f)
                }
            }
        } catch (e: Exception) {
            TINT
        }
    }
}

@Composable
private fun rememberArtworkColor(artworkUri: String?): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(TINT) }
    LaunchedEffect(artworkUri) {
        target = sampleArtworkColor(context, artworkUri)
    }
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = ART_FADE_MS),
        label = "artwork_color"
    )
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
    val deeper = Color(red = base.red * 0.80f, green = base.green * 0.80f, blue = base.blue * 0.80f, alpha = 1f)

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
                var zone = fadePx / size.height
                if (zone < 0.01f) zone = 0.01f
                if (zone > 0.99f) zone = 0.99f
                val stops = ArrayList<Pair<Float, Color>>()
                for (i in FADE_POSITIONS.indices) {
                    stops.add(Pair(FADE_POSITIONS[i] * zone, Color.Black.copy(alpha = FADE_ALPHAS[i])))
                }
                stops.add(Pair(1f, Color.Black))
                drawRect(
                    brush = Brush.verticalGradient(colorStops = stops.toTypedArray()),
                    blendMode = BlendMode.DstIn
                )
            }
            .background(Brush.verticalGradient(colors = listOf(base, base, deeper)))
    )
}
