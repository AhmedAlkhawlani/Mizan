package com.maknoon.mizan.core.domain.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BidiTextTest {

    @Test
    fun `value is wrapped in a left-to-right isolate`() {
        assertThat(isolateLtr("3.96 GB")).isEqualTo("\u20663.96 GB\u2069")
    }

    @Test
    fun `wrapping keeps the value itself untouched`() {
        val value = ByteFormatter.data(2_450_000_000)

        assertThat(isolateLtr(value).removeSurrounding("\u2066", "\u2069")).isEqualTo(value)
    }
}
