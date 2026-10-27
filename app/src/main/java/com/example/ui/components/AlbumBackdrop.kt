package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.ui.graphics.TransformOrigin
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
 *   ART_FRACTION      = --Hn    (50 %)  sharp art height
 *   OVERLAP_FRACTION  = --On    (14 %)  how far the blurred half tucks up under the art
 *   SLICE_FRACTION    = --slice (30 %)  part of the art (from its bottom) used for the blur
 */
const val ART_FRACTION = 0.50f
private const val OVERLAP_FRACTION = 0.14f
private const val SLICE_FRACTION = 0.30f
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
    0f to 1f, 0.58f to 1f, 0.68f to 0.85f, 0.79f to 0.55f,
    0.89f to 0.25f, 0.96f to 0.07f, 1f to 0f
)

private class BlurStep(val radius: Dp, val mask: Brush)

// Progressive blur ON the art (like .ob1 .ob2 .ob3)
private val ART_BLUR_STEPS = listOf(
    BlurStep(1.5.dp, alphaMask(0f to 0f, 0.38f to 0f, 0.58f to 1f, 1f to 1f)),
    BlurStep(4.dp, alphaMask(0f to 0f, 0.56f to 0f, 0.76f to 1f, 1f to 1f)),
    BlurStep(9.dp, alphaMask(0f to 0f, 0.74f to 0f, 0.94f to 1f, 1f to 1f))
)

// Progressive blur in the lower half (like .l1 .l2 .l3 .l4)
private val REFLECTION_BLUR_STEPS = listOf(
    BlurStep(6.dp, alphaMask(0f to 1f, 0.20f to 1f, 0.40f to 0f, 1f to 0f)),
    BlurStep(16.dp, alphaMask(0f to 0f, 0.15f to 0f, 0.35f to 1f, 0.42f to 1f, 0.60f to 0f, 1f to 0f)),
    BlurStep(30.dp, alphaMask(0f to 0f, 0.35f to 0f, 0.55f to 1f, 0.62f to 1f, 0.80f to 0f, 1f to 0f)),
    BlurStep(46.dp, alphaMask(0f to 0f, 0.55f to 0f, 0.80f to 1f, 1f to 1f))
)

// Fade-in of the whole lower half (the .reflection mask)
private val REFLECTION_FADE_IN = alphaMask(
    0f to 0f, 0.10f to 0.45f, 0.22f to 0.80f, 0.38f to 0.96f, 0.55f to 1f, 1f to 1f
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
// BOTTOM: flipped, stretched, heavily blurred copy of the art's bottom slice
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
    // How much the art slice is stretched vertically to fill the lower half (CSS --k)
    val stretch = reflectionHeight / (artHeight * SLICE_FRACTION)

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
                FlippedArtLayer(artworkUri, artHeight, stretch, step.radius, step.mask, 1f)
            }
        } else {
            // No blur support below API 31: one soft, faded copy instead.
            FlippedArtLayer(artworkUri, artHeight, stretch, 0.dp, alphaMask(0f to 1f, 1f to 1f), 0.45f)
        }
    }
}

@Composable
private fun FlippedArtLayer(
    artworkUri: String?,
    artHeight: Dp,
    stretch: Float,
    blurRadius: Dp,
    mask: Brush,
    alpha: Float
) {
    val blurModifier = if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alphaMasked(mask)
            .then(blurModifier)
    ) {
        // A full-size copy of the art, placed so its BOTTOM edge sits on the top of this box,
        // then mirrored (scaleY < 0) around that edge and stretched downward.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(artHeight)
                .align(Alignment.TopStart)
                .offset(y = -artHeight)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = -stretch
                    this.alpha = alpha
                }
        ) {
            ArtworkView(
                artworkUri = artworkUri,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp,
                fadeMillis = ART_FADE_MS
            )
        }
    }
}
