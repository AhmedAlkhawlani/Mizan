package com.maknoon.mizan.core.data.alert

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.AlertEventEntity
import com.maknoon.mizan.core.domain.budget.BudgetAlert
import com.maknoon.mizan.core.domain.budget.BudgetLevel
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import org.junit.Test

class BudgetAlertEventsTest {

    private val atMillis = 1_790_000_000_000L
    private val periodStart = 20_750L

    private fun alert(
        level: BudgetLevel,
        reached: Set<BudgetLevel>,
        scope: BudgetScope = BudgetScope.DEVICE,
        targetId: String = "device",
        period: BudgetPeriod = BudgetPeriod.DAILY,
    ) = BudgetAlert(
        scope = scope,
        targetId = targetId,
        period = period,
        periodStartEpochDay = periodStart,
        level = level,
        reachedLevels = reached,
        usedBytes = 900L,
        limitBytes = 1_000L,
        ratio = 0.9,
    )

    @Test
    fun `warning maps to one unacknowledged BUDGET_80 row`() {
        val events = BudgetAlertEvents.events(
            alert(BudgetLevel.WARNING, setOf(BudgetLevel.WARNING)),
            atMillis,
        )

        assertThat(events).hasSize(1)
        val event = events.single()
        assertThat(event.type).isEqualTo(BudgetAlertTypes.WARNING)
        assertThat(event.scope).isEqualTo(BudgetScope.DEVICE)
        assertThat(event.targetId).isEqualTo("device")
        assertThat(event.period).isEqualTo(BudgetPeriod.DAILY)
        assertThat(event.periodStartEpochDay).isEqualTo(periodStart)
        assertThat(event.measuredBytes).isEqualTo(900L)
        assertThat(event.limitBytes).isEqualTo(1_000L)
        assertThat(event.acknowledged).isFalse()
        assertThat(event.at).isEqualTo(atMillis)
    }

    @Test
    fun `a jump past the limit records warning before exceeded`() {
        val events = BudgetAlertEvents.events(
            alert(BudgetLevel.EXCEEDED, setOf(BudgetLevel.EXCEEDED, BudgetLevel.WARNING)),
            atMillis,
        )

        assertThat(events.map { it.type })
            .containsExactly(BudgetAlertTypes.WARNING, BudgetAlertTypes.EXCEEDED)
            .inOrder()
    }

    @Test
    fun `stored budget rows become the engine ledger`() {
        val warnings = BudgetAlertEvents.events(
            alert(BudgetLevel.WARNING, setOf(BudgetLevel.WARNING)),
            atMillis,
        )
        val exceeded = BudgetAlertEvents.events(
            alert(BudgetLevel.EXCEEDED, setOf(BudgetLevel.EXCEEDED)),
            atMillis,
        )

        val ledger = BudgetAlertEvents.ledger(warnings + exceeded)

        assertThat(ledger).hasSize(2)
        assertThat(ledger.map { it.level })
            .containsExactly(BudgetLevel.WARNING, BudgetLevel.EXCEEDED)
        assertThat(ledger.map { it.periodStartEpochDay }).containsExactly(periodStart, periodStart)
    }

    @Test
    fun `the ledger keeps an app budget key`() {
        val events = BudgetAlertEvents.events(
            alert(
                BudgetLevel.WARNING,
                setOf(BudgetLevel.WARNING),
                scope = BudgetScope.APP,
                targetId = "com.zhiliaoapp.musically",
                period = BudgetPeriod.MONTHLY,
            ),
            atMillis,
        )

        val entry = BudgetAlertEvents.ledger(events).single()

        assertThat(entry.scope).isEqualTo(BudgetScope.APP)
        assertThat(entry.targetId).isEqualTo("com.zhiliaoapp.musically")
        assertThat(entry.period).isEqualTo(BudgetPeriod.MONTHLY)
    }

    @Test
    fun `non-budget alert rows are not part of the budget ledger`() {
        val anomaly = AlertEventEntity(
            at = atMillis,
            type = "ANOMALY",
            scope = BudgetScope.DEVICE,
            targetId = "device",
            period = BudgetPeriod.DAILY,
            periodStartEpochDay = periodStart,
            measuredBytes = 0L,
            limitBytes = 0L,
            acknowledged = false,
        )

        assertThat(BudgetAlertEvents.ledger(listOf(anomaly))).isEmpty()
        assertThat(BudgetAlertTypes.levelOf("ANOMALY")).isNull()
    }

    @Test
    fun `type names are the stable ones documented in the data model`() {
        assertThat(BudgetAlertTypes.typeOf(BudgetLevel.WARNING)).isEqualTo("BUDGET_80")
        assertThat(BudgetAlertTypes.typeOf(BudgetLevel.EXCEEDED)).isEqualTo("BUDGET_100")
        assertThat(BudgetAlertTypes.levelOf("BUDGET_80")).isEqualTo(BudgetLevel.WARNING)
        assertThat(BudgetAlertTypes.levelOf("BUDGET_100")).isEqualTo(BudgetLevel.EXCEEDED)
    }
}
