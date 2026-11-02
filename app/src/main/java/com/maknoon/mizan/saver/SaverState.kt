package com.maknoon.mizan.saver

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Why the firewall could not stay on; every case is shown verbatim on the Guard screen. */
enum class SaverFailure {
    CONSENT_MISSING,
    NO_APPS,
    ESTABLISH_FAILED,
    REVOKED,
}

/**
 * What the VPN service currently knows.
 *
 * [serviceRunning] says the foreground service exists; [active] says a tunnel is established.
 * They differ while the settings are being read and [blockedCount] is the number of apps the
 * tunnel actually admits, which can be smaller than the stored list after an uninstall.
 * [unmetered] says the service is on but deliberately has no tunnel because the connection is
 * unmetered, so the blocked apps are currently allowed through (6.4).
 */
data class SaverState(
    val serviceRunning: Boolean = false,
    val active: Boolean = false,
    val blockedCount: Int = 0,
    val failure: SaverFailure? = null,
    val unmetered: Boolean = false,
)

/**
 * The single in-process source of truth for the firewall, observed by the Guard screen and by
 * the launch resume check, so the switch and the notification cannot disagree.
 */
object SaverStateHolder {

    private val currentState = MutableStateFlow(SaverState())

    val state: StateFlow<SaverState> = currentState.asStateFlow()

    fun publish(state: SaverState) {
        currentState.value = state
    }

    /** Keeps a failure visible after the service stops itself; clears the running flags. */
    fun markServiceDestroyed() {
        currentState.update {
            it.copy(serviceRunning = false, active = false, blockedCount = 0, unmetered = false)
        }
    }

    /** An explicit stop starts from a clean slate, including any old failure. */
    fun markStopped() {
        currentState.value = SaverState()
    }
}
