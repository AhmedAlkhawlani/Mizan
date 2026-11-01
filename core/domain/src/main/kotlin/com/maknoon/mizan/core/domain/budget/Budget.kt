package com.maknoon.mizan.core.domain.budget

/** Whether a budget caps the whole device or a single app. */
enum class BudgetScope { DEVICE, APP }

/** Whether a budget resets every local day or every billing cycle. */
enum class BudgetPeriod { DAILY, MONTHLY }

/** The target id that addresses the device as a whole, as stored in the budgets table. */
const val DEVICE_TARGET_ID = "device"
