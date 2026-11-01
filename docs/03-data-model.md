# 03 — نموذج البيانات

## نظرة عامة

- **Room** للبيانات الزمنية والثقيلة (استهلاك، سجل، ميزانيات، أحداث).
- **DataStore (Preferences)** للإعدادات الصغيرة (لغة، ثيم، فترة التحديث، دورة الفاتورة…).
- كل شيء محلي. لا سحابة. التصدير CSV/IPST يدوي.

## الكيانات (Room Entities)

### 1) عدّاد الجهاز (صف واحد)

```kotlin
@Entity(tableName = "device_state")
data class DeviceStateEntity(
    @PrimaryKey val id: Int = 1,
    val bootEpochMillis: Long,      // System.currentTimeMillis() - SystemClock.elapsedRealtime()
    val lastTotalRx: Long,
    val lastTotalTx: Long,
    val lastMobileRx: Long,
    val lastMobileTx: Long,
    val lastSampleAt: Long,
    val todayEpochDay: Long,        // اليوم المحلي الحالي
    val cycleStartEpochDay: Long,
    val partial: Boolean
)
```

### 2) الاستهلاك اليومي للجهاز

```kotlin
@Entity(tableName = "device_daily")
data class DeviceDailyEntity(
    @PrimaryKey val epochDay: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val partial: Boolean
)
```

### 3) الاستهلاك الساعي (لرسم 24 ساعة)

```kotlin
@Entity(tableName = "device_hourly", primaryKeys = ["epochDay", "hour"])
data class DeviceHourlyEntity(
    val epochDay: Long,
    val hour: Int,
    val rxBytes: Long,
    val txBytes: Long
)
```

### 4) الاستهلاك اليومي لكل تطبيق

```kotlin
@Entity(
    tableName = "app_daily",
    primaryKeys = ["epochDay", "uid", "networkType"]
)
data class AppDailyEntity(
    val epochDay: Long,
    val uid: Int,
    val packageName: String,
    val label: String,
    val networkType: Int,       // TYPE_WIFI(1) / TYPE_MOBILE(0)
    val rxBytes: Long,
    val txBytes: Long,
    val lastSyncedAt: Long
)
```

### 5) الميزانيات

```kotlin
enum class BudgetScope { DEVICE, APP }
enum class BudgetPeriod { DAILY, MONTHLY }

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: BudgetScope,
    val targetId: String,        // "device" أو packageName
    val period: BudgetPeriod,
    val limitBytes: Long,
    val enabled: Boolean,
    val createdAt: Long
)
```

### 6) التطبيقات المحظورة (Data Saver)

```kotlin
@Entity(tableName = "blocked_apps", primaryKeys = ["packageName"])
data class BlockedAppEntity(
    val packageName: String,
    val label: String,
    val blockedAt: Long,
    val allowOnUnmetered: Boolean   // قد نسمح بالبيانات على Wi‑Fi فقط
)
```

### 7) ملفات تعريف الشبكات

```kotlin
@Entity(tableName = "network_profiles")
data class NetworkProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,             // "البيت"، "هوتسبوت"
    val metered: Boolean,
    val monthlyBudgetBytes: Long?,
    val blockedPackages: List<String>, // TypeConverter
    val createdAt: Long
)
```

### 8) أحداث التنبيه

```kotlin
@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val type: String,             // BUDGET_80, BUDGET_100, ANOMALY, SAVER_BLOCK
    val scope: String,            // device/app package
    val level: Int?,
    val acknowledged: Boolean
)
```

## DAOs الأساسية

```kotlin
@Dao
interface DeviceUsageDao {
    @Query("SELECT * FROM device_daily WHERE epochDay = :day")
    fun observeDay(day: Long): Flow<DeviceDailyEntity?>

    @Query("SELECT * FROM device_daily WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeRange(from: Long, to: Long): Flow<List<DeviceDailyEntity>>

    @Upsert suspend fun upsert(entity: DeviceDailyEntity)
    @Query("SELECT * FROM device_hourly WHERE epochDay = :day ORDER BY hour")
    fun observeHourly(day: Long): Flow<List<DeviceHourlyEntity>>
}

@Dao
interface AppUsageDao {
    @Query("""
        SELECT packageName, label, SUM(rxBytes) AS rxBytes, SUM(txBytes) AS txBytes
        FROM app_daily WHERE epochDay BETWEEN :from AND :to
        GROUP BY packageName ORDER BY (rxBytes + txBytes) DESC
    """)
    fun observeTotals(from: Long, to: Long): Flow<List<AppUsageTotal>>

    @Upsert suspend fun upsertAll(rows: List<AppDailyEntity>)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE enabled = 1")
    fun observeEnabled(): Flow<List<BudgetEntity>>
    @Upsert suspend fun upsert(entity: BudgetEntity)
    @Delete suspend fun delete(entity: BudgetEntity)
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun observeAll(): Flow<List<BlockedAppEntity>>
    @Upsert suspend fun upsert(entity: BlockedAppEntity)
    @Query("DELETE FROM blocked_apps WHERE packageName = :pkg")
    suspend fun unblock(pkg: String)
}
```

## DataStore (الإعدادات)

```kotlin
object SettingsKeys {
    val LANGUAGE = stringPreferencesKey("language")            // system|ar|en
    val THEME = stringPreferencesKey("theme")                  // system|light|dark
    val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
    val SAMPLE_INTERVAL_SECONDS = intPreferencesKey("sample_interval")
    val CYCLE_START_DAY = intPreferencesKey("cycle_start_day") // 1..28
    val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
    val ONGOING_NOTIFICATION = booleanPreferencesKey("ongoing_notification")
    val DATA_SAVER_ENABLED = booleanPreferencesKey("data_saver_enabled")
    val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
}
```

## سياسات الاحتفاظ

| البيانات | المدة | السبب |
|---|---|---|
| `device_daily` | 400 يوم | سنة + دورة |
| `device_hourly` | 60 يوم | الرسوم التفصيلية |
| `app_daily` | 180 يوم | مقارنة الاتجاهات |
| `alert_events` | 90 يوم | سجل التنبيهات |
| ملفات الفيديو/السجلات | لا شيء | لا نخزّن محتوى |

تنظيف تلقائي في `WorkManager` أسبوعياً.

## التصدير

- CSV: `epochDay, rxBytes, txBytes, mobileBytes, wifiBytes`.
- CSV ثانٍ لكل تطبيق: `epochDay, packageName, label, rxBytes, txBytes`.
- `Intent.ACTION_CREATE_DOCUMENT` لحفظ الملف دون صلاحيات تخزين.
