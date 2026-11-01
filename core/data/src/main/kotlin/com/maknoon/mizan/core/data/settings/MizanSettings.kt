package com.maknoon.mizan.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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

    private companion object {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
    }
}
