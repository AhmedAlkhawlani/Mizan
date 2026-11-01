package com.maknoon.mizan.widget

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.widget.WidgetTopApp
import org.junit.Test

class BudgetWidgetUiStateTest {

    @Test
    fun `a normal day maps to its truncated percentage and ring fraction`() {
        val state = budgetWidgetUiState(
            usedBytes = 1_240_000_000,
            limitBytes = 2_000_000_000,
            topApp = null,
        )

        assertThat(state.percent).isEqualTo(62)
        assertThat(state.ringFraction).isWithin(TOLERANCE).of(0.62f)
        assertThat(state.status).isEqualTo(BudgetRingStatus.NORMAL)
    }

    @Test
    fun `eighty percent is the warning threshold`() {
        val atThreshold = budgetWidgetUiState(800, 1_000, null)
        val below = budgetWidgetUiState(799, 1_000, null)

        assertThat(atThreshold.percent).isEqualTo(80)
        assertThat(atThreshold.status).isEqualTo(BudgetRingStatus.WARNING)
        assertThat(below.percent).isEqualTo(79)
        assertThat(below.status).isEqualTo(BudgetRingStatus.NORMAL)
    }

    @Test
    fun `reaching the limit is an overrun`() {
        val state = budgetWidgetUiState(1_000, 1_000, null)

        assertThat(state.percent).isEqualTo(100)
        assertThat(state.status).isEqualTo(BudgetRingStatus.EXCEEDED)
        assertThat(state.ringFraction).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun `an overrun keeps its real percentage but clamps the ring`() {
        val state = budgetWidgetUiState(1_500, 1_000, null)

        assertThat(state.percent).isEqualTo(150)
        assertThat(state.status).isEqualTo(BudgetRingStatus.EXCEEDED)
        assertThat(state.ringFraction).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun `a zero limit is no budget rather than a division by zero`() {
        val state = budgetWidgetUiState(500, 0, null)

        assertThat(state.limitBytes).isNull()
        assertThat(state.percent).isNull()
        assertThat(state.status).isEqualTo(BudgetRingStatus.NONE)
        assertThat(state.ringFraction).isWithin(TOLERANCE).of(0f)
    }

    @Test
    fun `unknown usage with a limit stays unknown instead of reading zero percent`() {
        val state = budgetWidgetUiState(usedBytes = null, limitBytes = 1_000, topApp = null)

        assertThat(state.usedBytes).isNull()
        assertThat(state.percent).isNull()
        assertThat(state.status).isEqualTo(BudgetRingStatus.NONE)
    }

    @Test
    fun `usage without a limit has no percentage`() {
        val state = budgetWidgetUiState(usedBytes = 500, limitBytes = null, topApp = null)

        assertThat(state.usedBytes).isEqualTo(500L)
        assertThat(state.percent).isNull()
        assertThat(state.status).isEqualTo(BudgetRingStatus.NONE)
    }

    @Test
    fun `negative usage is clamped to zero`() {
        val state = budgetWidgetUiState(usedBytes = -1, limitBytes = 1_000, topApp = null)

        assertThat(state.usedBytes).isEqualTo(0L)
        assertThat(state.percent).isEqualTo(0)
        assertThat(state.status).isEqualTo(BudgetRingStatus.NORMAL)
    }

    @Test
    fun `the top app label is trimmed and falls back to the package name`() {
        val state = budgetWidgetUiState(
            usedBytes = 0,
            limitBytes = null,
            topApp = WidgetTopApp(" com.example.app ", "  Example  ", 42),
        )

        assertThat(state.topAppPackage).isEqualTo(" com.example.app ")
        assertThat(state.topAppLabel).isEqualTo("Example")
        assertThat(state.topAppBytes).isEqualTo(42L)
    }

    @Test
    fun `a blank label falls back to the package name`() {
        val state = budgetWidgetUiState(
            usedBytes = 0,
            limitBytes = null,
            topApp = WidgetTopApp("com.example.app", "   ", 42),
        )

        assertThat(state.topAppLabel).isEqualTo("com.example.app")
    }

    @Test
    fun `no top app leaves the row empty`() {
        val state = budgetWidgetUiState(usedBytes = 0, limitBytes = null, topApp = null)

        assertThat(state.topAppPackage).isNull()
        assertThat(state.topAppLabel).isNull()
        assertThat(state.topAppBytes).isNull()
    }

    private companion object {
        const val TOLERANCE = 0.000_001f
    }
}
