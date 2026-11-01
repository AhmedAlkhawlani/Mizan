package com.maknoon.mizan.core.domain.cycle

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class BillingCycleCalculatorTest {

    private fun epochDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).toEpochDay()

    @Test
    fun `a start day of one covers the whole calendar month`() {
        val cycle = BillingCycleCalculator.cycleFor(epochDay(2026, 3, 15), startDay = 1)

        assertThat(cycle.startEpochDay).isEqualTo(epochDay(2026, 3, 1))
        assertThat(cycle.endEpochDay).isEqualTo(epochDay(2026, 3, 31))
        assertThat(cycle.dayCount).isEqualTo(31)
    }

    @Test
    fun `the first of december ends on new year's eve`() {
        val cycle = BillingCycleCalculator.cycleFor(epochDay(2026, 12, 20), startDay = 1)

        assertThat(cycle.startEpochDay).isEqualTo(epochDay(2026, 12, 1))
        assertThat(cycle.endEpochDay).isEqualTo(epochDay(2026, 12, 31))
    }

    @Test
    fun `a day before the start day belongs to the previous month's cycle`() {
        val cycle = BillingCycleCalculator.cycleFor(epochDay(2026, 3, 14), startDay = 15)

        assertThat(cycle.startEpochDay).isEqualTo(epochDay(2026, 2, 15))
        assertThat(cycle.endEpochDay).isEqualTo(epochDay(2026, 3, 14))
    }

    @Test
    fun `the start day itself opens the new cycle`() {
        val cycle = BillingCycleCalculator.cycleFor(epochDay(2026, 3, 15), startDay = 15)

        assertThat(cycle.startEpochDay).isEqualTo(epochDay(2026, 3, 15))
        assertThat(cycle.endEpochDay).isEqualTo(epochDay(2026, 4, 14))
    }

    @Test
    fun `a cycle can span a short february without shifting its boundary`() {
        val beforeFebruaryStart = BillingCycleCalculator.cycleFor(epochDay(2026, 1, 31), startDay = 28)
        val onFebruaryStart = BillingCycleCalculator.cycleFor(epochDay(2026, 2, 28), startDay = 28)

        assertThat(beforeFebruaryStart.startEpochDay).isEqualTo(epochDay(2026, 1, 28))
        assertThat(beforeFebruaryStart.endEpochDay).isEqualTo(epochDay(2026, 2, 27))
        assertThat(onFebruaryStart.startEpochDay).isEqualTo(epochDay(2026, 2, 28))
        assertThat(onFebruaryStart.endEpochDay).isEqualTo(epochDay(2026, 3, 27))
        assertThat(onFebruaryStart.dayCount).isEqualTo(28)
    }

    @Test
    fun `a start day beyond the cap is treated as the twenty-eighth`() {
        val cycle = BillingCycleCalculator.cycleFor(epochDay(2026, 3, 31), startDay = 31)

        assertThat(cycle.startEpochDay).isEqualTo(epochDay(2026, 3, 28))
        assertThat(BillingCycleCalculator.normalizedStartDay(31)).isEqualTo(28)
    }

    @Test
    fun `a start day below the range is treated as the first`() {
        val cycle = BillingCycleCalculator.cycleFor(epochDay(2026, 3, 15), startDay = 0)

        assertThat(cycle.startEpochDay).isEqualTo(epochDay(2026, 3, 1))
        assertThat(BillingCycleCalculator.normalizedStartDay(-4)).isEqualTo(1)
    }

    @Test
    fun `both ends of the cycle are inside it`() {
        val cycle = BillingCycleCalculator.cycleFor(epochDay(2026, 3, 15), startDay = 15)

        assertThat(cycle.contains(cycle.startEpochDay)).isTrue()
        assertThat(cycle.contains(cycle.endEpochDay)).isTrue()
        assertThat(cycle.contains(cycle.startEpochDay - 1)).isFalse()
        assertThat(cycle.contains(cycle.endEpochDay + 1)).isFalse()
    }
}
