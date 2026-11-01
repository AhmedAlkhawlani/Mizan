package com.maknoon.mizan.core.domain.budget

/**
 * One enabled cap the engine evaluates. The caller passes only enabled rows with a positive
 * limit, so the engine never has to know how budgets are stored.
 */
data class BudgetRule(
    val scope: BudgetScope,
    val targetId: String,
    val period: BudgetPeriod,
    val limitBytes: Long,
)

/**
 * Usage to compare against the rules, split by scope and period.
 *
 * The device and the apps are separate maps because a package name can never address the
 * device; a missing package simply has no usage yet.
 */
data class BudgetUsage(
    val deviceDailyBytes: Long = 0L,
    val deviceCycleBytes: Long = 0L,
    val appDailyBytes: Map<String, Long> = emptyMap(),
    val appCycleBytes: Map<String, Long> = emptyMap(),
)

/**
 * A threshold already notified for one rule in one period, as persisted by the caller.
 *
 * [periodStartEpochDay] is the day the budget resets: the local day for a daily budget and the
 * billing-cycle start for a monthly one. Storing it is what makes "once per cycle" survive a
 * restart without the caller having to clear anything at midnight.
 */
data class NotifiedBudgetLevel(
    val scope: BudgetScope,
    val targetId: String,
    val period: BudgetPeriod,
    val periodStartEpochDay: Long,
    val level: BudgetLevel,
)

/**
 * One alert to show now.
 *
 * [level] is the highest new threshold; [reachedLevels] contains every threshold the usage has
 * crossed, including one skipped by a jump. Persisting all of [reachedLevels] prevents a late
 * warning after an exceed notice.
 */
data class BudgetAlert(
    val scope: BudgetScope,
    val targetId: String,
    val period: BudgetPeriod,
    val periodStartEpochDay: Long,
    val level: BudgetLevel,
    val reachedLevels: Set<BudgetLevel>,
    val usedBytes: Long,
    val limitBytes: Long,
    val ratio: Double,
) {
    /** The ledger rows to persist after showing this alert, warning before exceeded. */
    fun ledgerEntries(): List<NotifiedBudgetLevel> =
        reachedLevels.sortedBy { it.ordinal }.map { reached ->
            NotifiedBudgetLevel(
                scope = scope,
                targetId = targetId,
                period = period,
                periodStartEpochDay = periodStartEpochDay,
                level = reached,
            )
        }
}

/**
 * Decides which budgets alert right now (docs/05 4.3).
 *
 * [BudgetPolicy] already answers "is this one cap over its threshold, and what is new since the
 * ledger"; this engine applies it to every budget, chooses the matching usage and period start,
 * and ignores ledger rows from an earlier day or cycle. It is clock-free: the caller supplies
 * today and the cycle start, so the reset behaviour is unit tested without waiting for midnight.
 */
class BudgetAlertEngine(
    private val policy: BudgetPolicy = BudgetPolicy(),
) {

    fun evaluate(
        rules: List<BudgetRule>,
        usage: BudgetUsage,
        todayEpochDay: Long,
        cycleStartEpochDay: Long,
        notified: List<NotifiedBudgetLevel> = emptyList(),
    ): List<BudgetAlert> = rules.asSequence()
        .filter { it.limitBytes > 0L }
        .mapNotNull { rule ->
            val periodStart = when (rule.period) {
                BudgetPeriod.DAILY -> todayEpochDay
                BudgetPeriod.MONTHLY -> cycleStartEpochDay
            }
            val used = usage.usedBytes(rule)
            val notifiedLevels = notified.asSequence()
                .filter {
                    it.scope == rule.scope &&
                        it.targetId == rule.targetId &&
                        it.period == rule.period &&
                        it.periodStartEpochDay == periodStart
                }
                .map { it.level }
                .toSet()
            val decision = policy.evaluate(used, rule.limitBytes, notifiedLevels)
            decision.level?.let { level ->
                BudgetAlert(
                    scope = rule.scope,
                    targetId = rule.targetId,
                    period = rule.period,
                    periodStartEpochDay = periodStart,
                    level = level,
                    reachedLevels = decision.reachedLevels,
                    usedBytes = used.coerceAtLeast(0L),
                    limitBytes = rule.limitBytes,
                    ratio = decision.ratio,
                )
            }
        }
        .sortedWith(
            compareByDescending<BudgetAlert> { it.ratio }
                .thenBy { it.scope }
                .thenBy { it.targetId }
                .thenBy { it.period },
        )
        .toList()
}

private fun BudgetUsage.usedBytes(rule: BudgetRule): Long = when (rule.scope) {
    BudgetScope.DEVICE -> when (rule.period) {
        BudgetPeriod.DAILY -> deviceDailyBytes
        BudgetPeriod.MONTHLY -> deviceCycleBytes
    }

    BudgetScope.APP -> {
        val perApp = when (rule.period) {
            BudgetPeriod.DAILY -> appDailyBytes
            BudgetPeriod.MONTHLY -> appCycleBytes
        }
        perApp[rule.targetId] ?: 0L
    }
}
