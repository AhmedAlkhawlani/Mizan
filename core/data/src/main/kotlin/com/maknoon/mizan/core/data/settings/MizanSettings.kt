package com.maknoon.mizan.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    private companion object {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val CYCLE_START_DAY = intPreferencesKey("cycle_start_day")
        const val DEFAULT_CYCLE_START_DAY = BillingCycleCalculator.MIN_START_DAY
    }
}
