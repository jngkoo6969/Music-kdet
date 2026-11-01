package com.example.ui.components

import android.graphics.drawable.BitmapDrawable
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Album background style (ported from the HTML demo).
 *
 *  - TOP    : sharp album art that dissolves with a progressive blur.
 *  - BOTTOM : a solid colour taken from the art itself, faded in under the art
 *             (Apple Music style), so the picture melts into the controls.
 *
 * Screen fractions (same names as the CSS variables):
 *   ART_FRACTION      = --Hn    (55 %)  sharp art height
 *   OVERLAP_FRACTION  = --On    (14 %)  how far the blurred half tucks up under the art
 *   SLICE_FRACTION    = --slice (30 %)  part of the art (from its bottom) used for the blur
 */
const val ART_FRACTION = 0.58f // art on top (a bit taller so its fade ends right above the title)
// Where the picture (and its fade) ends, as a fraction of the screen height.
// Layout of the controls still uses ART_FRACTION, so text does not move.
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

// ---- Masks: sharp art ------------------------------------------------------
private val ART_FADE = alphaMask(
    0f to 1f, 0.80f to 1f, 0.87f to 0.80f, 0.93f to 0.50f,
    0.97f to 0.20f, 1f to 0f
)

private class BlurStep(val radius: Dp, val mask: Brush)

// Progressive blur ON the art (like .ob1 .ob2 .ob3)
private val ART_BLUR_STEPS = listOf(
    BlurStep(3.dp, alphaMask(0f to 0f, 0.74f to 0f, 0.86f to 1f, 1f to 1f)),
    BlurStep(8.dp, alphaMask(0f to 0f, 0.82f to 0f, 0.92f to 1f, 1f to 1f)),
    BlurStep(16.dp, alphaMask(0f to 0f, 0.88f to 0f, 0.96f to 1f, 1f to 1f)),
    BlurStep(28.dp, alphaMask(0f to 0f, 0.93f to 0f, 0.99f to 1f, 1f to 1f))
)

// Fade-in of the colour overlay (reaches full strength where the art ends)
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

/** Draws [content] into its own layer, then multiplies its alpha by [mask]. */
private fun Modifier.alphaMasked(mask: Brush): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(brush = mask, blendMode = BlendMode.DstIn)
        }

// =============================================================================
// TOP: sharp art + progressive blur that dissolves into the lower half
// =============================================================================
@Composable
fun AlbumArtTop(
    artworkUri: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                // keep the layout size, but draw the picture down to IMAGE_END_FRACTION
                val h = (constraints.maxHeight * IMAGE_END_FRACTION / ART_FRACTION).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(0, 0) }
            }
            .alphaMasked(ART_FADE)
    ) {
        ArtworkView(
            artworkUri = artworkUri,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0.dp,
            fadeMillis = ART_FADE_MS
        )
        // Blur needs Android 12+ (API 31). Older phones just keep the sharp art + fade.
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
// BOTTOM: solid colour sampled from the art, fading in under/over its lower edge
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
 * Place this inside the controls area (the box that starts where the sharp art ends).
 * It pulls itself up by OVERLAP_FRACTION so it tucks over the art's blurred bottom edge.
 *
 * @param artHeight the measured height of the sharp art area (= ART_FRACTION of the screen)
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
    val deeper = Color(base.red * 0.78f, base.green * 0.78f, base.blue * 0.78f)

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
            .background(Brush.verticalGradient(listOf(base, base, deeper)))
    )
}
