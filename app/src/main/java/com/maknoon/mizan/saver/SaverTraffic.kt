package com.maknoon.mizan.saver

import com.maknoon.mizan.core.domain.saver.BlockedTrafficSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process snapshot of the bytes the active tunnel has dropped, observed by the Guard screen.
 *
 * It lives outside [MizanVpnService] for the same reason [SaverStateHolder] does: the UI reads it
 * while the service owns the counting, and a system kill must not erase the last published value.
 */
object SaverTrafficHolder {

    private val current = MutableStateFlow(BlockedTrafficSnapshot())

    val snapshot: StateFlow<BlockedTrafficSnapshot> = current.asStateFlow()

    fun publish(value: BlockedTrafficSnapshot) {
        current.value = value
    }

    fun reset() {
        current.value = BlockedTrafficSnapshot()
    }
}
