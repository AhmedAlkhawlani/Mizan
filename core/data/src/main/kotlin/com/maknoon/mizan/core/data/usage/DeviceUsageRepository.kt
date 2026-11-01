package com.maknoon.mizan.core.data.usage

import android.content.Context
import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import com.maknoon.mizan.core.data.db.DeviceStateEntity
import com.maknoon.mizan.core.data.db.DeviceUsageDao
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.domain.accounting.DailyUsage
import com.maknoon.mizan.core.domain.accounting.TrafficAccountant
import com.maknoon.mizan.core.domain.accounting.TrafficSnapshot
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Persists what the accountant measures.
 *
 * Two rows are written: the day's running total and the accountant's accumulator. The
 * accumulator is what makes a reboot survivable — counters restart at zero, but the stored
 * state lets the next sample add post-boot traffic to the total already counted.
 *
 * Writes are throttled. A sample arrives every second, and writing every second would cost
 * flash and battery for no benefit: if the process dies, the next sample rebuilds the total
 * from the boot counters anyway, so the most a throttle can lose across a reboot is one
 * window of traffic, never the whole day.
 */
class DeviceUsageRepository(
    private val dao: DeviceUsageDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val persistIntervalMillis: Long = DEFAULT_PERSIST_INTERVAL_MILLIS,
) {

    private val memoryState = InMemoryUsageStateStore()

    private var accountant: TrafficAccountant? = null
    private var lastPersistedAtMillis: Long? = null
    private var lastPersistedDay: Long? = null

    /** Records one sample and returns the day's usage so far. */
    suspend fun record(snapshot: TrafficSnapshot): DailyUsage {
        val usage = accountant().record(snapshot)
        if (shouldPersist(usage, snapshot.atMillis)) {
            persist(usage, snapshot.atMillis)
        }
        return usage
    }

    fun observeDailyRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<DeviceDailyEntity>> =
        dao.observeDailyRange(fromEpochDay, toEpochDay)

    private suspend fun accountant(): TrafficAccountant {
        accountant?.let { return it }
        dao.state()?.let { stored -> memoryState.save(stored.toUsageState()) }
        return TrafficAccountant(memoryState, zone).also { accountant = it }
    }

    private fun shouldPersist(usage: DailyUsage, atMillis: Long): Boolean {
        val lastAt = lastPersistedAtMillis ?: return true
        if (lastPersistedDay != usage.epochDay) return true
        val elapsed = atMillis - lastAt
        // A clock that moved backwards must not stall writes until it catches up.
        return elapsed < 0 || elapsed >= persistIntervalMillis
    }

    private suspend fun persist(usage: DailyUsage, atMillis: Long) {
        dao.upsertDaily(
            DeviceDailyEntity(
                epochDay = usage.epochDay,
                rxBytes = usage.rxBytes,
                txBytes = usage.txBytes,
                partial = usage.partial,
                updatedAtMillis = atMillis,
            ),
        )
        memoryState.load()?.let { state -> dao.upsertState(DeviceStateEntity.from(state)) }
        lastPersistedAtMillis = atMillis
        lastPersistedDay = usage.epochDay
    }

    companion object {
        const val DEFAULT_PERSIST_INTERVAL_MILLIS = 5_000L

        fun create(context: Context): DeviceUsageRepository = DeviceUsageRepository(
            dao = MizanDatabaseProvider.get(context).deviceUsageDao(),
        )
    }
}
