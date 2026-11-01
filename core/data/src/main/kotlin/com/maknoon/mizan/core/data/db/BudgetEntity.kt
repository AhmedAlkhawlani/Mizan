package com.maknoon.mizan.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope

/**
 * One data cap (docs/03 §5). The scopes are stored as their enum names, so the device row is
 * addressed by `targetId = "device"` and an app row by its package name.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: BudgetScope,
    val targetId: String,
    val period: BudgetPeriod,
    val limitBytes: Long,
    val enabled: Boolean,
    val createdAt: Long,
)
