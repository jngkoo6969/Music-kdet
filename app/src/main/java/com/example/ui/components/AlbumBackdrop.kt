package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Album background style (ported from the HTML demo).
 *
 *  - TOP    : sharp album art that dissolves with a progressive blur.
 *  - BOTTOM : the bottom slice of the art, flipped + stretched, heavily blurred
 *             (4 progressive layers), darkened, and faded in under the art.
 *
 * Screen fractions (same names as the CSS variables):
 *   ART_FRACTION      = --Hn    (55 %)  sharp art height
 *   OVERLAP_FRACTION  = --On    (14 %)  how far the blurred half tucks up under the art
 *   SLICE_FRACTION    = --slice (30 %)  part of the art (from its bottom) used for the blur
 */
const val ART_FRACTION = 0.55f // 55 / 45 split: art on top, controls below
private const val OVERLAP_FRACTION = 0.14f
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

// Progressive blur in the lower half (like .l1 .l2 .l3 .l4)
private val REFLECTION_BLUR_STEPS = listOf(
    BlurStep(56.dp, alphaMask(0f to 1f, 1f to 1f))
)

// Fade-in of the whole lower half (the .reflection mask)
private val REFLECTION_FADE_IN = alphaMask(
    0f to 0f, 0.12f to 0.50f, 0.30f to 0.90f, 0.50f to 1f, 1f to 1f
)

// Soft darkening toward the bottom (the .reflection::after tint)
private val REFLECTION_TINT = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to TINT.copy(alpha = 0f),
        0.40f to TINT.copy(alpha = 0.22f),
        1.00f to TINT.copy(alpha = 0.50f)
    )
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
    Box(modifier = modifier.alphaMasked(ART_FADE)) {
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
// BOTTOM: heavily blurred, darkened copy of the art
// =============================================================================
/**
 * Place this inside the controls area (the box that starts where the sharp art ends).
 * It pulls itself up by OVERLAP_FRACTION so it tucks under the art.
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
    val reflectionHeight = screenHeight * (1f - ART_FRACTION + OVERLAP_FRACTION)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .height(reflectionHeight)
            .offset(y = -overlap)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // brightness(.82)-style darkening, only where there is content
                drawRect(Color.Black.copy(alpha = 0.18f), blendMode = BlendMode.SrcAtop)
                drawRect(brush = REFLECTION_TINT)
                drawRect(brush = REFLECTION_FADE_IN, blendMode = BlendMode.DstIn)
            }
    ) {
        if (canBlur) {
            REFLECTION_BLUR_STEPS.forEach { step ->
                BlurredArtLayer(artworkUri, step.radius, step.mask, 1f)
            }
        } else {
            // No blur support below API 31: one soft, faded copy instead.
            BlurredArtLayer(artworkUri, 0.dp, alphaMask(0f to 1f, 1f to 1f), 0.45f)
        }
    }
}

@Composable
private fun BlurredArtLayer(
    artworkUri: String?,
    blurRadius: Dp,
    mask: Brush,
    alpha: Float
) {
    val blurModifier = if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alphaMasked(mask)
            .graphicsLayer { this.alpha = alpha }
            .then(blurModifier)
    ) {
        ArtworkView(
            artworkUri = artworkUri,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0.dp,
            fadeMillis = ART_FADE_MS
        )
    }
}
