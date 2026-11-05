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
    fun `megabytes below ten keep one decimal`() {
        assertThat(SpeedBadgeFormatter.of(2_400_000.0)).isEqualTo(SpeedBadge("2.4", "M"))
    }

    @Test
    fun `a whole megabyte drops the decimal`() {
        assertThat(SpeedBadgeFormatter.of(2_000_000.0)).isEqualTo(SpeedBadge("2", "M"))
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
            .isEqualTo(SpeedBadge("2.4", "M"))
    }

    @Test
    fun `a byte rate that carries into kilobytes is shown`() {
        assertThat(SpeedBadgeFormatter.iconBadge(999.6, showBytes = false))
            .isEqualTo(SpeedBadge("1", "K"))
    }
}
