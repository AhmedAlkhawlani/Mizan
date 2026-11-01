package com.maknoon.mizan.core.data.widget

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What one speed-widget instance shows; every widget may choose differently. */
data class SpeedWidgetConfig(
    val showToday: Boolean = true,
    val showUpload: Boolean = true,
)

/** What one budget-widget instance shows; the ring itself is never optional. */
data class BudgetWidgetConfig(
    val showTopApp: Boolean = true,
    val showRemaining: Boolean = false,
)

private val Context.widgetConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mizan_widgets",
)

/**
 * Per-instance widget choices, keyed by the launcher's `appWidgetId`.
 *
 * The values live outside Room because they belong to a launcher instance, not to usage data:
 * removing the widget deletes its keys, and two widgets of the same kind can differ. Missing
 * keys mean the defaults of the config classes, which is what a freshly added widget gets if
 * the configuration activity never wrote anything.
 */
class WidgetConfigStore(context: Context) {

    private val store: DataStore<Preferences> = context.applicationContext.widgetConfigDataStore

    suspend fun speed(appWidgetId: Int): SpeedWidgetConfig {
        val preferences = store.data.first()
        val defaults = SpeedWidgetConfig()
        return SpeedWidgetConfig(
            showToday = preferences[speedKey(appWidgetId, SHOW_TODAY)] ?: defaults.showToday,
            showUpload = preferences[speedKey(appWidgetId, SHOW_UPLOAD)] ?: defaults.showUpload,
        )
    }

    suspend fun saveSpeed(appWidgetId: Int, config: SpeedWidgetConfig) {
        store.edit { preferences ->
            preferences[speedKey(appWidgetId, SHOW_TODAY)] = config.showToday
            preferences[speedKey(appWidgetId, SHOW_UPLOAD)] = config.showUpload
        }
    }

    suspend fun budget(appWidgetId: Int): BudgetWidgetConfig {
        val preferences = store.data.first()
        val defaults = BudgetWidgetConfig()
        return BudgetWidgetConfig(
            showTopApp = preferences[budgetKey(appWidgetId, SHOW_TOP_APP)] ?: defaults.showTopApp,
            showRemaining = preferences[budgetKey(appWidgetId, SHOW_REMAINING)]
                ?: defaults.showRemaining,
        )
    }

    suspend fun saveBudget(appWidgetId: Int, config: BudgetWidgetConfig) {
        store.edit { preferences ->
            preferences[budgetKey(appWidgetId, SHOW_TOP_APP)] = config.showTopApp
            preferences[budgetKey(appWidgetId, SHOW_REMAINING)] = config.showRemaining
        }
    }

    suspend fun delete(appWidgetIds: List<Int>) {
        if (appWidgetIds.isEmpty()) return
        store.edit { preferences ->
            appWidgetIds.forEach { id ->
                preferences.remove(speedKey(id, SHOW_TODAY))
                preferences.remove(speedKey(id, SHOW_UPLOAD))
                preferences.remove(budgetKey(id, SHOW_TOP_APP))
                preferences.remove(budgetKey(id, SHOW_REMAINING))
            }
        }
    }

    companion object {
        private const val SHOW_TODAY = "show_today"
        private const val SHOW_UPLOAD = "show_upload"
        private const val SHOW_TOP_APP = "show_top_app"
        private const val SHOW_REMAINING = "show_remaining"
        private const val TAG = "MizanWidgetConfig"

        private fun speedKey(appWidgetId: Int, name: String) =
            booleanPreferencesKey("speed_${appWidgetId}_$name")

        private fun budgetKey(appWidgetId: Int, name: String) =
            booleanPreferencesKey("budget_${appWidgetId}_$name")

        /**
         * Best-effort cleanup from `onDeleted`, which is synchronous and must return quickly.
         * A process death before the write finishes only leaves unreachable keys behind.
         */
        fun deleteInBackground(context: Context, appWidgetIds: IntArray) {
            if (appWidgetIds.isEmpty()) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    WidgetConfigStore(context).delete(appWidgetIds.toList())
                } catch (e: Exception) {
                    Log.w(TAG, "widget config cleanup failed", e)
                }
            }
        }
    }
}
