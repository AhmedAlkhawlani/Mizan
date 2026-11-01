package com.maknoon.mizan.core.domain.budget

import kotlin.math.roundToInt

/** The two thresholds a budget reports, in ascending order. */
enum class BudgetLevel { WARNING, EXCEEDED }

/**
 * Outcome of one evaluation.
 *
 * [level] is the threshold to notify right now, or `null` when there is nothing new to say.
 * [reachedLevels] is every threshold the usage has crossed, which the caller persists as its
 * notification ledger: a jump straight past 100% therefore records both levels, so a warning
 * can never arrive after the exceed notice.
 */
data class BudgetDecision(
    val level: BudgetLevel?,
    val reachedLevels: Set<BudgetLevel>,
    val ratio: Double,
)

/**
 * Decides when a budget should alert: once at the warning threshold and once when the budget
 * is exceeded, per cycle. The policy itself is stateless — the caller owns the ledger — so a
 * notification fired in a previous process is never repeated after a restart.
 */
class BudgetPolicy(
    private val warningThreshold: Double = DEFAULT_WARNING_THRESHOLD,
) {

    init {
        require(warningThreshold in 0.0..1.0) {
            "warningThreshold must be within 0..1 but was $warningThreshold"
        }
    }

    /**
     * Evaluates [usedBytes] against [limitBytes], given the levels already notified in this
     * cycle. A limit of zero or less means no budget is configured and yields no decision.
     */
    fun evaluate(
        usedBytes: Long,
        limitBytes: Long,
        notifiedLevels: Set<BudgetLevel> = emptySet(),
    ): BudgetDecision {
        if (limitBytes <= 0L) return BudgetDecision(level = null, reachedLevels = emptySet(), ratio = 0.0)

        val used = usedBytes.coerceAtLeast(0L)
        val ratio = used.toDouble() / limitBytes
        val reached = buildSet {
            if (ratio >= warningThreshold) add(BudgetLevel.WARNING)
            if (ratio >= EXCEEDED_RATIO) add(BudgetLevel.EXCEEDED)
        }
        val level = when {
            BudgetLevel.EXCEEDED in reached && BudgetLevel.EXCEEDED !in notifiedLevels -> BudgetLevel.EXCEEDED
            BudgetLevel.WARNING in reached && BudgetLevel.WARNING !in notifiedLevels -> BudgetLevel.WARNING
            else -> null
        }
        return BudgetDecision(level = level, reachedLevels = reached, ratio = ratio)
    }

    /** The ledger to persist after [decision], so the same level is never notified twice. */
    fun ledgerAfter(decision: BudgetDecision, notifiedLevels: Set<BudgetLevel>): Set<BudgetLevel> =
        notifiedLevels + decision.reachedLevels

    /** Percentage for display, clamped to 0..100 so an overrun never reads as 118 of 100. */
    fun percentUsed(usedBytes: Long, limitBytes: Long): Int {
        if (limitBytes <= 0L) return 0
        val used = usedBytes.coerceAtLeast(0L)
        return ((used.toDouble() / limitBytes) * 100.0).roundToInt().coerceIn(0, 100)
    }

    /** Bytes left in the budget, never below zero. */
    fun remainingBytes(usedBytes: Long, limitBytes: Long): Long =
        (limitBytes - usedBytes.coerceAtLeast(0L)).coerceAtLeast(0L)

    companion object {
        const val DEFAULT_WARNING_THRESHOLD = 0.8
        const val EXCEEDED_RATIO = 1.0
    }
}
