package com.maknoon.mizan.core.domain.budget

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BudgetPolicyTest {

    private val policy = BudgetPolicy()
    private val limit = 2_000_000_000L

    @Test
    fun `usage below the warning threshold raises nothing`() {
        val decision = policy.evaluate(usedBytes = 1_500_000_000, limitBytes = limit)

        assertThat(decision.level).isNull()
        assertThat(decision.reachedLevels).isEmpty()
        assertThat(decision.ratio).isWithin(0.0001).of(0.75)
    }

    @Test
    fun `exactly eighty percent raises the warning`() {
        val decision = policy.evaluate(usedBytes = 1_600_000_000, limitBytes = limit)

        assertThat(decision.level).isEqualTo(BudgetLevel.WARNING)
        assertThat(decision.reachedLevels).containsExactly(BudgetLevel.WARNING)
    }

    @Test
    fun `a warning that was already notified is not raised again`() {
        val decision = policy.evaluate(
            usedBytes = 1_700_000_000,
            limitBytes = limit,
            notifiedLevels = setOf(BudgetLevel.WARNING),
        )

        assertThat(decision.level).isNull()
        assertThat(decision.reachedLevels).containsExactly(BudgetLevel.WARNING)
    }

    @Test
    fun `reaching the limit raises the exceeded level`() {
        val decision = policy.evaluate(
            usedBytes = limit,
            limitBytes = limit,
            notifiedLevels = setOf(BudgetLevel.WARNING),
        )

        assertThat(decision.level).isEqualTo(BudgetLevel.EXCEEDED)
        assertThat(decision.reachedLevels)
            .containsExactly(BudgetLevel.WARNING, BudgetLevel.EXCEEDED)
    }

    @Test
    fun `jumping past the limit records the warning as reached too`() {
        val decision = policy.evaluate(usedBytes = 2_100_000_000, limitBytes = limit)

        assertThat(decision.level).isEqualTo(BudgetLevel.EXCEEDED)
        assertThat(decision.reachedLevels)
            .containsExactly(BudgetLevel.WARNING, BudgetLevel.EXCEEDED)
        assertThat(policy.ledgerAfter(decision, emptySet()))
            .containsExactly(BudgetLevel.WARNING, BudgetLevel.EXCEEDED)
    }

    @Test
    fun `nothing is raised once both levels were notified`() {
        val decision = policy.evaluate(
            usedBytes = 3_000_000_000,
            limitBytes = limit,
            notifiedLevels = setOf(BudgetLevel.WARNING, BudgetLevel.EXCEEDED),
        )

        assertThat(decision.level).isNull()
    }

    @Test
    fun `a new cycle with an empty ledger warns again`() {
        val first = policy.evaluate(usedBytes = 1_800_000_000, limitBytes = limit)
        val ledger = policy.ledgerAfter(first, emptySet())

        val restarted = policy.evaluate(usedBytes = 1_800_000_000, limitBytes = limit)

        assertThat(ledger).containsExactly(BudgetLevel.WARNING)
        assertThat(restarted.level).isEqualTo(BudgetLevel.WARNING)
    }

    @Test
    fun `no budget configured produces no decision`() {
        assertThat(policy.evaluate(usedBytes = 500_000, limitBytes = 0).level).isNull()
        assertThat(policy.evaluate(usedBytes = 500_000, limitBytes = -1).reachedLevels).isEmpty()
    }

    @Test
    fun `negative usage is treated as zero`() {
        val decision = policy.evaluate(usedBytes = -5_000, limitBytes = limit)

        assertThat(decision.level).isNull()
        assertThat(decision.ratio).isEqualTo(0.0)
    }

    @Test
    fun `an overrun reports a ratio above one`() {
        val decision = policy.evaluate(usedBytes = limit * 2, limitBytes = limit)

        assertThat(decision.ratio).isWithin(0.0001).of(2.0)
    }

    @Test
    fun `percentage is rounded and clamped for display`() {
        assertThat(policy.percentUsed(0, limit)).isEqualTo(0)
        assertThat(policy.percentUsed(1_240_000_000, limit)).isEqualTo(62)
        assertThat(policy.percentUsed(3_000_000_000, limit)).isEqualTo(100)
        assertThat(policy.percentUsed(1_000, 0)).isEqualTo(0)
    }

    @Test
    fun `remaining budget never goes negative`() {
        assertThat(policy.remainingBytes(500_000_000, limit)).isEqualTo(1_500_000_000)
        assertThat(policy.remainingBytes(3_000_000_000, limit)).isEqualTo(0)
    }

    @Test
    fun `a custom warning threshold is honoured`() {
        val strict = BudgetPolicy(warningThreshold = 0.5)

        assertThat(strict.evaluate(usedBytes = 1_000_000_000, limitBytes = limit).level)
            .isEqualTo(BudgetLevel.WARNING)
    }

    @Test
    fun `an out of range warning threshold is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { BudgetPolicy(warningThreshold = 1.5) }
    }
}
