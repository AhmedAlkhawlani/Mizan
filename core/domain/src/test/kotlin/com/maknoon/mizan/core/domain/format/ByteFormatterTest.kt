package com.maknoon.mizan.core.domain.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.After
import org.junit.Test

class ByteFormatterTest {

    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `zero bytes renders as zero B`() {
        assertThat(ByteFormatter.data(0)).isEqualTo("0 B")
    }

    @Test
    fun `negative bytes clamp to zero instead of showing a negative number`() {
        assertThat(ByteFormatter.data(-4_096)).isEqualTo("0 B")
    }

    @Test
    fun `bytes below one thousand keep the byte unit`() {
        assertThat(ByteFormatter.data(1)).isEqualTo("1 B")
        assertThat(ByteFormatter.data(999)).isEqualTo("999 B")
    }

    @Test
    fun `exactly one thousand bytes is one kilobyte`() {
        assertThat(ByteFormatter.data(1_000)).isEqualTo("1 KB")
    }

    @Test
    fun `fractional kilobytes keep one decimal`() {
        assertThat(ByteFormatter.data(1_500)).isEqualTo("1.5 KB")
    }

    @Test
    fun `exactly one million bytes is one megabyte`() {
        assertThat(ByteFormatter.data(1_000_000)).isEqualTo("1 MB")
    }

    @Test
    fun `gigabytes keep two decimals`() {
        assertThat(ByteFormatter.data(2_450_000_000)).isEqualTo("2.45 GB")
    }

    @Test
    fun `values above one hundred in a unit drop to a single decimal`() {
        assertThat(ByteFormatter.data(123_456_789)).isEqualTo("123.5 MB")
    }

    @Test
    fun `rounding carries into the next unit instead of showing 1000 of a unit`() {
        assertThat(ByteFormatter.data(999_999)).isEqualTo("1 MB")
        assertThat(ByteFormatter.data(999_999_999)).isEqualTo("1 GB")
    }

    @Test
    fun `terabytes use decimal units`() {
        assertThat(ByteFormatter.data(1_000_000_000_000)).isEqualTo("1 TB")
    }

    @Test
    fun `the largest possible byte count stays within the supported units`() {
        // Long.MAX_VALUE is about 9.22 exabytes; the unit list stops at PB, so the value
        // is expressed in petabytes rather than silently overflowing to a wrong unit.
        assertThat(ByteFormatter.data(Long.MAX_VALUE)).isEqualTo("9223.4 PB")
    }

    @Test
    fun `zero rate renders as zero bytes per second`() {
        assertThat(ByteFormatter.rate(0.0)).isEqualTo("0 B/s")
    }

    @Test
    fun `negative rate clamps to zero`() {
        assertThat(ByteFormatter.rate(-12.5)).isEqualTo("0 B/s")
    }

    @Test
    fun `rates keep one decimal below one hundred megabytes`() {
        assertThat(ByteFormatter.rate(12_400_000.0)).isEqualTo("12.4 MB/s")
        assertThat(ByteFormatter.rate(1_200_000.0)).isEqualTo("1.2 MB/s")
    }

    @Test
    fun `sub-byte rates round down to zero`() {
        assertThat(ByteFormatter.rate(0.4)).isEqualTo("0 B/s")
    }

    @Test
    fun `byte rates use the byte unit`() {
        assertThat(ByteFormatter.rate(512.0)).isEqualTo("512 B/s")
    }

    @Test
    fun `digits stay western ascii under an arabic default locale`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        assertThat(ByteFormatter.data(2_450_000_000)).isEqualTo("2.45 GB")
        assertThat(ByteFormatter.rate(12_400_000.0)).isEqualTo("12.4 MB/s")
    }
}
