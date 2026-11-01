# 09 — سقالة Gradle والكود الأولي

> هذه سقالة مرجعية. بعد `Sync` في Android Studio، صحّح الإصدارات إن لزم ثم ابدأ المرحلة 0.
> القاعدة: لا تنسخ كل شيء دفعة واحدة؛ أنشئ الملفات بالترتيب وشغّل البناء بعد كل خطوة.

## 1) إنشاء المشروع

```bash
cd ~/Desktop/Mizan
# إن لم يكن Gradle مثبتاً، استخدم Gradle wrapper من Android Studio أو:
# gradle wrapper --gradle-version 8.9
```

## 2) `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Mizan"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:platform")
include(":core:designsystem")
```

## 3) `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
composeBom = "2024.10.01"
coreKtx = "1.15.0"
lifecycle = "2.8.6"
activityCompose = "1.9.3"
navigation = "2.8.3"
room = "2.6.1"
datastore = "1.1.1"
work = "2.9.1"
hilt = "2.52"
hiltNavigation = "1.2.0"
coroutines = "1.9.0"
glance = "1.1.1"
junit = "4.13.2"
truth = "1.4.4"
turbine = "1.2.0"
robolectric = "4.13"
mockk = "1.13.13"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-datastore = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-work = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-glance = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigation" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

## 4) `build.gradle.kts` (root)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

## 5) `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.maknoon.mizan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maknoon.mizan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.platform)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

## 6) وحدات `core`

**`core/domain/build.gradle.kts`**
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

**`core/platform/build.gradle.kts`**
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.maknoon.mizan.core.platform"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.coroutines.android)
}
```

`core/data` شبيه بـ platform مع Room + DataStore + WorkManager + Hilt.
`core/designsystem` شبيه مع Compose.

## 7) المانيفست الأساسي

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />

    <application
        android:name=".MizanApplication"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Mizan">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.MonitoringService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="continuous_network_usage_monitoring" />
        </service>

        <service
            android:name=".tile.MizanTileService"
            android:exported="true"
            android:icon="@drawable/ic_tile"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>

        <receiver
            android:name=".boot.BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

## 8) منطق أساسي (نماذج)

**`ByteFormatter.kt`**
```kotlin
package com.maknoon.mizan.core.domain.format

import kotlin.math.log10
import kotlin.math.pow

object ByteFormatter {
    private val units = listOf("B", "KB", "MB", "GB", "TB", "PB")

    fun speed(bytesPerSecond: Double): String {
        val v = bytesPerSecond.coerceAtLeast(0.0)
        if (v < 1) return "0 B/s"
        if (v < 1000) return "%.0f B/s".format(v)
        val e = (log10(v) / 3).toInt().coerceIn(0, units.lastIndex)
        val scaled = v / 1000.0.pow(e)
        return if (scaled < 10) "%.1f %s/s".format(scaled, units[e])
        else "%.0f %s/s".format(scaled, units[e])
    }

    fun data(bytes: Long): String {
        val v = bytes.coerceAtLeast(0).toDouble()
        if (v < 1000) return "%.0f B".format(v)
        val e = (log10(v) / 3).toInt().coerceIn(0, units.lastIndex)
        val scaled = v / 1000.0.pow(e)
        return when (e) {
            1 -> "%.0f KB".format(scaled)
            2 -> "%.1f MB".format(scaled)
            else -> "%.2f %s".format(scaled, units[e])
        }
    }
}
```

**`TrafficAccountant.kt` (هيكل)**
```kotlin
package com.maknoon.mizan.core.domain.accounting

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DeviceSnapshot(
    val rx: Long,
    val tx: Long,
    val bootEpochMillis: Long,
    val atMillis: Long
)

data class DailyUsage(
    val epochDay: Long,
    val rx: Long,
    val tx: Long,
    val downBps: Double,
    val upBps: Double,
    val partial: Boolean
) {
    val total: Long get() = rx + tx
}

data class UsageState(
    val epochDay: Long,
    val bootEpochMillis: Long,
    val lastRx: Long,
    val lastTx: Long,
    val todayRx: Long,
    val todayTx: Long,
    val updatedAt: Long,
    val partial: Boolean,
    val startedAt: Long
)

interface UsageStateStore {
    fun load(): UsageState?
    fun save(state: UsageState)
}

