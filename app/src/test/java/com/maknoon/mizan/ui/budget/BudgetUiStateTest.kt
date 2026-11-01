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
        assertThat(parseLimitBytes("2.5")).isEqualTo(2_500_000_000L)
        assertThat(parseLimitBytes("1")).isEqualTo(1_000_000_000L)
        assertThat(parseLimitBytes("0.5")).isEqualTo(500_000_000L)
    }

    @Test
    fun `parseLimitBytes accepts a trailing decimal point while typing`() {
        assertThat(parseLimitBytes("2.")).isEqualTo(2_000_000_000L)
    }

    @Test
    fun `parseLimitBytes normalises Arabic and Persian digits and separators`() {
        assertThat(parseLimitBytes("٢٫٥")).isEqualTo(2_500_000_000L)
        assertThat(parseLimitBytes("۲.۵")).isEqualTo(2_500_000_000L)
        assertThat(parseLimitBytes("1٬000")).isEqualTo(1_000_000_000_000L)
        assertThat(parseLimitBytes("1,000")).isEqualTo(1_000_000_000_000L)
        assertThat(parseLimitBytes("1 000.5")).isEqualTo(1_000_500_000_000L)
    }

    @Test
    fun `parseLimitBytes rejects non-numbers and non-positive limits`() {
        assertThat(parseLimitBytes("")).isNull()
        assertThat(parseLimitBytes("abc")).isNull()
        assertThat(parseLimitBytes("0")).isNull()
        assertThat(parseLimitBytes("-5")).isNull()
        assertThat(parseLimitBytes("1.2.3")).isNull()
        assertThat(parseLimitBytes("2.5x")).isNull()
    }

    @Test
    fun `parseLimitBytes accepts a comma only as a thousands separator`() {
        assertThat(parseLimitBytes("1,5")).isNull()
        assertThat(parseLimitBytes("12,34")).isNull()
        assertThat(parseLimitBytes("100,000")).isEqualTo(100_000_000_000_000L)
    }

    @Test
    fun `parseLimitBytes rejects limits at or above the cap`() {
        // 1,000,000 GB hits MAX_LIMIT_BYTES and must not overflow into a smaller long.
        assertThat(parseLimitBytes("1000000")).isNull()
        assertThat(parseLimitBytes("999999999999999999")).isNull()
    }

    @Test
    fun `formatLimitGb renders stored bytes as trimmed gigabytes and zero as empty`() {
        assertThat(formatLimitGb(0L)).isEmpty()
        assertThat(formatLimitGb(1_000_000_000L)).isEqualTo("1")
        assertThat(formatLimitGb(2_500_000_000L)).isEqualTo("2.5")
        assertThat(formatLimitGb(500_000_000L)).isEqualTo("0.5")
        assertThat(formatLimitGb(100_000_000_000L)).isEqualTo("100")
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
