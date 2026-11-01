package com.maknoon.mizan.core.data.alert

import com.maknoon.mizan.core.data.db.AlertEventEntity
import com.maknoon.mizan.core.domain.budget.BudgetAlert
import com.maknoon.mizan.core.domain.budget.BudgetLevel
import com.maknoon.mizan.core.domain.budget.NotifiedBudgetLevel

/**
 * The alert_events types budget alerts write. The values come from docs/03 §8 and stay stable:
 * the ledger query and the acknowledged state depend on them.
 */
object BudgetAlertTypes {
    const val WARNING = "BUDGET_80"
    const val EXCEEDED = "BUDGET_100"

    fun typeOf(level: BudgetLevel): String = when (level) {
        BudgetLevel.WARNING -> WARNING
        BudgetLevel.EXCEEDED -> EXCEEDED
    }

    fun levelOf(type: String): BudgetLevel? = when (type) {
        WARNING -> BudgetLevel.WARNING
        EXCEEDED -> BudgetLevel.EXCEEDED
        else -> null
    }
}

/**
 * Translates between the engine's alert and the stored ledger. Kept free of Android types so
 * the mapping can be unit tested without a database.
 */
object BudgetAlertEvents {

    /**
     * One row per crossed threshold, not just the highest: a jump past 100% records both, so a
     * late warning can never follow the exceed notice.
     */
    fun events(alert: BudgetAlert, atMillis: Long): List<AlertEventEntity> =
        alert.ledgerEntries().map { entry ->
            AlertEventEntity(
                at = atMillis,
                type = BudgetAlertTypes.typeOf(entry.level),
                scope = entry.scope,
                targetId = entry.targetId,
                period = entry.period,
                periodStartEpochDay = entry.periodStartEpochDay,
                measuredBytes = alert.usedBytes,
                limitBytes = alert.limitBytes,
                acknowledged = false,
            )
        }

    /** Stored budget rows as the engine's ledger; later alert kinds are not budget thresholds. */
    fun ledger(events: List<AlertEventEntity>): List<NotifiedBudgetLevel> =
        events.mapNotNull { event ->
            BudgetAlertTypes.levelOf(event.type)?.let { level ->
                NotifiedBudgetLevel(
                    scope = event.scope,
                    targetId = event.targetId,
                    period = event.period,
                    periodStartEpochDay = event.periodStartEpochDay,
                    level = level,
                )
            }
        }
}