class TrafficAccountant(
    private val store: UsageStateStore,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private var state: UsageState? = store.load()

    fun record(snapshot: DeviceSnapshot): DailyUsage {
        val day = LocalDate.ofInstant(Instant.ofEpochMilli(snapshot.atMillis), zone).toEpochDay()
        val start = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val bootedToday = snapshot.bootEpochMillis >= start

        var current = state
        if (current == null) {
            val partial = !bootedToday
            current = UsageState(
                epochDay = day,
                bootEpochMillis = snapshot.bootEpochMillis,
                lastRx = snapshot.rx,
                lastTx = snapshot.tx,
                todayRx = if (bootedToday) snapshot.rx else 0,
                todayTx = if (bootedToday) snapshot.tx else 0,
                updatedAt = snapshot.atMillis,
                partial = partial,
                startedAt = if (partial) snapshot.atMillis else start
            )
            state = current
            store.save(current)
            return current.toUsage(0.0, 0.0)
        }

        if (current.epochDay != day) {
            current = current.copy(
                epochDay = day,
                bootEpochMillis = snapshot.bootEpochMillis,
                lastRx = snapshot.rx,
                lastTx = snapshot.tx,
                todayRx = if (bootedToday) snapshot.rx else 0,
                todayTx = if (bootedToday) snapshot.tx else 0,
                updatedAt = snapshot.atMillis,
                partial = !bootedToday,
                startedAt = if (bootedToday) start else snapshot.atMillis
            )
            state = current
            store.save(current)
            return current.toUsage(0.0, 0.0)
        }

        if (current.bootEpochMillis != snapshot.bootEpochMillis) {
            current = current.copy(
                todayRx = current.todayRx + if (bootedToday) snapshot.rx else 0,
                todayTx = current.todayTx + if (bootedToday) snapshot.tx else 0,
                bootEpochMillis = snapshot.bootEpochMillis,
                lastRx = snapshot.rx,
                lastTx = snapshot.tx,
                updatedAt = snapshot.atMillis
            )
            state = current
            store.save(current)
            return current.toUsage(0.0, 0.0)
        }

        val dRx = (snapshot.rx - current.lastRx).coerceAtLeast(0)
        val dTx = (snapshot.tx - current.lastTx).coerceAtLeast(0)
        val elapsed = (snapshot.atMillis - current.updatedAt) / 1000.0
        current = current.copy(
            todayRx = current.todayRx + dRx,
            todayTx = current.todayTx + dTx,
            lastRx = snapshot.rx,
            lastTx = snapshot.tx,
            updatedAt = snapshot.atMillis
        )
        state = current
        store.save(current)

        val down = if (elapsed in 0.2..10.0) dRx / elapsed else 0.0
        val up = if (elapsed in 0.2..10.0) dTx / elapsed else 0.0
        return current.toUsage(down, up)
    }

    private fun UsageState.toUsage(down: Double, up: Double) = DailyUsage(
        epochDay = epochDay,
        rx = todayRx,
        tx = todayTx,
        downBps = down,
        upBps = up,
        partial = partial
    )
}
```

**`BudgetPolicy.kt`**
```kotlin
package com.maknoon.mizan.core.domain.budget

enum class BudgetLevel { WARNING, EXCEEDED }

class BudgetPolicy(private val sent: MutableSet<Int> = mutableSetOf()) {
    fun evaluate(usedBytes: Long, limitBytes: Long): BudgetLevel? {
        if (limitBytes <= 0) return null
        val ratio = usedBytes.toDouble() / limitBytes
        if (ratio >= 1.0 && sent.add(100)) return BudgetLevel.EXCEEDED
        if (ratio >= 0.8 && sent.add(80)) return BudgetLevel.WARNING
        return null
    }

    fun reset() = sent.clear()
}
```

**اختبار نموذجي**
```kotlin
class ByteFormatterTest {
    @Test fun `uses decimal units`() {
        assertThat(ByteFormatter.data(1000)).isEqualTo("1 KB")
        assertThat(ByteFormatter.data(2_450_000_000)).isEqualTo("2.45 GB")
    }
}
```

## 9) `TrafficStatsSource.kt`

```kotlin
package com.maknoon.mizan.core.platform

import android.net.TrafficStats
import android.os.SystemClock

data class TrafficSnapshot(
    val rx: Long,
    val tx: Long,
    val bootEpochMillis: Long,
    val atMillis: Long
)

interface TrafficSource { fun snapshot(): TrafficSnapshot }

class AndroidTrafficSource : TrafficSource {
    override fun snapshot(): TrafficSnapshot {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        return TrafficSnapshot(
            rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0),
            tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0),
            bootEpochMillis = now - elapsed,
            atMillis = now
        )
    }
}
```

## 10) CI

```yaml
name: Android CI
on:
  push:
    branches: [main]
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew testDebugUnitTest lintDebug assembleDebug
```

## 11) أوامر مفيدة

```bash
./gradlew :core:domain:test
./gradlew :app:assembleDebug
./gradlew lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.maknoon.mizan/.MainActivity
```

> **ملاحظة:** إصدارات AGP/Kotlin أعلاه نقطة بداية. إن اختلفت مع SDK 36 أو Android Studio،
> فحدّثها في `libs.versions.toml` وشغّل `./gradlew --refresh-dependencies`.
