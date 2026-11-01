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
        )
    }
}
