package com.maknoon.mizan.app

import android.content.Context

/**
 * The operator's language choice, kept in plain preferences because it is read synchronously
 * in `attachBaseContext`, before any coroutine or DataStore could answer.
 *
 * Arabic stays the default so an existing install keeps the behaviour it was built around;
 * "system" opts into the phone's own locale, and anything unrecognised falls back to Arabic.
 */
object LanguagePreference {

    const val SYSTEM = "system"

    private const val FILE = "mizan_language"
    private const val KEY = "language_tag"
    private val SUPPORTED = setOf(AppLanguage.ARABIC, AppLanguage.ENGLISH)

    fun stored(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, AppLanguage.DEFAULT_LANGUAGE_TAG)
            .orEmpty()
            .ifEmpty { AppLanguage.DEFAULT_LANGUAGE_TAG }

    fun set(context: Context, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value)
            .apply()
    }

    /** Null means "follow the system locale", which needs no wrapped context. */
    fun resolvedTag(stored: String): String? = when (stored) {
        SYSTEM -> null
        in SUPPORTED -> stored
        else -> AppLanguage.DEFAULT_LANGUAGE_TAG
    }
}
