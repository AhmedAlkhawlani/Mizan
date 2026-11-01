package com.maknoon.mizan.ui.apps

import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.ui.budget.formatLimitGb
import com.maknoon.mizan.ui.budget.parseLimitBytes

/** One cap in the per-app editor: the daily or the monthly limit. */
data class AppBudgetFieldUi(
    val text: String = "",
    val enabled: Boolean = false,
    val error: Boolean = false,
)

/** The per-app budget dialog; null while the dialog is closed. */
data class AppBudgetUiState(
    val packageName: String,
    val label: String,
    val daily: AppBudgetFieldUi = AppBudgetFieldUi(),
    val monthly: AppBudgetFieldUi = AppBudgetFieldUi(),
)

/**
 * Builds the dialog state from the stored app budgets.
 *
 * Same contract as the device editor: the operator's in-flight text wins over Room, an invalid
 * non-empty entry is flagged, and an emptied field is a pause in editing rather than an error.
 */
internal fun appBudgetUiState(
    packageName: String,
    label: String,
    budgets: List<BudgetEntity>,
    dailyText: String? = null,
    monthlyText: String? = null,
): AppBudgetUiState {
    val rows = budgets.filter { it.scope == BudgetScope.APP && it.targetId == packageName }
    return AppBudgetUiState(
        packageName = packageName,
        label = label,
        daily = fieldUi(rows.firstOrNull { it.period == BudgetPeriod.DAILY }, dailyText),
        monthly = fieldUi(rows.firstOrNull { it.period == BudgetPeriod.MONTHLY }, monthlyText),
    )
}

private fun fieldUi(row: BudgetEntity?, userText: String?): AppBudgetFieldUi = AppBudgetFieldUi(
    text = userText ?: formatLimitGb(row?.limitBytes ?: 0L),
    enabled = row?.enabled ?: false,
    error = userText != null && userText.isNotEmpty() && parseLimitBytes(userText) == null,
)
