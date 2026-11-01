package com.maknoon.mizan.ui.apps

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.core.domain.budget.DEVICE_TARGET_ID
import org.junit.Test

class AppBudgetUiStateTest {

    @Test
    fun `stored rows fill their period and leave the other empty`() {
        val state = appBudgetUiState(
            packageName = "com.example.app",
            label = "Example",
            budgets = listOf(
                budget("com.example.app", BudgetPeriod.DAILY, limitBytes = 2_000_000_000, enabled = true),
                budget("com.example.app", BudgetPeriod.MONTHLY, limitBytes = 30_000_000_000, enabled = false),
            ),
        )

        assertThat(state.packageName).isEqualTo("com.example.app")
        assertThat(state.label).isEqualTo("Example")
        assertThat(state.daily.text).isEqualTo("2")
        assertThat(state.daily.enabled).isTrue()
        assertThat(state.monthly.text).isEqualTo("30")
        assertThat(state.monthly.enabled).isFalse()
        assertThat(state.daily.error).isFalse()
        assertThat(state.monthly.error).isFalse()
    }

    @Test
    fun `no stored rows start with two empty fields`() {
        val state = appBudgetUiState("com.example.app", "Example", emptyList())

        assertThat(state.daily.text).isEmpty()
        assertThat(state.daily.enabled).isFalse()
        assertThat(state.monthly.text).isEmpty()
        assertThat(state.monthly.enabled).isFalse()
    }

    @Test
    fun `other apps and the device row are ignored`() {
        val state = appBudgetUiState(
            packageName = "com.example.app",
            label = "Example",
            budgets = listOf(
                budget("com.example.other", BudgetPeriod.DAILY, limitBytes = 9_000_000_000, enabled = true),
                budget(DEVICE_TARGET_ID, BudgetPeriod.DAILY, limitBytes = 5_000_000_000, enabled = true, scope = BudgetScope.DEVICE),
            ),
        )

        assertThat(state.daily.text).isEmpty()
        assertThat(state.daily.enabled).isFalse()
    }

    @Test
    fun `in flight text wins over the stored limit`() {
        val state = appBudgetUiState(
            packageName = "com.example.app",
            label = "Example",
            budgets = listOf(budget("com.example.app", BudgetPeriod.DAILY, 2_000_000_000, enabled = true)),
            dailyText = "1.5",
        )

        assertThat(state.daily.text).isEqualTo("1.5")
        assertThat(state.daily.enabled).isTrue()
    }

    @Test
    fun `an invalid non empty entry is flagged`() {
        val state = appBudgetUiState(
            packageName = "com.example.app",
            label = "Example",
            budgets = emptyList(),
            monthlyText = "abc",
        )

        assertThat(state.monthly.error).isTrue()
    }

    @Test
    fun `an emptied field is a pause, not an error`() {
        val state = appBudgetUiState(
            packageName = "com.example.app",
            label = "Example",
            budgets = listOf(budget("com.example.app", BudgetPeriod.DAILY, 2_000_000_000, enabled = true)),
            dailyText = "",
        )

        assertThat(state.daily.text).isEmpty()
        assertThat(state.daily.error).isFalse()
    }

    private fun budget(
        targetId: String,
        period: BudgetPeriod,
        limitBytes: Long,
        enabled: Boolean,
        scope: BudgetScope = BudgetScope.APP,
    ) = BudgetEntity(
        scope = scope,
        targetId = targetId,
        period = period,
        limitBytes = limitBytes,
        enabled = enabled,
        createdAt = 0L,
    )
}
