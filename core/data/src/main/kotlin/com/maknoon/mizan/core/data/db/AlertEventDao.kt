package com.maknoon.mizan.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {

    /** Ignores a row whose threshold was already recorded for the same period. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<AlertEventEntity>): List<Long>

    /** Budget thresholds only, which the alert engine uses as its notification ledger. */
    @Query("SELECT * FROM alert_events WHERE type IN ('BUDGET_80', 'BUDGET_100')")
    suspend fun budgetLedger(): List<AlertEventEntity>

    @Query(
        """
        UPDATE alert_events SET acknowledged = 1
        WHERE scope = :scope
          AND targetId = :targetId
          AND period = :period
          AND periodStartEpochDay = :periodStartEpochDay
        """,
    )
    suspend fun acknowledge(
        scope: BudgetScope,
        targetId: String,
        period: BudgetPeriod,
        periodStartEpochDay: Long,
    )

    @Query("SELECT * FROM alert_events ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AlertEventEntity>>
}
