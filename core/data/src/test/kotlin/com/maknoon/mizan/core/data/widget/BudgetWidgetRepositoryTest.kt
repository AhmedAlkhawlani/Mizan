package com.maknoon.mizan.core.data.widget

import com.google.common.truth.Truth.assertThat
import com.maknoon.mizan.core.data.db.AppDailyRow
import com.maknoon.mizan.core.data.db.AppDailyEntity
import com.maknoon.mizan.core.data.db.AppUsageDao
import com.maknoon.mizan.core.data.db.AppUsageTotal
import com.maknoon.mizan.core.data.db.BudgetDao
import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.data.db.DeviceDailyEntity
import com.maknoon.mizan.core.data.db.DeviceHourlyEntity
import com.maknoon.mizan.core.data.db.DeviceStateEntity
import com.maknoon.mizan.core.data.db.DeviceUsageDao
import com.maknoon.mizan.core.data.db.PackageBytes
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BudgetWidgetRepositoryTest {

    private val budgetDao = FakeBudgetDao()
    private val deviceUsageDao = FakeDeviceUsageDao()
    private val appUsageDao = FakeAppUsageDao()
    private val repository = BudgetWidgetRepository(budgetDao, deviceUsageDao, appUsageDao)

    @Test
    fun `load sums today and exposes the enabled daily cap and top app`() = runTest {
        budgetDao.daily = budget(limitBytes = 2_000_000_000, enabled = true)
        deviceUsageDao.daily = deviceDay(rxBytes = 800, txBytes = 200)
        appUsageDao.top = appTotal(rxBytes = 300, txBytes = 100, label = "Chrome")

        val snapshot = repository.load(TODAY)

        assertThat(snapshot.storedTodayBytes).isEqualTo(1_000L)
        assertThat(snapshot.dailyLimitBytes).isEqualTo(2_000_000_000L)
        assertThat(snapshot.topApp).isEqualTo(WidgetTopApp("com.android.chrome", "Chrome", 400L))
    }

    @Test
    fun `a disabled daily cap is not a limit`() = runTest {
        budgetDao.daily = budget(limitBytes = 2_000_000_000, enabled = false)

        assertThat(repository.load(TODAY).dailyLimitBytes).isNull()
    }

    @Test
    fun `a zero daily cap is not a limit`() = runTest {
        budgetDao.daily = budget(limitBytes = 0, enabled = true)

        assertThat(repository.load(TODAY).dailyLimitBytes).isNull()
    }

    @Test
    fun `a monthly-only cap never feeds the daily ring`() = runTest {
        budgetDao.monthly = budget(limitBytes = 6_000_000_000, enabled = true, period = BudgetPeriod.MONTHLY)

        assertThat(repository.load(TODAY).dailyLimitBytes).isNull()
    }

    @Test
    fun `a missing device row leaves today's bytes unknown`() = runTest {
        deviceUsageDao.daily = null

        assertThat(repository.load(TODAY).storedTodayBytes).isNull()
    }

    @Test
    fun `a missing app row leaves the top app unknown`() = runTest {
        appUsageDao.top = null

        assertThat(repository.load(TODAY).topApp).isNull()
    }

    @Test
    fun `the requested day reaches both usage queries`() = runTest {
        repository.load(20_759)

        assertThat(deviceUsageDao.requestedDays).containsExactly(20_759L)
        assertThat(appUsageDao.requestedDays).containsExactly(20_759L)
    }

    @Test
    fun `a missing app label is kept null for the widget to fall back`() = runTest {
        appUsageDao.top = appTotal(rxBytes = 10, txBytes = 0, label = null)

        assertThat(repository.load(TODAY).topApp?.label).isNull()
    }

    private fun budget(
        limitBytes: Long,
        enabled: Boolean,
        period: BudgetPeriod = BudgetPeriod.DAILY,
    ) = BudgetEntity(
        id = 1,
        scope = BudgetScope.DEVICE,
        targetId = "device",
        period = period,
        limitBytes = limitBytes,
        enabled = enabled,
        createdAt = 0,
    )

    private fun deviceDay(rxBytes: Long, txBytes: Long) = DeviceDailyEntity(
        epochDay = TODAY,
        rxBytes = rxBytes,
        txBytes = txBytes,
        partial = false,
        updatedAtMillis = 0,
    )

    private fun appTotal(rxBytes: Long, txBytes: Long, label: String?) = AppUsageTotal(
        packageName = "com.android.chrome",
        label = label,
        rxBytes = rxBytes,
        txBytes = txBytes,
        lastSyncedAt = 0,
    )

    private companion object {
        const val TODAY = 20_759L
    }
}

private class FakeBudgetDao : BudgetDao {
    var daily: BudgetEntity? = null
    var monthly: BudgetEntity? = null

    override fun observeDevice(): Flow<List<BudgetEntity>> = flowOf(emptyList())

    override suspend fun deviceBudget(period: BudgetPeriod): BudgetEntity? =
        if (period == BudgetPeriod.DAILY) daily else monthly

    override fun observeAppBudgets(): Flow<List<BudgetEntity>> = flowOf(emptyList())

    override suspend fun appBudget(packageName: String, period: BudgetPeriod): BudgetEntity? =
        error("not used")

    override suspend fun enabledBudgets(): List<BudgetEntity> = error("not used")

    override suspend fun upsert(entity: BudgetEntity) = error("not used")
}

private class FakeDeviceUsageDao : DeviceUsageDao {
    var daily: DeviceDailyEntity? = null
    val requestedDays = mutableListOf<Long>()

    override suspend fun daily(epochDay: Long): DeviceDailyEntity? {
        requestedDays += epochDay
        return daily
    }

    override fun observeDailyRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<DeviceDailyEntity>> =
        error("not used")

    override suspend fun allDaily(): List<DeviceDailyEntity> = error("not used")

    override suspend fun totalBytes(fromEpochDay: Long, toEpochDay: Long): Long = error("not used")

    override suspend fun upsertDaily(entity: DeviceDailyEntity) = error("not used")

    override suspend fun openHourBucket(epochDay: Long, hour: Int) = error("not used")

    override suspend fun accumulateHour(epochDay: Long, hour: Int, rxBytes: Long, txBytes: Long) =
        error("not used")

    override fun observeHourly(epochDay: Long): Flow<List<DeviceHourlyEntity>> = error("not used")

    override suspend fun state(): DeviceStateEntity? = error("not used")

    override suspend fun upsertState(entity: DeviceStateEntity) = error("not used")
}

private class FakeAppUsageDao : AppUsageDao {
    var top: AppUsageTotal? = null
    val requestedDays = mutableListOf<Long>()

    override suspend fun upsertAll(rows: List<AppDailyEntity>) = error("not used")

    override fun observeTotals(fromEpochDay: Long, toEpochDay: Long): Flow<List<AppUsageTotal>> =
        error("not used")

    override fun observePackageDaily(
        packageName: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<AppDailyRow>> = error("not used")

    override suspend fun totalsByPackage(fromEpochDay: Long, toEpochDay: Long): List<PackageBytes> =
        error("not used")

    override suspend fun topForDay(epochDay: Long): AppUsageTotal? {
        requestedDays += epochDay
        return top
    }
}
