package com.maknoon.mizan.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /** The device's daily and monthly caps, for the editor screen. */
    @Query("SELECT * FROM budgets WHERE scope = 'DEVICE' AND targetId = 'device'")
    fun observeDevice(): Flow<List<BudgetEntity>>

    /** The stored row for one period, so an edit updates it instead of inserting a duplicate. */
    @Query(
        "SELECT * FROM budgets WHERE scope = 'DEVICE' AND targetId = 'device' AND period = :period LIMIT 1",
    )
    suspend fun deviceBudget(period: BudgetPeriod): BudgetEntity?

    @Upsert
    suspend fun upsert(entity: BudgetEntity)
}
