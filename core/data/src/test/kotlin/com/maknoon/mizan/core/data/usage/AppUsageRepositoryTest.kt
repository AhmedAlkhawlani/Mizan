package com.maknoon.mizan.core.data.usage

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.AppDailyEntity
import com.maknoon.mizan.core.data.db.AppDailyNetwork
import com.maknoon.mizan.core.data.db.AppUsageDao
import com.maknoon.mizan.core.data.db.AppUsageTotal
import com.maknoon.mizan.core.domain.usage.AppUsageBytes
import com.maknoon.mizan.core.domain.usage.InstalledApp
import com.maknoon.mizan.core.domain.usage.PerAppUsageSource
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppUsageRepositoryTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val today = LocalDate.of(2026, 3, 10)
    private val yesterday = today.minusDays(1)

    private val dao = FakeAppUsageDao()
    private val source = FakePerAppUsageSource()
    private val repository = AppUsageRepository(dao, source, zone)

    private val chrome = InstalledApp("com.android.chrome", uid = 10191, label = "Chrome")
    private val youtube = InstalledApp("com.google.android.youtube", uid = 10213, label = "YouTube")

    private fun at(hour: Int, minute: Int = 0, day: LocalDate = today): Long =
        day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun startOf(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `yesterday and today are both queried, and today stops at the sample`() = runTest {
        repository.sync(nowMillis = at(hour = 10), apps = emptyList())

        assertThat(source.queries).containsExactly(
            startOf(yesterday) to startOf(today),
            startOf(today) to at(hour = 10),
        ).inOrder()
    }

    @Test
    fun `a measured app gets one row per network with its day and identity`() = runTest {
        source.summary = mapOf(
            chrome.uid to AppUsageBytes(
                mobileRxBytes = 1_000,
                mobileTxBytes = 200,
                wifiRxBytes = 3_000,
                wifiTxBytes = 400,
            ),
        )

        val result = repository.sync(nowMillis = at(hour = 10), apps = listOf(chrome))

        assertThat(result).isEqualTo(AppSyncResult(rowsWritten = 4, accessGranted = true))
        val mobile = dao.rows.getValue(Key(today.toEpochDay(), chrome.uid, AppDailyNetwork.MOBILE))
        assertThat(mobile.packageName).isEqualTo("com.android.chrome")
        assertThat(mobile.label).isEqualTo("Chrome")
        assertThat(mobile.rxBytes).isEqualTo(1_000)
        assertThat(mobile.txBytes).isEqualTo(200)
        assertThat(mobile.lastSyncedAt).isEqualTo(at(hour = 10))

        val wifi = dao.rows.getValue(Key(today.toEpochDay(), chrome.uid, AppDailyNetwork.WIFI))
        assertThat(wifi.rxBytes).isEqualTo(3_000)
        assertThat(wifi.txBytes).isEqualTo(400)
    }

    @Test
    fun `traffic that did not move on a network gets no row`() = runTest {
        source.summary = mapOf(
            youtube.uid to AppUsageBytes(
                mobileRxBytes = 0,
                mobileTxBytes = 0,
                wifiRxBytes = 5_000,
                wifiTxBytes = 500,
            ),
        )

        repository.sync(nowMillis = at(hour = 10), apps = listOf(youtube))

        assertThat(dao.rows.keys).containsExactly(
            Key(yesterday.toEpochDay(), youtube.uid, AppDailyNetwork.WIFI),
            Key(today.toEpochDay(), youtube.uid, AppDailyNetwork.WIFI),
        )
    }

    @Test
    fun `a channel with one unknown direction writes no row`() = runTest {
        source.summary = mapOf(
            chrome.uid to AppUsageBytes(
                mobileRxBytes = 1_000,
                mobileTxBytes = null,
                wifiRxBytes = null,
                wifiTxBytes = null,
            ),
        )

        val result = repository.sync(nowMillis = at(hour = 10), apps = listOf(chrome))

        assertThat(result.rowsWritten).isEqualTo(0)
        assertThat(dao.rows).isEmpty()
    }

    @Test
    fun `an app the platform did not mention is skipped`() = runTest {
        source.summary = mapOf(
            chrome.uid to AppUsageBytes(1, 1, 0, 0),
        )

        repository.sync(nowMillis = at(hour = 10), apps = listOf(chrome, youtube))

        assertThat(dao.rows.keys.map { it.uid }.toSet()).containsExactly(chrome.uid)
    }

    @Test
    fun `packages sharing a uid are recorded once`() = runTest {
        val sharedA = InstalledApp("com.example.a", uid = 10500, label = "A")
        val sharedB = InstalledApp("com.example.b", uid = 10500, label = "B")
        source.summary = mapOf(
            sharedA.uid to AppUsageBytes(10, 1, 0, 0),
        )

        repository.sync(nowMillis = at(hour = 10), apps = listOf(sharedA, sharedB))

        // One row for each queried day, never one per package of the shared UID.
        assertThat(dao.rows).hasSize(2)
        assertThat(dao.rows.values.map { it.packageName }.toSet())
            .containsExactly("com.example.a")
    }

    @Test
    fun `a revoked grant writes nothing and reports it`() = runTest {
        source.summary = null

        val result = repository.sync(nowMillis = at(hour = 10), apps = listOf(chrome))

        assertThat(result).isEqualTo(AppSyncResult(rowsWritten = 0, accessGranted = false))
        assertThat(dao.rows).isEmpty()
    }

    @Test
    fun `a later sync replaces the day's rows instead of adding to them`() = runTest {
        source.summary = mapOf(chrome.uid to AppUsageBytes(1_000, 100, 0, 0))
        repository.sync(nowMillis = at(hour = 9), apps = listOf(chrome))

        source.summary = mapOf(chrome.uid to AppUsageBytes(2_000, 250, 0, 0))
        repository.sync(nowMillis = at(hour = 10), apps = listOf(chrome))

        val mobile = dao.rows.getValue(Key(today.toEpochDay(), chrome.uid, AppDailyNetwork.MOBILE))
        assertThat(mobile.rxBytes).isEqualTo(2_000)
        assertThat(mobile.txBytes).isEqualTo(250)
    }

    private data class Key(val epochDay: Long, val uid: Int, val networkType: Int)

    private class FakeAppUsageDao : AppUsageDao {
        val rows = linkedMapOf<Key, AppDailyEntity>()

        override suspend fun upsertAll(rows: List<AppDailyEntity>) {
            rows.forEach { row ->
                this.rows[Key(row.epochDay, row.uid, row.networkType)] = row
            }
        }

        override fun observeTotals(
            fromEpochDay: Long,
            toEpochDay: Long,
        ): Flow<List<AppUsageTotal>> = flowOf(emptyList())
    }

    private class FakePerAppUsageSource : PerAppUsageSource {
        val queries = mutableListOf<Pair<Long, Long>>()
        var summary: Map<Int, AppUsageBytes>? = emptyMap()

        override fun usageForUid(uid: Int, fromMillis: Long, toMillis: Long): AppUsageBytes? =
            error("the sync must use the bulk summary")

        override fun usageByUid(fromMillis: Long, toMillis: Long): Map<Int, AppUsageBytes>? {
            queries += fromMillis to toMillis
            return summary
        }
    }
}
