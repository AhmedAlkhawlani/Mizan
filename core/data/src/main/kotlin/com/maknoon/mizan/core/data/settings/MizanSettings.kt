package com.maknoon.mizan.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One DataStore for the whole process; the delegate guarantees a single instance per file. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mizan_settings",
)

/**
 * Local, unencrypted-by-design settings (docs/03). Nothing here ever leaves the device, so the
 * store is a plain preferences file rather than anything synchronised.
 */
class MizanSettings(context: Context) {

    private val store: DataStore<Preferences> = context.applicationContext.settingsDataStore

    /** Whether the user asked monitoring to run; the boot receiver restarts it from here. */
    val monitoringEnabled: Flow<Boolean> = store.data
        .map { preferences -> preferences[MONITORING_ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        store.edit { preferences -> preferences[MONITORING_ENABLED] = enabled }
    }

    /**
     * The day of the month the operator's billing cycle starts on (docs/03), capped at 28 so
     * every month has it. Read outside this class through [BillingCycleCalculator].
     */
    val cycleStartDay: Flow<Int> = store.data
        .map { preferences -> preferences[CYCLE_START_DAY] ?: DEFAULT_CYCLE_START_DAY }
        .distinctUntilChanged()

    suspend fun setCycleStartDay(day: Int) {
        val normalized = BillingCycleCalculator.normalizedStartDay(day)
        store.edit { preferences -> preferences[CYCLE_START_DAY] = normalized }
    }

    /**
     * The firewall's master switch and the packages the operator chose to block (docs/04).
     * Only the package names are stored; the labels and icons are read from PackageManager, so
     * an app that is uninstalled or renamed never leaves a stale name on screen.
     */
    val dataSaverEnabled: Flow<Boolean> = store.data
        .map { preferences -> preferences[DATA_SAVER_ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun setDataSaverEnabled(enabled: Boolean) {
        store.edit { preferences -> preferences[DATA_SAVER_ENABLED] = enabled }
    }

    val blockedPackages: Flow<Set<String>> = store.data
        .map { preferences -> preferences[BLOCKED_PACKAGES] ?: emptySet() }
        .distinctUntilChanged()

    suspend fun setBlockedPackages(packages: Set<String>) {
        val normalized = packages.filterTo(LinkedHashSet()) { it.isNotBlank() }
        store.edit { preferences -> preferences[BLOCKED_PACKAGES] = normalized }
    }

    /**
     * Whether the first-run walkthrough has been completed or skipped. Absent means "not seen",
     * so an update that introduces it shows the flow once instead of hiding it from every
     * existing install.
     */
    val onboardingCompleted: Flow<Boolean> = store.data
        .map { preferences -> preferences[ONBOARDING_COMPLETED] ?: false }
        .distinctUntilChanged()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { preferences -> preferences[ONBOARDING_COMPLETED] = completed }
    }

    /**
     * Which rate the status-bar icon draws. Download is the default because it is the number
     * most people check; an unknown stored value falls back to it rather than crashing.
     */
    val statusBarSpeedMode: Flow<StatusBarSpeedMode> = store.data
        .map { preferences ->
            preferences[STATUS_BAR_SPEED_MODE]
                ?.let { stored -> StatusBarSpeedMode.entries.firstOrNull { it.name == stored } }
                ?: StatusBarSpeedMode.DOWNLOAD
        }
        .distinctUntilChanged()

    suspend fun setStatusBarSpeedMode(mode: StatusBarSpeedMode) {
        store.edit { preferences -> preferences[STATUS_BAR_SPEED_MODE] = mode.name }
    }

    private companion object {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val CYCLE_START_DAY = intPreferencesKey("cycle_start_day")
        val DATA_SAVER_ENABLED = booleanPreferencesKey("data_saver_enabled")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("data_saver_blocked_packages")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val STATUS_BAR_SPEED_MODE = stringPreferencesKey("status_bar_speed_mode")
        const val DEFAULT_CYCLE_START_DAY = BillingCycleCalculator.MIN_START_DAY
    }
}
