package com.maknoon.mizan.core.data.usage

import android.content.Context
import android.util.Log
import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import com.maknoon.mizan.core.data.db.DeviceHourlyEntity
import com.maknoon.mizan.core.data.db.DeviceStateEntity
import com.maknoon.mizan.core.data.db.DeviceUsageDao
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.domain.accounting.DailyUsage
import com.maknoon.mizan.core.domain.accounting.TrafficAccountant
import com.maknoon.mizan.core.domain.accounting.TrafficSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Persists what the accountant measures.
 *
 * Three rows are written: the day's running total, the accountant's accumulator, and the
 * traffic observed during the current local hour. The accumulator is what makes a reboot
 * survivable — counters restart at zero, but the stored state lets the next sample add
 * post-boot traffic to the total already counted.
 *
 * Writes are throttled. A sample arrives every second, and writing every second would cost
 * flash and battery for no benefit: if the process dies, the next sample rebuilds the total
 * from the boot counters anyway, so the most a throttle can lose across a reboot is one
 * window of traffic, never the whole day.
 *
 * Hourly buckets are accumulated from the deltas between consecutive samples, never from the
 * day's running total: the first sample after the process starts carries everything counted
 * since midnight, and attributing that to the current hour would falsify the chart. This also
 * means an hour shows only traffic observed while monitoring ran, which the chart states.
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

    private var lastRecordedUsage: DailyUsage? = null
    private var pendingHour: PendingHour? = null
    private var lastHourlyFlushAtMillis: Long? = null

    /** Records one sample and returns the day's usage so far. */
    suspend fun record(snapshot: TrafficSnapshot): DailyUsage {
        val usage = accountant().record(snapshot)
        // The accountant has the sample either way, so a rejected write costs only storage: the
        // hour bucket stays pending and the next sample carries it again. An exception here used
        // to escape into the monitoring worker and kill the app with it (§12/8).
        try {
            accumulateHourly(usage, snapshot.atMillis)
            if (shouldPersist(usage, snapshot.atMillis)) {
                persist(usage, snapshot.atMillis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sample not stored", e)
        }
        return usage
    }

    fun observeDailyRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<DeviceDailyEntity>> =
        dao.observeDailyRange(fromEpochDay, toEpochDay)

    fun observeHourly(epochDay: Long): Flow<List<DeviceHourlyEntity>> =
        dao.observeHourly(epochDay)

    suspend fun allDaily(): List<DeviceDailyEntity> = dao.allDaily()

    private suspend fun accumulateHourly(usage: DailyUsage, atMillis: Long) {
        val previous = lastRecordedUsage
        lastRecordedUsage = usage

        // A new day (or the very first sample) has no observed delta: the day total it carries
        // predates this process and cannot be assigned to an hour.
        val sameDay = previous != null && previous.epochDay == usage.epochDay
        val observedRx = if (sameDay) (usage.rxBytes - previous!!.rxBytes).coerceAtLeast(0L) else 0L
        val observedTx = if (sameDay) (usage.txBytes - previous!!.txBytes).coerceAtLeast(0L) else 0L

        val local = Instant.ofEpochMilli(atMillis).atZone(zone)
        val epochDay = local.toLocalDate().toEpochDay()
        val hour = local.hour

        val open = pendingHour
        if (open != null && (open.epochDay != epochDay || open.hour != hour)) {
            // The old hour must reach the database before the new bucket replaces it.
            flushHourly(atMillis)
        }
        if (observedRx > 0L || observedTx > 0L) {
            val bucket = pendingHour ?: PendingHour(epochDay, hour).also { pendingHour = it }
            bucket.rxBytes += observedRx
            bucket.txBytes += observedTx
        }

        val lastFlush = lastHourlyFlushAtMillis
        if (lastFlush == null) {
            lastHourlyFlushAtMillis = atMillis
            return
        }
        val elapsed = atMillis - lastFlush
        // A clock that moved backwards must not stall the flush until it catches up.
        if (elapsed < 0L || elapsed >= persistIntervalMillis) {
            flushHourly(atMillis)
        }
    }

    private suspend fun flushHourly(atMillis: Long) {
        lastHourlyFlushAtMillis = atMillis
        val bucket = pendingHour ?: return
        dao.addHourly(
            epochDay = bucket.epochDay,
            hour = bucket.hour,
            rxBytes = bucket.rxBytes,
            txBytes = bucket.txBytes,
        )
        pendingHour = null
    }

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
                mobileRxBytes = usage.mobileRxBytes,
                mobileTxBytes = usage.mobileTxBytes,
                wifiRxBytes = usage.wifiRxBytes,
                wifiTxBytes = usage.wifiTxBytes,
            ),
        )
        memoryState.load()?.let { state -> dao.upsertState(DeviceStateEntity.from(state)) }
        lastPersistedAtMillis = atMillis
        lastPersistedDay = usage.epochDay
    }

    private class PendingHour(
        val epochDay: Long,
        val hour: Int,
        var rxBytes: Long = 0L,
        var txBytes: Long = 0L,
    )

    companion object {
        private const val TAG = "DeviceUsageRepository"

        const val DEFAULT_PERSIST_INTERVAL_MILLIS = 5_000L

        fun create(context: Context): DeviceUsageRepository = DeviceUsageRepository(
            dao = MizanDatabaseProvider.get(context).deviceUsageDao(),
        )
    }
}
