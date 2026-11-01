package com.maknoon.mizan.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Arabic is a first language, not a translation (ADR-008).
 *
 * The app therefore runs in Arabic even on a device set to another language, which also makes
 * the whole interface right-to-left from the first frame instead of only the Arabic strings.
 * The language tag is a parameter so the settings screen can switch it later; until then it
 * stays at the default.
 */
object AppLanguage {
    const val ARABIC = "ar"
    const val DEFAULT_LANGUAGE_TAG = ARABIC

    /** Returns a context whose resources resolve [languageTag] and whose direction follows it. */
    fun wrapContext(base: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
