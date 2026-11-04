package com.maknoon.mizan.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF083344),
    secondary = Color(0xFFBE185D),
    onSecondary = Color.White,
    tertiary = Color(0xFF047857),
    onTertiary = Color.White,
    error = Color(0xFFB91C1C),
    onError = Color.White,
)

/**
 * The concept board's chrome: near-black background, graphite panels and hairline borders, so
 * every screen — not only the Guard tab — reads as one dark instrument panel.
 */
private val DarkColorScheme = darkColorScheme(
    primary = MizanPalette.DataDown,
    onPrimary = Color(0xFF083344),
    primaryContainer = Color(0xFF155E75),
    onPrimaryContainer = Color(0xFFCFFAFE),
    secondary = MizanPalette.DataUp,
    onSecondary = Color(0xFF500724),
    secondaryContainer = Color(0xFF831843),
    onSecondaryContainer = Color(0xFFFCE7F3),
    tertiary = MizanPalette.Guard,
    onTertiary = Color(0xFF022C22),
    background = Color(0xFF07090D),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF11151C),
    onSurface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFF161C25),
    onSurfaceVariant = Color(0xFF8A93A6),
    outline = Color(0x1FFFFFFF),
    surfaceContainerLowest = Color(0xFF05070A),
    surfaceContainerLow = Color(0xFF0B0E13),
    surfaceContainer = Color(0xFF0B0E13),
    surfaceContainerHigh = Color(0xFF11151C),
    surfaceContainerHighest = Color(0xFF161C25),
    error = MizanPalette.Danger,
    onError = Color(0xFF450A0A),
)

/**
 * The semantic status colours of the current theme, for example
 * `MaterialTheme.statusColors.warning`.
 */
val MaterialTheme.statusColors: MizanStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMizanStatusColors.current

/**
 * Mizan's Material 3 theme.
 *
 * [dynamicColor] follows docs/04: on Android 12 and later the scheme can adopt the user's
 * wallpaper colours, while the status colours stay fixed to the brand so data keeps its
 * meaning.
 */
@Composable
fun MizanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalMizanStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}
