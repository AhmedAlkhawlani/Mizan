package com.maknoon.mizan.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Mizan palette from docs/00: cyan for downloads, pink for uploads, amber for warnings,
 * red for overruns and green for protection. These are the exact brand values used on dark
 * surfaces and in charts.
 */
object MizanPalette {
    val DataDown = Color(0xFF22D3EE)
    val DataUp = Color(0xFFF472B6)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)
    val Guard = Color(0xFF34D399)
}

/**
 * Semantic colours for traffic figures and budget states.
 *
 * They are separate from the Material colour scheme because their meaning must survive
 * Material You: a budget bar turning amber always means "warning", even when the theme takes
 * its hues from the user's wallpaper. The light variants are darkened so text and bars keep at
 * least 4.5:1 contrast on white, as required by docs/04.
 */
@Immutable
data class MizanStatusColors(
    val dataDown: Color,
    val dataUp: Color,
    val warning: Color,
    val danger: Color,
    val guard: Color,
)

internal val LightStatusColors = MizanStatusColors(
    dataDown = Color(0xFF0E7490),
    dataUp = Color(0xFFBE185D),
    warning = Color(0xFFB45309),
    danger = Color(0xFFB91C1C),
    guard = Color(0xFF047857),
)

internal val DarkStatusColors = MizanStatusColors(
    dataDown = MizanPalette.DataDown,
    dataUp = MizanPalette.DataUp,
    warning = MizanPalette.Warning,
    danger = MizanPalette.Danger,
    guard = MizanPalette.Guard,
)

internal val LocalMizanStatusColors = staticCompositionLocalOf { LightStatusColors }
