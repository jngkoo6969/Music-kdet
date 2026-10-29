package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = Color(0xFF003833),
    primaryContainer = SipTealPrimary,
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = DarkOceanBlue,
    onSecondary = Color(0xFF00344F),
    secondaryContainer = SipOceanBlueDark,
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = SipErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SipTealPrimary,
    onPrimary = Color.White,
    primaryContainer = SipTealContainer,
    onPrimaryContainer = SipOnTealContainer,
    secondary = SipOceanBlue,
    onSecondary = Color.White,
    secondaryContainer = SipOceanBlueLight,
    onSecondaryContainer = SipOceanBlueDark,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = SipErrorRed,
    onError = Color.White
)

@Composable
fun SipShopTheme(
    themePreference: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    dynamicColor: Boolean = false, // Set false to prioritize authentic Sip Shop branding
    fontScale: Float = 1.0f,       // user text size multiplier
    spacingScale: Float = 1.0f,    // user layout/spacing multiplier (scales every dp)
    backgroundColor: String = "",  // user colours from Settings -> Colours ("" = default)
    blockColor: String = "",
    textColor: String = "",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePreference) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // User colours (Settings -> Colours). Anything left on "Default" keeps the normal theme colour.
    val colorScheme = remember(baseScheme, backgroundColor, blockColor, textColor) {
        var cs = baseScheme
        parseHexColor(backgroundColor)?.let { cs = cs.copy(background = it) }
        parseHexColor(textColor)?.let {
            cs = cs.copy(
                onBackground = it,
                onSurface = it,
                onSurfaceVariant = it.copy(alpha = 0.72f)
            )
        }
        parseHexColor(blockColor)?.let {
            val variant = lerp(it, cs.onSurface, 0.07f)
            cs = cs.copy(
                surface = it,
                surfaceVariant = variant,
                surfaceContainerLowest = it,
                surfaceContainerLow = it,
                surfaceContainer = it,
                surfaceContainerHigh = it,
                surfaceContainerHighest = variant
            )
        }
        cs
    }

    // Responsive scaling: shrink layout + text on narrow / "large display size" phones,
    // cap the system font scale so huge system fonts can't break the layout, then apply the
    // user's own sliders from Settings on top.
    val base = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val density = remember(base.density, base.fontScale, widthDp, fontScale, spacingScale) {
        val auto = (widthDp / 400f).coerceIn(0.82f, 1.0f)          // 1.0 on normal phones, smaller on narrow ones
        val layout = auto * spacingScale
        val systemFont = base.fontScale.coerceIn(0.85f, 1.15f)       // cap system font scale
        val textMultiplier = systemFont * auto * fontScale
        Density(
            density = base.density * layout,
            fontScale = textMultiplier / layout   // sp -> px = density * fontScale = text multiplier only
        )
    }

    val glassShapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(30.dp)
    )

    CompositionLocalProvider(
        LocalDensity provides density
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = glassShapes,
            content = content
        )
    }
}
 
