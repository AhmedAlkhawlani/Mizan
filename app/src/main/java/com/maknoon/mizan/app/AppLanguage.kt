package com.maknoon.mizan.app

import android.content.Context
import android.content.res.Configuration
import android.view.View
import java.util.Locale

/**
 * Arabic is a first language, not a translation (ADR-008).
 *
 * It stays the default even on a phone set to another language, which also makes the interface
 * right-to-left from the first frame instead of only the Arabic strings. [LanguagePreference]
 * lets the operator pick English or follow the system locale afterwards.
 */
object AppLanguage {
    const val ARABIC = "ar"
    const val ENGLISH = "en"
    const val DEFAULT_LANGUAGE_TAG = ARABIC

    /**
     * The context a non-activity caller should resolve strings with, so notifications, the tile
     * and the widgets follow the operator's choice instead of the phone's language.
     *
     * It does not touch the process-wide default locale: these calls run on background
     * threads, and flipping a global from there would leak into unrelated formatting.
     */
    fun localized(base: Context): Context {
        val tag = LanguagePreference.resolvedTag(LanguagePreference.stored(base)) ?: return base
        return contextFor(base, tag)
    }

    /**
     * Whether the interface reads right-to-left.
     *
     * The localized context swaps the strings, but the window keeps the system's `ldrtl` flag on
     * some builds, so the direction is derived from the choice itself instead of from that flag.
     */
    fun isRtl(context: Context): Boolean = isRtl(
        stored = LanguagePreference.stored(context),
        systemRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
    )

    internal fun isRtl(stored: String, systemRtl: Boolean): Boolean =
        when (LanguagePreference.resolvedTag(stored)) {
            ENGLISH -> false
            ARABIC -> true
            else -> systemRtl
        }

    private fun contextFor(base: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
