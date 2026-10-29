package com.example.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/** Screens add this to their top/bottom padding so content is not hidden behind the top/bottom bars. */
val LocalBarPadding = compositionLocalOf { PaddingValues(0.dp) }

/**
 * Normal block (card). Its colour is MaterialTheme.colorScheme.surface, which the user can change in
 * Settings -> Colours. [tint] washes the block with a colour (e.g. red for voided orders).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    tint: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = MaterialTheme.colorScheme.surface
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (tint == Color.Unspecified) base else tint.compositeOver(base),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content
    )
}

/** "#RRGGBB" (or "RRGGBB") -> "#RRGGBB" upper-case, or null if it is not a valid 6-digit hex colour. */
fun normalizeHex(input: String): String? {
    val h = input.trim().removePrefix("#")
    if (h.length != 6 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return "#" + h.uppercase()
}

/** Parses a colour saved in Settings. Empty / invalid = null, which means "use the default colour". */
fun parseHexColor(input: String): Color? {
    val h = normalizeHex(input) ?: return null
    return Color(("FF" + h.removePrefix("#")).toLong(16))
}
