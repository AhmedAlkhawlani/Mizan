package com.maknoon.mizan.core.data.usage

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.domain.accounting.UsageState
import org.junit.Test

class InMemoryUsageStateStoreTest {

    private val state = UsageState(
        epochDay = 20_000L,
        bootEpochMillis = 1_000L,
        lastRxBytes = 10L,
        lastTxBytes = 20L,
        todayRxBytes = 30L,
        todayTxBytes = 40L,
        updatedAtMillis = 5_000L,
        startedAtMillis = 0L,
        partial = false,
    )

    @Test
    fun `loading before anything is saved yields null`() {
        assertThat(InMemoryUsageStateStore().load()).isNull()
    }

    @Test
    fun `saved state is returned unchanged`() {
        val store = InMemoryUsageStateStore()
        store.save(state)

        assertThat(store.load()).isEqualTo(state)
    }

    @Test
    fun `the latest save wins`() {
        val store = InMemoryUsageStateStore()
        store.save(state)
        store.save(state.copy(todayRxBytes = 99L))

        assertThat(store.load()!!.todayRxBytes).isEqualTo(99L)
    }

    @Test
    fun `each store instance starts empty`() {
        InMemoryUsageStateStore().save(state)

        assertThat(InMemoryUsageStateStore().load()).isNull()
    }
}
