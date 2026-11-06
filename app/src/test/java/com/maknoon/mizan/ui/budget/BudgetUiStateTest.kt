package com.maknoon.mizan.ui.budget

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.core.domain.budget.DEVICE_TARGET_ID
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import org.junit.Test

class BudgetUiStateTest {

    @Test
    fun `parseLimitBytes converts gigabytes to bytes`() {
        assertThat(parseLimitBytes("2.5", BudgetUnit.GIGABYTES)).isEqualTo(2_500_000_000L)
        assertThat(parseLimitBytes("1", BudgetUnit.GIGABYTES)).isEqualTo(1_000_000_000L)
        assertThat(parseLimitBytes("0.5", BudgetUnit.GIGABYTES)).isEqualTo(500_000_000L)
    }

    @Test
    fun `parseLimitBytes converts megabytes to bytes`() {
        assertThat(parseLimitBytes("90", BudgetUnit.MEGABYTES)).isEqualTo(90_000_000L)
        assertThat(parseLimitBytes("2.5", BudgetUnit.MEGABYTES)).isEqualTo(2_500_000L)
        assertThat(parseLimitBytes("1000", BudgetUnit.MEGABYTES)).isEqualTo(1_000_000_000L)
    }

    @Test
    fun `parseLimitBytes accepts a trailing decimal point while typing`() {
        assertThat(parseLimitBytes("2.", BudgetUnit.GIGABYTES)).isEqualTo(2_000_000_000L)
    }

    @Test
    fun `parseLimitBytes normalises Arabic and Persian digits and separators`() {
        assertThat(parseLimitBytes("٢٫٥", BudgetUnit.GIGABYTES)).isEqualTo(2_500_000_000L)
        assertThat(parseLimitBytes("۲.۵", BudgetUnit.GIGABYTES)).isEqualTo(2_500_000_000L)
        assertThat(parseLimitBytes("1٬000", BudgetUnit.GIGABYTES)).isEqualTo(1_000_000_000_000L)
        assertThat(parseLimitBytes("1,000", BudgetUnit.GIGABYTES)).isEqualTo(1_000_000_000_000L)
        assertThat(parseLimitBytes("1 000.5", BudgetUnit.GIGABYTES)).isEqualTo(1_000_500_000_000L)
    }

    @Test
    fun `parseLimitBytes rejects non-numbers and non-positive limits`() {
        assertThat(parseLimitBytes("", BudgetUnit.GIGABYTES)).isNull()
        assertThat(parseLimitBytes("abc", BudgetUnit.GIGABYTES)).isNull()
        assertThat(parseLimitBytes("0", BudgetUnit.GIGABYTES)).isNull()
        assertThat(parseLimitBytes("-5", BudgetUnit.GIGABYTES)).isNull()
        assertThat(parseLimitBytes("1.2.3", BudgetUnit.GIGABYTES)).isNull()
        assertThat(parseLimitBytes("2.5x", BudgetUnit.GIGABYTES)).isNull()
    }

    @Test
    fun `parseLimitBytes accepts a comma only as a thousands separator`() {
        assertThat(parseLimitBytes("1,5", BudgetUnit.GIGABYTES)).isNull()
        assertThat(parseLimitBytes("12,34", BudgetUnit.GIGABYTES)).isNull()
        assertThat(parseLimitBytes("100,000", BudgetUnit.GIGABYTES)).isEqualTo(100_000_000_000_000L)
    }

    @Test
    fun `parseLimitBytes rejects limits at or above the cap`() {
        // 1,000,000 GB hits MAX_LIMIT_BYTES and must not overflow into a smaller long.
        assertThat(parseLimitBytes("1000000", BudgetUnit.GIGABYTES)).isNull()
        assertThat(parseLimitBytes("999999999999999999", BudgetUnit.GIGABYTES)).isNull()
        // The same cap in megabytes is a thousand times smaller a number, and still rejected.
        assertThat(parseLimitBytes("1000000000000", BudgetUnit.MEGABYTES)).isNull()
    }

    @Test
    fun `formatLimit renders stored bytes in the chosen unit and zero as empty`() {
        assertThat(formatLimit(0L, BudgetUnit.GIGABYTES)).isEmpty()
        assertThat(formatLimit(1_000_000_000L, BudgetUnit.GIGABYTES)).isEqualTo("1")
        assertThat(formatLimit(2_500_000_000L, BudgetUnit.GIGABYTES)).isEqualTo("2.5")
        assertThat(formatLimit(500_000_000L, BudgetUnit.GIGABYTES)).isEqualTo("0.5")
        assertThat(formatLimit(100_000_000_000L, BudgetUnit.GIGABYTES)).isEqualTo("100")
        assertThat(formatLimit(90_000_000L, BudgetUnit.MEGABYTES)).isEqualTo("90")
        assertThat(formatLimit(2_500_000_000L, BudgetUnit.MEGABYTES)).isEqualTo("2500")
    }

    @Test
    fun `a stored cap below one gigabyte opens in megabytes`() {
        assertThat(unitForBytes(0L)).isEqualTo(BudgetUnit.GIGABYTES)
        assertThat(unitForBytes(90_000_000L)).isEqualTo(BudgetUnit.MEGABYTES)
        assertThat(unitForBytes(999_999_999L)).isEqualTo(BudgetUnit.MEGABYTES)
        assertThat(unitForBytes(1_000_000_000L)).isEqualTo(BudgetUnit.GIGABYTES)
    }

