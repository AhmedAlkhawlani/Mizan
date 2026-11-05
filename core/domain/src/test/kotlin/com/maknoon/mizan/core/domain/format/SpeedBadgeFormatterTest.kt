package com.maknoon.mizan.core.domain.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeedBadgeFormatterTest {

    @Test
    fun `zero speed renders as zero bytes`() {
        assertThat(SpeedBadgeFormatter.of(0.0)).isEqualTo(SpeedBadge("0", "B"))
    }

    @Test
    fun `negative speed clamps to zero`() {
        assertThat(SpeedBadgeFormatter.of(-2_048.0)).isEqualTo(SpeedBadge("0", "B"))
    }

    @Test
    fun `kilobytes show a whole number without a unit suffix`() {
        assertThat(SpeedBadgeFormatter.of(45_000.0)).isEqualTo(SpeedBadge("45", "K"))
    }

    @Test
    fun `megabytes round down to a whole number`() {
        assertThat(SpeedBadgeFormatter.of(2_400_000.0)).isEqualTo(SpeedBadge("2", "M"))
        assertThat(SpeedBadgeFormatter.of(2_900_000.0)).isEqualTo(SpeedBadge("2", "M"))
    }

    @Test
    fun `sub-kilobyte rates stay in bytes`() {
        assertThat(SpeedBadgeFormatter.of(345.0)).isEqualTo(SpeedBadge("345", "B"))
    }

    @Test
    fun `a badge never carries a decimal point or a grouping separator`() {
        val rates = listOf(0.0, 9.0, 345.0, 999.6, 45_000.0, 2_400_000.0, 12_000_000_000.0)
        assertThat(rates.map { SpeedBadgeFormatter.of(it).value })
            .containsExactly("0", "9", "345", "1", "45", "2", "12").inOrder()
    }

    @Test
    fun `three digit kilobytes stay in kilobytes`() {
        assertThat(SpeedBadgeFormatter.of(150_000.0)).isEqualTo(SpeedBadge("150", "K"))
    }

    @Test
    fun `rounding up to one thousand carries into the next unit`() {
        assertThat(SpeedBadgeFormatter.of(999_600.0)).isEqualTo(SpeedBadge("1", "M"))
    }

    @Test
    fun `gigabytes are the largest unit and never carry`() {
        assertThat(SpeedBadgeFormatter.of(12_000_000_000.0)).isEqualTo(SpeedBadge("12", "G"))
        assertThat(SpeedBadgeFormatter.of(9_999_000_000_000.0)).isEqualTo(SpeedBadge("9999", "G"))
    }

    @Test
    fun `the spelled-out unit keeps the byte case literal`() {
        assertThat(listOf(345.0, 45_000.0, 2_400_000.0, 12_000_000_000.0)
            .map { SpeedBadgeFormatter.of(it).fullUnit })
            .containsExactly("B/s", "KB/s", "MB/s", "GB/s").inOrder()
    }

    @Test
    fun `quiet mode hides a bytes-only reading`() {
        assertThat(SpeedBadgeFormatter.iconBadge(320.0, showBytes = false)).isNull()
        assertThat(SpeedBadgeFormatter.iconBadge(0.0, showBytes = false)).isNull()
    }

    @Test
    fun `showing bytes keeps the byte badge`() {
        assertThat(SpeedBadgeFormatter.iconBadge(320.0, showBytes = true))
            .isEqualTo(SpeedBadge("320", "B"))
    }

    @Test
    fun `quiet mode still shows anything at a kilobyte or above`() {
        assertThat(SpeedBadgeFormatter.iconBadge(45_000.0, showBytes = false))
            .isEqualTo(SpeedBadge("45", "K"))
        assertThat(SpeedBadgeFormatter.iconBadge(2_400_000.0, showBytes = false))
            .isEqualTo(SpeedBadge("2", "M"))
    }

    @Test
    fun `a byte rate that carries into kilobytes is shown`() {
        assertThat(SpeedBadgeFormatter.iconBadge(999.6, showBytes = false))
            .isEqualTo(SpeedBadge("1", "K"))
    }
}
