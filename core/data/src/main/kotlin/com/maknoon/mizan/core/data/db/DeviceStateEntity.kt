package com.maknoon.mizan.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maknoon.mizan.core.domain.accounting.UsageState

/**
 * The accountant's accumulator, stored so a process death or a reboot resumes rather than
 * restarts (docs/03). Exactly one row exists, with [SINGLETON_ID].
 */
@Entity(tableName = "device_state")
data class DeviceStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val epochDay: Long,
    val bootEpochMillis: Long,
    val lastRxBytes: Long,
    val lastTxBytes: Long,
    val todayRxBytes: Long,
    val todayTxBytes: Long,
    val updatedAtMillis: Long,
    val startedAtMillis: Long,
    val partial: Boolean,
    val lastMobileRxBytes: Long? = null,
    val lastMobileTxBytes: Long? = null,
    val todayMobileRxBytes: Long? = null,
    val todayMobileTxBytes: Long? = null,
    val lastLoopbackRxBytes: Long? = null,
    val lastLoopbackTxBytes: Long? = null,
    val todayLoopbackRxBytes: Long? = null,
    val todayLoopbackTxBytes: Long? = null,
) {
    fun toUsageState(): UsageState = UsageState(
        epochDay = epochDay,
        bootEpochMillis = bootEpochMillis,
        lastRxBytes = lastRxBytes,
        lastTxBytes = lastTxBytes,
        todayRxBytes = todayRxBytes,
        todayTxBytes = todayTxBytes,
        updatedAtMillis = updatedAtMillis,
        startedAtMillis = startedAtMillis,
        partial = partial,
        lastMobileRxBytes = lastMobileRxBytes,
        lastMobileTxBytes = lastMobileTxBytes,
        todayMobileRxBytes = todayMobileRxBytes,
        todayMobileTxBytes = todayMobileTxBytes,
        lastLoopbackRxBytes = lastLoopbackRxBytes,
        lastLoopbackTxBytes = lastLoopbackTxBytes,
        todayLoopbackRxBytes = todayLoopbackRxBytes,
        todayLoopbackTxBytes = todayLoopbackTxBytes,
    )

    companion object {
        const val SINGLETON_ID = 1

        fun from(state: UsageState): DeviceStateEntity = DeviceStateEntity(
            epochDay = state.epochDay,
            bootEpochMillis = state.bootEpochMillis,
            lastRxBytes = state.lastRxBytes,
            lastTxBytes = state.lastTxBytes,
            todayRxBytes = state.todayRxBytes,
            todayTxBytes = state.todayTxBytes,
            updatedAtMillis = state.updatedAtMillis,
            startedAtMillis = state.startedAtMillis,
            partial = state.partial,
            lastMobileRxBytes = state.lastMobileRxBytes,
            lastMobileTxBytes = state.lastMobileTxBytes,
            todayMobileRxBytes = state.todayMobileRxBytes,
            todayMobileTxBytes = state.todayMobileTxBytes,
            lastLoopbackRxBytes = state.lastLoopbackRxBytes,
            lastLoopbackTxBytes = state.lastLoopbackTxBytes,
            todayLoopbackRxBytes = state.todayLoopbackRxBytes,
            todayLoopbackTxBytes = state.todayLoopbackTxBytes,
        )
    }
}
