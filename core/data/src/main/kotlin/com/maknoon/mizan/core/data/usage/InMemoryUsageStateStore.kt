package com.maknoon.mizan.core.data.usage

import com.maknoon.mizan.core.domain.accounting.UsageState
import com.maknoon.mizan.core.domain.accounting.UsageStateStore

/**
 * Keeps the accountant's accumulator in memory.
 *
 * This is the phase 1 store: the live figure must work before the database exists. Nothing is
 * lost that matters, because the counters themselves are cumulative since boot — if the process
 * dies mid-day the accountant rebuilds today's total from the counters. What is lost is the
 * [UsageState.partial] nuance across a process death, which Room will preserve when the daily
 * history lands in phase 2.
 */
class InMemoryUsageStateStore : UsageStateStore {

    @Volatile
    private var state: UsageState? = null

    override fun load(): UsageState? = state

    override fun save(state: UsageState) {
        this.state = state
    }
}
