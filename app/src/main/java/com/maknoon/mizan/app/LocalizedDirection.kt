package com.maknoon.mizan.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Sets Compose's layout direction from the stored language choice.
 *
 * Swapping the locale through `attachBaseContext` changes the strings, but a phone whose system
 * language is Arabic keeps the window's right-to-left flag, so an English interface would still
 * be mirrored: text aligned to the right and punctuation at the wrong end. This pins the
 * direction to the language actually on screen.
 */
@Composable
fun LocalizedDirection(content: @Composable () -> Unit) {
    val rtl = AppLanguage.isRtl(LocalContext.current)
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}
