package com.maknoon.mizan.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanguagePreferenceTest {

    @Test
    fun `arabic resolves to arabic`() {
        assertThat(LanguagePreference.resolvedTag(AppLanguage.ARABIC)).isEqualTo("ar")
    }

    @Test
    fun `english resolves to english`() {
        assertThat(LanguagePreference.resolvedTag(AppLanguage.ENGLISH)).isEqualTo("en")
    }

    @Test
    fun `system resolves to no wrapper`() {
        assertThat(LanguagePreference.resolvedTag(LanguagePreference.SYSTEM)).isNull()
    }

    @Test
    fun `unknown tag falls back to arabic`() {
        assertThat(LanguagePreference.resolvedTag("fr")).isEqualTo("ar")
        assertThat(LanguagePreference.resolvedTag("")).isEqualTo("ar")
    }
}
