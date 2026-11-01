package com.maknoon.mizan.core.data.usage

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import com.maknoon.mizan.core.data.db.DeviceHourlyEntity
import com.maknoon.mizan.core.data.db.DeviceStateEntity
import com.maknoon.mizan.core.data.db.DeviceUsageDao
import com.maknoon.mizan.core.domain.accounting.TrafficSnapshot
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeviceUsageRepositoryTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val day1 = LocalDate.of(2026, 3, 9)
    private val day2 = LocalDate.of(2026, 3, 10)

    private val dao = FakeDeviceUsageDao()
    private val repository = DeviceUsageRepository(dao, zone)

    private fun millisAt(
        date: LocalDate = day1,
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
    ): Long = date.atStartOfDay(zone)
        .plusHours(hour.toLong())
        .plusMinutes(minute.toLong())
        .plusSeconds(second.toLong())
        .toInstant()
        .toEpochMilli()

    private fun snapshot(
        rx: Long,
        tx: Long = 0,
        bootEpochMillis: Long = millisAt(hour = 0, minute = 1),
        atMillis: Long,
    ) = TrafficSnapshot(rx, tx, bootEpochMillis, atMillis)

    @Test
    fun `the first sample writes both the daily row and the state row`() = runTest {
        repository.record(snapshot(rx = 4_096, atMillis = millisAt(hour = 8)))

        assertThat(dao.dailyRows).hasSize(1)
        assertThat(dao.dailyRows[day1.toEpochDay()]!!.rxBytes).isEqualTo(4_096)
        assertThat(dao.stateRow).isNotNull()
        assertThat(dao.stateWrites).isEqualTo(1)
    }

    @Test
    fun `samples inside the write window stay in memory`() = runTest {
        repository.record(snapshot(rx = 1_000, atMillis = millisAt(hour = 8, second = 0)))
        repository.record(snapshot(rx = 2_000, atMillis = millisAt(hour = 8, second = 1)))
        repository.record(snapshot(rx = 3_000, atMillis = millisAt(hour = 8, second = 2)))

        assertThat(dao.dailyWrites).isEqualTo(1)
        assertThat(dao.dailyRows[day1.toEpochDay()]!!.rxBytes).isEqualTo(1_000)
    }

    @Test
    fun `the row is written again once the window has passed`() = runTest {
        repository.record(snapshot(rx = 1_000, atMillis = millisAt(hour = 8, second = 0)))
        repository.record(snapshot(rx = 2_000, atMillis = millisAt(hour = 8, second = 6)))

        assertThat(dao.dailyWrites).isEqualTo(2)
        assertThat(dao.dailyRows[day1.toEpochDay()]!!.rxBytes).isEqualTo(2_000)
    }

    @Test
    fun `a new day is written immediately even inside the window`() = runTest {
        repository.record(snapshot(rx = 1_000, atMillis = millisAt(hour = 23, minute = 59, second = 59)))
        repository.record(
            snapshot(
                rx = 500,
                bootEpochMillis = millisAt(date = day2, hour = 0, minute = 1),
                atMillis = millisAt(date = day2, hour = 0, minute = 1),
            ),
        )

        assertThat(dao.dailyRows).hasSize(2)
        assertThat(dao.dailyRows[day1.toEpochDay()]!!.rxBytes).isEqualTo(1_000)
        assertThat(dao.dailyRows[day2.toEpochDay()]!!.rxBytes).isEqualTo(500)
    }

    @Test
    fun `a fresh repository resumes from the persisted state`() = runTest {
        repository.record(snapshot(rx = 1_000, atMillis = millisAt(hour = 8, second = 0)))

        val resumed = DeviceUsageRepository(dao, zone)
        val usage = resumed.record(snapshot(rx = 1_500, atMillis = millisAt(hour = 8, second = 6)))

        assertThat(usage.rxBytes).isEqualTo(1_500)
    }

    @Test
    fun `traffic after a reboot is added to the total already stored`() = runTest {
        repository.record(snapshot(rx = 1_000, atMillis = millisAt(hour = 8, second = 0)))

        val afterReboot = DeviceUsageRepository(dao, zone)
        val usage = afterReboot.record(
            snapshot(
                rx = 200,
                bootEpochMillis = millisAt(hour = 8, minute = 4),
                atMillis = millisAt(hour = 8, minute = 5),
            ),
        )

        assertThat(usage.rxBytes).isEqualTo(1_200)
    }

    @Test
    fun `a day that started before the boot is stored as partial`() = runTest {
        repository.record(
            snapshot(
                rx = 5_000,
                bootEpochMillis = millisAt(date = day1.minusDays(1), hour = 22),
                atMillis = millisAt(hour = 9),
            ),
        )

        val row = dao.dailyRows[day1.toEpochDay()]!!
        assertThat(row.partial).isTrue()
        assertThat(row.rxBytes).isEqualTo(0)
    }

    @Test
    fun `a clock that jumps backwards still writes the row`() = runTest {
        repository.record(snapshot(rx = 1_000, atMillis = millisAt(hour = 8, second = 30)))
        repository.record(snapshot(rx = 2_000, atMillis = millisAt(hour = 8, second = 1)))

        assertThat(dao.dailyWrites).isEqualTo(2)
        assertThat(dao.dailyRows[day1.toEpochDay()]!!.rxBytes).isEqualTo(2_000)
    }

    @Test
    fun `the state row keeps the boot instant the accountant relies on`() = runTest {
        val boot = millisAt(hour = 0, minute = 1)
        repository.record(snapshot(rx = 1_000, bootEpochMillis = boot, atMillis = millisAt(hour = 8)))

        assertThat(dao.stateRow!!.bootEpochMillis).isEqualTo(boot)
        assertThat(dao.stateRow!!.todayRxBytes).isEqualTo(1_000)
    }

    @Test
    fun `the first sample is not attributed to its hour`() = runTest {
        repository.record(snapshot(rx = 4_096, atMillis = millisAt(hour = 8)))

        assertThat(dao.hourlyRows).isEmpty()
    }

    @Test
    fun `observed deltas accumulate into the current hour`() = runTest {
        repository.record(snapshot(rx = 1_000, tx = 100, atMillis = millisAt(hour = 8, second = 0)))
        repository.record(snapshot(rx = 1_500, tx = 300, atMillis = millisAt(hour = 8, second = 1)))
        repository.record(snapshot(rx = 2_000, tx = 600, atMillis = millisAt(hour = 8, second = 6)))

        val bucket = dao.hourlyRows[day1.toEpochDay() to 8]!!
        assertThat(bucket.rxBytes).isEqualTo(1_000)
        assertThat(bucket.txBytes).isEqualTo(500)
    }

    @Test
    fun `crossing an hour flushes the previous bucket immediately`() = runTest {
        repository.record(
            snapshot(rx = 1_000, atMillis = millisAt(hour = 8, minute = 59, second = 58)),
        )
        repository.record(
            snapshot(rx = 1_400, atMillis = millisAt(hour = 8, minute = 59, second = 59)),
        )
        repository.record(snapshot(rx = 1_900, atMillis = millisAt(hour = 9, second = 0)))

        assertThat(dao.hourlyRows[day1.toEpochDay() to 8]!!.rxBytes).isEqualTo(400)
        assertThat(dao.hourlyRows[day1.toEpochDay() to 9]).isNull()

        repository.record(snapshot(rx = 2_000, atMillis = millisAt(hour = 9, second = 5)))

        assertThat(dao.hourlyRows[day1.toEpochDay() to 9]!!.rxBytes).isEqualTo(600)
    }

    @Test
    fun `a new day starts new hourly buckets`() = runTest {
        repository.record(
            snapshot(rx = 1_000, atMillis = millisAt(hour = 23, minute = 59, second = 58)),
        )
        repository.record(
            snapshot(rx = 2_000, atMillis = millisAt(hour = 23, minute = 59, second = 59)),
        )
        val day2Boot = millisAt(date = day2, hour = 0, minute = 1)
        repository.record(
            snapshot(rx = 300, bootEpochMillis = day2Boot, atMillis = day2Boot),
        )
        repository.record(
            snapshot(
                rx = 800,
                bootEpochMillis = day2Boot,
                atMillis = millisAt(date = day2, hour = 0, minute = 6),
            ),
        )

        assertThat(dao.hourlyRows[day1.toEpochDay() to 23]!!.rxBytes).isEqualTo(1_000)
        assertThat(dao.hourlyRows[day2.toEpochDay() to 0]!!.rxBytes).isEqualTo(500)
    }

    @Test
    fun `a resumed repository does not count the stored total as the current hour`() = runTest {
        repository.record(snapshot(rx = 1_000, atMillis = millisAt(hour = 8, second = 0)))

        val resumed = DeviceUsageRepository(dao, zone)
        resumed.record(snapshot(rx = 1_500, atMillis = millisAt(hour = 8, second = 1)))
        resumed.record(snapshot(rx = 2_000, atMillis = millisAt(hour = 8, second = 6)))

        assertThat(dao.hourlyRows[day1.toEpochDay() to 8]!!.rxBytes).isEqualTo(500)
    }

    @Test
    fun `hourly buckets add to the row written by an earlier process`() = runTest {
        repository.record(snapshot(rx = 1_000, atMillis = millisAt(hour = 8, second = 0)))
        repository.record(snapshot(rx = 1_500, atMillis = millisAt(hour = 8, second = 5)))

        val resumed = DeviceUsageRepository(dao, zone)
        resumed.record(snapshot(rx = 2_000, atMillis = millisAt(hour = 8, second = 20)))
        resumed.record(snapshot(rx = 2_400, atMillis = millisAt(hour = 8, second = 25)))

        assertThat(dao.hourlyRows[day1.toEpochDay() to 8]!!.rxBytes).isEqualTo(900)
    }

    private class FakeDeviceUsageDao : DeviceUsageDao {
        val dailyRows = linkedMapOf<Long, DeviceDailyEntity>()
        val hourlyRows = linkedMapOf<Pair<Long, Int>, DeviceHourlyEntity>()
        var stateRow: DeviceStateEntity? = null
        var dailyWrites = 0
            private set
        var stateWrites = 0
            private set

        override suspend fun daily(epochDay: Long): DeviceDailyEntity? = dailyRows[epochDay]

        override fun observeDailyRange(
            fromEpochDay: Long,
            toEpochDay: Long,
        ): Flow<List<DeviceDailyEntity>> = flowOf(
            dailyRows.values.filter { it.epochDay in fromEpochDay..toEpochDay },
        )

        override suspend fun upsertDaily(entity: DeviceDailyEntity) {
            dailyRows[entity.epochDay] = entity
            dailyWrites++
        }

        override suspend fun addHourly(
            epochDay: Long,
            hour: Int,
            rxBytes: Long,
            txBytes: Long,
        ) {
            val key = epochDay to hour
            val existing = hourlyRows[key]
            hourlyRows[key] = DeviceHourlyEntity(
                epochDay = epochDay,
                hour = hour,
                rxBytes = (existing?.rxBytes ?: 0L) + rxBytes,
                txBytes = (existing?.txBytes ?: 0L) + txBytes,
            )
        }

        override fun observeHourly(epochDay: Long): Flow<List<DeviceHourlyEntity>> = flowOf(
            hourlyRows.values.filter { it.epochDay == epochDay }.sortedBy { it.hour },
        )

        override suspend fun state(): DeviceStateEntity? = stateRow

        override suspend fun upsertState(entity: DeviceStateEntity) {
            stateRow = entity
            stateWrites++
        }
    }
}
