package com.maknoon.mizan.ui.apps

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.AppUsageTotal
import org.junit.Test

class AppsUiStateTest {

    @Test
    fun `rows are heaviest first with a share of the listed total`() {
        val state = appsUiState(
            totals = listOf(
                total("com.example.small", label = "Small", rx = 1_000, tx = 0),
                total("com.example.big", label = "Big", rx = 3_000, tx = 1_000),
            ),
            period = AppsPeriod.WEEK,
        )

        assertThat(state.rows.map { it.packageName })
            .containsExactly("com.example.big", "com.example.small").inOrder()
        assertThat(state.totalBytes).isEqualTo(5_000)
        assertThat(state.rows[0].bytes).isEqualTo(4_000)
        assertThat(state.rows[0].share).isWithin(0.0001f).of(0.8f)
        assertThat(state.rows[1].share).isWithin(0.0001f).of(0.2f)
        assertThat(state.period).isEqualTo(AppsPeriod.WEEK)
    }

    @Test
    fun `a missing label falls back to the package name`() {
        val state = appsUiState(listOf(total("com.example.app", label = null)), AppsPeriod.TODAY)

        assertThat(state.rows.single().label).isEqualTo("com.example.app")
    }

    @Test
    fun `an app with no traffic is not a row`() {
        val state = appsUiState(
            listOf(
                total("com.example.idle", rx = 0, tx = 0),
                total("com.example.active", rx = 10, tx = 5),
            ),
            AppsPeriod.TODAY,
        )

        assertThat(state.rows.map { it.packageName }).containsExactly("com.example.active")
        assertThat(state.totalBytes).isEqualTo(15)
    }

    @Test
    fun `zero total never divides`() {
        val state = appsUiState(emptyList(), AppsPeriod.CYCLE)

        assertThat(state.rows).isEmpty()
        assertThat(state.totalBytes).isEqualTo(0)
        assertThat(state.lastSyncedAtMillis).isNull()
    }

    @Test
    fun `the newest sync time represents the list`() {
        val state = appsUiState(
            listOf(
                total("com.example.a", lastSyncedAt = 1_000),
                total("com.example.b", lastSyncedAt = 9_000),
            ),
            AppsPeriod.WEEK,
        )

        assertThat(state.lastSyncedAtMillis).isEqualTo(9_000)
    }

    private fun total(
        packageName: String,
        label: String? = "App",
        rx: Long = 1,
        tx: Long = 1,
        lastSyncedAt: Long? = 500,
    ) = AppUsageTotal(
        packageName = packageName,
        label = label,
        rxBytes = rx,
        txBytes = tx,
        lastSyncedAt = lastSyncedAt,
    )
}
