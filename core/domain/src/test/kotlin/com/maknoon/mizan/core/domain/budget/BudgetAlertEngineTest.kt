package com.maknoon.mizan.core.domain.budget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BudgetAlertEngineTest {

    private val engine = BudgetAlertEngine()
    private val today = 100L
    private val cycleStart = 90L

    @Test
    fun `no rules means no alerts`() {
        val alerts = engine.evaluate(emptyList(), BudgetUsage(), today, cycleStart)

        assertThat(alerts).isEmpty()
    }

    @Test
    fun `usage below the warning threshold is silent`() {
        val alerts = engine.evaluate(
            listOf(rule(limitBytes = 1_000)),
            BudgetUsage(deviceDailyBytes = 799),
            today,
            cycleStart,
        )

        assertThat(alerts).isEmpty()
    }

    @Test
    fun `exactly eighty percent raises the warning`() {
        val alerts = engine.evaluate(
            listOf(rule(limitBytes = 1_000)),
            BudgetUsage(deviceDailyBytes = 800),
            today,
            cycleStart,
        )

        val alert = alerts.single()
        assertThat(alert.level).isEqualTo(BudgetLevel.WARNING)
        assertThat(alert.reachedLevels).containsExactly(BudgetLevel.WARNING)
        assertThat(alert.ratio).isWithin(0.0001).of(0.8)
        assertThat(alert.periodStartEpochDay).isEqualTo(today)
    }

    @Test
    fun `a notified warning is not repeated`() {
        val alerts = engine.evaluate(
            listOf(rule(limitBytes = 1_000)),
            BudgetUsage(deviceDailyBytes = 850),
            today,
            cycleStart,
            notified = listOf(notifiedLevel(BudgetLevel.WARNING)),
        )

        assertThat(alerts).isEmpty()
    }

    @Test
    fun `crossing the limit after a warning raises only the exceed notice`() {
        val alerts = engine.evaluate(
            listOf(rule(limitBytes = 1_000)),
            BudgetUsage(deviceDailyBytes = 1_100),
            today,
            cycleStart,
            notified = listOf(notifiedLevel(BudgetLevel.WARNING)),
        )

        val alert = alerts.single()
        assertThat(alert.level).isEqualTo(BudgetLevel.EXCEEDED)
        assertThat(alert.reachedLevels)
            .containsExactly(BudgetLevel.WARNING, BudgetLevel.EXCEEDED)
        assertThat(alert.ledgerEntries().map { it.level })
            .containsExactly(BudgetLevel.WARNING, BudgetLevel.EXCEEDED).inOrder()
    }

    @Test
    fun `a jump past the limit sends one exceed notice and records both levels`() {
        val alerts = engine.evaluate(
            listOf(rule(limitBytes = 1_000)),
            BudgetUsage(deviceDailyBytes = 1_400),
            today,
            cycleStart,
        )

        val alert = alerts.single()
        assertThat(alert.level).isEqualTo(BudgetLevel.EXCEEDED)
        assertThat(alert.ledgerEntries().map { it.level })
            .containsExactly(BudgetLevel.WARNING, BudgetLevel.EXCEEDED).inOrder()
    }

    @Test
    fun `a zero or negative limit is not a budget`() {
        val alerts = engine.evaluate(
            listOf(rule(limitBytes = 0), rule(limitBytes = -1)),
            BudgetUsage(deviceDailyBytes = 10_000),
            today,
            cycleStart,
        )

        assertThat(alerts).isEmpty()
    }

    @Test
    fun `device daily and monthly rules read their own usage`() {
        val alerts = engine.evaluate(
            listOf(
                rule(period = BudgetPeriod.DAILY, limitBytes = 1_000),
                rule(period = BudgetPeriod.MONTHLY, limitBytes = 2_000),
            ),
            BudgetUsage(deviceDailyBytes = 900, deviceCycleBytes = 2_500),
            today,
            cycleStart,
        )

        assertThat(alerts.map { it.period to it.level }).containsExactly(
            BudgetPeriod.DAILY to BudgetLevel.WARNING,
            BudgetPeriod.MONTHLY to BudgetLevel.EXCEEDED,
        )
        assertThat(alerts.first { it.period == BudgetPeriod.DAILY }.periodStartEpochDay)
            .isEqualTo(today)
        assertThat(alerts.first { it.period == BudgetPeriod.MONTHLY }.periodStartEpochDay)
            .isEqualTo(cycleStart)
    }

    @Test
    fun `app rules read the package totals and treat a missing package as zero`() {
        val alerts = engine.evaluate(
            listOf(
                rule(scope = BudgetScope.APP, targetId = "com.example.heavy", limitBytes = 1_000),
                rule(scope = BudgetScope.APP, targetId = "com.example.silent", limitBytes = 1_000),
            ),
            BudgetUsage(appDailyBytes = mapOf("com.example.heavy" to 900)),
            today,
            cycleStart,
        )

        assertThat(alerts.map { it.targetId }).containsExactly("com.example.heavy")
    }

    @Test
    fun `a ledger row from yesterday does not silence today's daily alert`() {
        val alerts = engine.evaluate(
            listOf(rule(limitBytes = 1_000)),
            BudgetUsage(deviceDailyBytes = 850),
            today,
            cycleStart,
            notified = listOf(notifiedLevel(BudgetLevel.WARNING, periodStart = today - 1)),
        )

        assertThat(alerts.single().level).isEqualTo(BudgetLevel.WARNING)
    }

    @Test
    fun `a ledger row from the previous cycle does not silence this cycle's monthly alert`() {
        val alerts = engine.evaluate(
            listOf(rule(period = BudgetPeriod.MONTHLY, limitBytes = 1_000)),
            BudgetUsage(deviceCycleBytes = 850),
            today,
            cycleStart,
            notified = listOf(notifiedLevel(BudgetLevel.WARNING, periodStart = cycleStart - 31)),
        )

        assertThat(alerts.single().level).isEqualTo(BudgetLevel.WARNING)
    }

    @Test
    fun `daily and monthly ledgers are independent`() {
        val alerts = engine.evaluate(
            listOf(
                rule(period = BudgetPeriod.DAILY, limitBytes = 1_000),
                rule(period = BudgetPeriod.MONTHLY, limitBytes = 1_000),
            ),
            BudgetUsage(deviceDailyBytes = 850, deviceCycleBytes = 850),
            today,
            cycleStart,
            notified = listOf(notifiedLevel(BudgetLevel.WARNING, period = BudgetPeriod.DAILY)),
        )

        assertThat(alerts.map { it.period }).containsExactly(BudgetPeriod.MONTHLY)
    }

    @Test
    fun `a ledger row for another app does not silence this app`() {
        val alerts = engine.evaluate(
            listOf(rule(scope = BudgetScope.APP, targetId = "com.example.a", limitBytes = 1_000)),
            BudgetUsage(appDailyBytes = mapOf("com.example.a" to 850)),
            today,
            cycleStart,
            notified = listOf(
                notifiedLevel(
                    BudgetLevel.WARNING,
                    scope = BudgetScope.APP,
                    targetId = "com.example.b",
                ),
            ),
        )

        assertThat(alerts.single().targetId).isEqualTo("com.example.a")
    }

    @Test
    fun `alerts are ordered by how far over the limit they are`() {
        val alerts = engine.evaluate(
            listOf(
                rule(scope = BudgetScope.APP, targetId = "com.example.ninety", limitBytes = 1_000),
                rule(scope = BudgetScope.APP, targetId = "com.example.full", limitBytes = 1_000),
            ),
            BudgetUsage(appDailyBytes = mapOf("com.example.ninety" to 900, "com.example.full" to 1_200)),
            today,
            cycleStart,
        )

        assertThat(alerts.map { it.targetId })
            .containsExactly("com.example.full", "com.example.ninety").inOrder()
    }

    @Test
    fun `negative usage cannot alert`() {
        val alerts = engine.evaluate(
            listOf(rule(limitBytes = 1_000)),
            BudgetUsage(deviceDailyBytes = -500),
            today,
            cycleStart,
        )

        assertThat(alerts).isEmpty()
    }

    private fun rule(
        scope: BudgetScope = BudgetScope.DEVICE,
        targetId: String = "device",
        period: BudgetPeriod = BudgetPeriod.DAILY,
        limitBytes: Long,
    ) = BudgetRule(scope = scope, targetId = targetId, period = period, limitBytes = limitBytes)

    private fun notifiedLevel(
        level: BudgetLevel,
        scope: BudgetScope = BudgetScope.DEVICE,
        targetId: String = "device",
        period: BudgetPeriod = BudgetPeriod.DAILY,
        periodStart: Long = today,
    ) = NotifiedBudgetLevel(
        scope = scope,
        targetId = targetId,
        period = period,
        periodStartEpochDay = periodStart,
        level = level,
    )
}
