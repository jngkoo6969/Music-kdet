package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
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
 *  - TOP    : the album art. It is drawn down to IMAGE_END_FRACTION of the screen height
 *             (the line, just above the artist name). Only its lower edge gets a soft blur.
 *  - BOTTOM : one smooth colour taken from the art itself, faded in over the lower part of
 *             the art and solid exactly at the line, so the picture melts into the controls.
 *
 * ART_FRACTION is the layout split used by NowPlayingSheet (so the text stays where it is).
 * IMAGE_END_FRACTION is where the picture visually ends. Change it to move the line.
 */
const val ART_FRACTION = 0.58f
private const val IMAGE_END_FRACTION = 0.63f
private const val OVERLAP_FRACTION = 0.10f
private const val ART_FADE_MS = 900

private val TINT = Color(10, 10, 14)

/** Builds an alpha mask: each pair is (position 0..1, alpha 0..1). */
private fun alphaMask(vararg stops: Pair<Float, Float>): Brush =
    Brush.verticalGradient(
        colorStops = Array(stops.size) { i ->
            stops[i].first to Color.Black.copy(alpha = stops[i].second)
        }
    )

private class BlurStep(val radius: Dp, val mask: Brush)

// Progressive blur ON the art, only near its bottom edge (the face stays sharp)
private val ART_BLUR_STEPS = listOf(
    BlurStep(3.dp, alphaMask(0f to 0f, 0.74f to 0f, 0.86f to 1f, 1f to 1f)),
    BlurStep(8.dp, alphaMask(0f to 0f, 0.82f to 0f, 0.92f to 1f, 1f to 1f)),
    BlurStep(16.dp, alphaMask(0f to 0f, 0.88f to 0f, 0.96f to 1f, 1f to 1f)),
    BlurStep(28.dp, alphaMask(0f to 0f, 0.93f to 0f, 0.99f to 1f, 1f to 1f))
)

// Fade-in of the colour layer (reaches full strength exactly at the line)
private val OVERLAP_IN_REFLECTION =
    OVERLAP_FRACTION / (1f - IMAGE_END_FRACTION + OVERLAP_FRACTION)

private val REFLECTION_FADE_IN = alphaMask(
    0f to 0f,
    OVERLAP_IN_REFLECTION * 0.30f to 0.25f,
    OVERLAP_IN_REFLECTION * 0.60f to 0.60f,
    OVERLAP_IN_REFLECTION * 0.85f to 0.88f,
    OVERLAP_IN_REFLECTION to 1f,
    1f to 1f
)

private val canBlur: Boolean get() = Build.VERSION.SDK_INT >= 31

/** Draws content into its own layer, then multiplies its alpha by [mask]. */
private fun Modifier.alphaMasked(mask: Brush): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(brush = mask, blendMode = BlendMode.DstIn)
        }

// =============================================================================
// TOP: album art, drawn down to the line, soft blur on its lower edge
// =============================================================================
@Composable
fun AlbumArtTop(
    artworkUri: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.layout { measurable, constraints ->
            // keep the layout size, but draw the picture down to IMAGE_END_FRACTION
            val h = (constraints.maxHeight * IMAGE_END_FRACTION / ART_FRACTION).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(0, 0) }
        }
    ) {
        ArtworkView(
            artworkUri = artworkUri,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0.dp,
            fadeMillis = ART_FADE_MS
        )
        // Blur needs Android 12+ (API 31). Older phones just keep the sharp art.
        if (canBlur) {
            ART_BLUR_STEPS.forEach { step ->
                Box(modifier = Modifier.fillMaxSize().alphaMasked(step.mask)) {
                    ArtworkView(
                        artworkUri = artworkUri,
                        modifier = Modifier.fillMaxSize().blur(step.radius),
                        cornerRadius = 0.dp,
                        fadeMillis = ART_FADE_MS
                    )
                }
            }
        }
    }
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
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            val s1 = openArtwork(context, uriString)
            if (s1 != null) {
                s1.use { BitmapFactory.decodeStream(it, null, bounds) }
            }
            var sample = 1
            while (bounds.outWidth / sample > 96 && bounds.outHeight / sample > 96) sample *= 2

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
 * Place this inside the controls area (the box that starts where the layout split ends).
 * It reaches up over the art's blurred bottom edge and is solid exactly at IMAGE_END_FRACTION.
 *
 * @param artHeight the measured height of the art area (= ART_FRACTION of the screen)
 */
@Composable
fun AlbumReflection(
    artworkUri: String?,
    artHeight: Dp,
    modifier: Modifier = Modifier
) {
    val screenHeight = artHeight / ART_FRACTION
    val overlap = screenHeight * OVERLAP_FRACTION
    // the picture ends at IMAGE_END_FRACTION; the colour must be solid exactly there
    val extra = screenHeight * (IMAGE_END_FRACTION - ART_FRACTION)
    val reflectionHeight = screenHeight * (1f - IMAGE_END_FRACTION + OVERLAP_FRACTION)
    val base = rememberArtworkColor(artworkUri)
    val deeper = Color(red = base.red * 0.78f, green = base.green * 0.78f, blue = base.blue * 0.78f, alpha = 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .height(reflectionHeight)
            .offset(y = extra - overlap)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = REFLECTION_FADE_IN, blendMode = BlendMode.DstIn)
            }
            .background(Brush.verticalGradient(colors = listOf(base, base, deeper)))
    )
}
