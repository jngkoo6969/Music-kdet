package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Slider with a thin, flat bar. While you touch/drag it the bar grows thick
 * (Apple Music style); when you let go it shrinks back to flat.
 * Use it for both the song position and the volume.
 */
@Composable
fun ExpandingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    flatHeight: Dp = 4.dp,
    expandedHeight: Dp = 14.dp,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.2f)
) {
    var pressed by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(
        targetValue = if (pressed) expandedHeight else flatHeight,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "slider_track_height"
    )

    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val currentRange by rememberUpdatedState(valueRange)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp) // generous touch area; the visible bar is much thinner
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true

                    fun report(x: Float) {
                        val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        val range = currentRange
                        currentOnChange(range.start + fraction * (range.endInclusive - range.start))
                    }

                    report(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        report(change.position.x)
                        change.consume()
                    }
                    pressed = false
                    currentOnFinished()
                }
            }
    ) {
        val range = valueRange.endInclusive - valueRange.start
        val fraction = if (range <= 0f) 0f else ((value - valueRange.start) / range).coerceIn(0f, 1f)
        val barHeight = trackHeight.toPx()
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)

        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = radius
        )
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, top),
            size = Size(size.width * fraction, barHeight),
            cornerRadius = radius
        )
    }
}
