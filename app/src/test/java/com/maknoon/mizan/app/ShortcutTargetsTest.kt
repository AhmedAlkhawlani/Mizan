package com.maknoon.mizan.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShortcutTargetsTest {

    @Test
    fun `apps opens the apps tab`() {
        assertThat(shortcutTarget("apps"))
            .isEqualTo(ShortcutTarget.Destination(MizanDestination.Apps.route))
    }

    @Test
    fun `history opens the history tab`() {
        assertThat(shortcutTarget("history"))
            .isEqualTo(ShortcutTarget.Destination(MizanDestination.History.route))
    }

    @Test
    fun `budget opens the budget editor`() {
        assertThat(shortcutTarget("budget"))
            .isEqualTo(ShortcutTarget.Destination(BUDGET_ROUTE))
    }

    @Test
    fun `stop monitoring is an action`() {
        assertThat(shortcutTarget("stop-monitoring"))
            .isEqualTo(ShortcutTarget.StopMonitoring)
    }

    @Test
    fun `an unknown host maps to nothing`() {
        assertThat(shortcutTarget("elsewhere")).isNull()
    }

    @Test
    fun `a missing host maps to nothing`() {
        assertThat(shortcutTarget(null)).isNull()
    }
}