    @Test
    fun `switching the unit rewrites the number and keeps the bytes`() {
        assertThat(convertLimitText("2", BudgetUnit.GIGABYTES, BudgetUnit.MEGABYTES)).isEqualTo("2000")
        assertThat(convertLimitText("1500", BudgetUnit.MEGABYTES, BudgetUnit.GIGABYTES)).isEqualTo("1.5")
        assertThat(convertLimitText("90", BudgetUnit.MEGABYTES, BudgetUnit.MEGABYTES)).isEqualTo("90")
    }

    @Test
    fun `switching the unit leaves text that does not parse yet alone`() {
        assertThat(convertLimitText("", BudgetUnit.GIGABYTES, BudgetUnit.MEGABYTES)).isEmpty()
        assertThat(convertLimitText("abc", BudgetUnit.GIGABYTES, BudgetUnit.MEGABYTES)).isEqualTo("abc")
    }

    @Test
    fun `no stored budgets render disabled empty fields`() {
        val state = budgetUiState(emptyList(), cycleStartDay = 1)

        assertThat(state.daily).isEqualTo(BudgetFieldUi())
        assertThat(state.monthly).isEqualTo(BudgetFieldUi())
        assertThat(state.cycleStartDay).isEqualTo(1)
    }

    @Test
    fun `stored rows fill the text and the switch`() {
        val state = budgetUiState(
            listOf(
                budget(BudgetPeriod.DAILY, limitBytes = 2_500_000_000L, enabled = true),
                budget(BudgetPeriod.MONTHLY, limitBytes = 50_000_000_000L, enabled = false),
            ),
            cycleStartDay = 5,
        )

        assertThat(state.daily.text).isEqualTo("2.5")
        assertThat(state.daily.enabled).isTrue()
        assertThat(state.monthly.text).isEqualTo("50")
        assertThat(state.monthly.enabled).isFalse()
        assertThat(state.cycleStartDay).isEqualTo(5)
    }

    @Test
    fun `stored rows pick their unit from the stored bytes`() {
        val state = budgetUiState(
            listOf(
                budget(BudgetPeriod.DAILY, limitBytes = 90_000_000L, enabled = true),
                budget(BudgetPeriod.MONTHLY, limitBytes = 50_000_000_000L, enabled = true),
            ),
            cycleStartDay = 1,
        )

        assertThat(state.daily.text).isEqualTo("90")
        assertThat(state.daily.unit).isEqualTo(BudgetUnit.MEGABYTES)
        assertThat(state.monthly.text).isEqualTo("50")
        assertThat(state.monthly.unit).isEqualTo(BudgetUnit.GIGABYTES)
    }

    @Test
    fun `the chosen unit wins over the one derived from the stored bytes`() {
        val state = budgetUiState(
            listOf(budget(BudgetPeriod.DAILY, limitBytes = 90_000_000L, enabled = true)),
            cycleStartDay = 1,
            dailyUnit = BudgetUnit.GIGABYTES,
        )

        assertThat(state.daily.text).isEqualTo("0.09")
        assertThat(state.daily.unit).isEqualTo(BudgetUnit.GIGABYTES)
    }

    @Test
    fun `in-flight text wins over the stored value while typing`() {
        val state = budgetUiState(
            listOf(budget(BudgetPeriod.DAILY, limitBytes = 2_500_000_000L, enabled = true)),
            cycleStartDay = 1,
            dailyText = "3",
        )

        assertThat(state.daily.text).isEqualTo("3")
        assertThat(state.daily.error).isFalse()
    }

    @Test
    fun `invalid in-flight text is flagged but still shown so it can be fixed`() {
        val state = budgetUiState(
            listOf(budget(BudgetPeriod.DAILY, limitBytes = 2_500_000_000L, enabled = true)),
            cycleStartDay = 1,
            dailyText = "abc",
        )

        assertThat(state.daily.text).isEqualTo("abc")
        assertThat(state.daily.error).isTrue()
    }

    @Test
    fun `a cleared field is a pause in editing, not an error`() {
        val state = budgetUiState(
            listOf(budget(BudgetPeriod.DAILY, limitBytes = 2_500_000_000L, enabled = true)),
            cycleStartDay = 1,
            dailyText = "",
        )

        assertThat(state.daily.text).isEmpty()
        assertThat(state.daily.error).isFalse()
    }

    @Test
    fun `per-app rows do not leak into the device editor`() {
        val state = budgetUiState(
            listOf(
                budget(BudgetPeriod.DAILY, limitBytes = 2_500_000_000L, enabled = true),
                BudgetEntity(
                    id = 2L,
                    scope = BudgetScope.APP,
                    targetId = "com.example.app",
                    period = BudgetPeriod.MONTHLY,
                    limitBytes = 99_000_000_000L,
                    enabled = true,
                    createdAt = 0L,
                ),
            ),
            cycleStartDay = 1,
        )

        assertThat(state.monthly).isEqualTo(BudgetFieldUi())
    }

    @Test
    fun `the start day is clamped into the supported range`() {
        assertThat(budgetUiState(emptyList(), cycleStartDay = 40).cycleStartDay)
            .isEqualTo(BillingCycleCalculator.MAX_START_DAY)
        assertThat(budgetUiState(emptyList(), cycleStartDay = 0).cycleStartDay)
            .isEqualTo(BillingCycleCalculator.MIN_START_DAY)
    }

    private fun budget(period: BudgetPeriod, limitBytes: Long, enabled: Boolean) = BudgetEntity(
        id = 1L,
        scope = BudgetScope.DEVICE,
        targetId = DEVICE_TARGET_ID,
        period = period,
        limitBytes = limitBytes,
        enabled = enabled,
        createdAt = 0L,
    )
}
