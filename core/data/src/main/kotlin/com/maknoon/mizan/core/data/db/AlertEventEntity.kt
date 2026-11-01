package com.maknoon.mizan.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope

/**
 * One alert that was actually delivered (docs/03 §8).
 *
 * The unique index is the "once per level per cycle" guarantee: two evaluators racing — the
 * monitoring service and the sync worker — can both post, but the database keeps a single row
 * for the same threshold, period and cycle. [type] carries the alert kind so later anomaly
 * alerts can share the table; budget rows use `BUDGET_80` and `BUDGET_100`.
 */
@Entity(
    tableName = "alert_events",
    indices = [
        Index(
            value = ["type", "scope", "targetId", "period", "periodStartEpochDay"],
            unique = true,
        ),
    ],
)
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val type: String,
    val scope: BudgetScope,
    val targetId: String,
    val period: BudgetPeriod,
    val periodStartEpochDay: Long,
    val measuredBytes: Long,
    val limitBytes: Long,
    val acknowledged: Boolean,
)
