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

    @Test
    fun `direction follows the chosen language, not the system`() {
        assertThat(AppLanguage.isRtl(AppLanguage.ARABIC, systemRtl = false)).isTrue()
        assertThat(AppLanguage.isRtl(AppLanguage.ENGLISH, systemRtl = true)).isFalse()
    }

    @Test
    fun `system direction follows the phone`() {
        assertThat(AppLanguage.isRtl(LanguagePreference.SYSTEM, systemRtl = true)).isTrue()
        assertThat(AppLanguage.isRtl(LanguagePreference.SYSTEM, systemRtl = false)).isFalse()
    }
}
