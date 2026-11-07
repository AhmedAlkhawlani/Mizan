package com.maknoon.mizan.ui.saver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuardToggleTest {

    @Test
    fun `pressing the switch with no app chosen opens the picker instead of doing nothing`() {
        // The regression this file exists for: the switch used to be disabled here, and a
        // disabled switch swallows the press, so a fresh install looked broken.
        assertThat(guardToggleAction(enabled = true, blockedCount = 0, consentGranted = true))
            .isEqualTo(GuardToggleAction.CHOOSE_APPS)
    }

    @Test
    fun `the picker is offered before the consent dialog when both are missing`() {
        assertThat(guardToggleAction(enabled = true, blockedCount = 0, consentGranted = false))
            .isEqualTo(GuardToggleAction.CHOOSE_APPS)
    }

    @Test
    fun `a chosen app without consent asks the system`() {
        assertThat(guardToggleAction(enabled = true, blockedCount = 2, consentGranted = false))
            .isEqualTo(GuardToggleAction.REQUEST_CONSENT)
    }

    @Test
    fun `both preconditions met starts the tunnel`() {
        assertThat(guardToggleAction(enabled = true, blockedCount = 1, consentGranted = true))
            .isEqualTo(GuardToggleAction.START)
    }

    @Test
    fun `turning the switch off always stops, whatever the preconditions say`() {
        assertThat(guardToggleAction(enabled = false, blockedCount = 0, consentGranted = false))
            .isEqualTo(GuardToggleAction.STOP)
        assertThat(guardToggleAction(enabled = false, blockedCount = 3, consentGranted = true))
            .isEqualTo(GuardToggleAction.STOP)
    }
}
