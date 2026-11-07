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

    @Test
    fun `a label is wrapped in a first-strong isolate, not a forced direction`() {
        // Forcing LTR would break an Arabic app name; the first strong character must decide.
        assertThat(isolateFirstStrong("1DM+")).isEqualTo("\u20681DM+\u2069")
    }

    @Test
    fun `isolating a label leaves its characters untouched`() {
        val label = "1DM+"

        assertThat(isolateFirstStrong(label).removeSurrounding("\u2068", "\u2069"))
            .isEqualTo(label)
    }

    @Test
    fun `an arabic label keeps its own characters`() {
        val label = "واتساب"

        assertThat(isolateFirstStrong(label).removeSurrounding("\u2068", "\u2069"))
            .isEqualTo(label)
    }
}
