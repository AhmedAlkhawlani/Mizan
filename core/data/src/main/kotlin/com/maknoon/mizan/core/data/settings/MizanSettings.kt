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
     * Whether the firewall applies on every connection, not only on metered ones.
     *
     * On by default, because a blocklist is the operator's instruction: the tunnel used to stand
     * down on any unmetered network, so a blocked app opened normally on home Wi-Fi and the
     * screen looked broken. Turning it off restores the older data-saving rule, where the
     * blocked apps are allowed through while the connection is unmetered.
     */
    val protectEveryNetwork: Flow<Boolean> = store.data
        .map { preferences -> preferences[PROTECT_EVERY_NETWORK] ?: true }
        .distinctUntilChanged()

    suspend fun setProtectEveryNetwork(enabled: Boolean) {
        store.edit { preferences -> preferences[PROTECT_EVERY_NETWORK] = enabled }
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

    /**
     * Whether the status-bar icon may drop to the bytes unit. Off by default: a number that
     * changes every second over a few hundred bytes looks like an alarm for background chatter,
     * so the bar stays static until the rate is worth a kilobyte.
     */
    val statusBarShowBytes: Flow<Boolean> = store.data
        .map { preferences -> preferences[STATUS_BAR_SHOW_BYTES] ?: false }
        .distinctUntilChanged()

    suspend fun setStatusBarShowBytes(showBytes: Boolean) {
        store.edit { preferences -> preferences[STATUS_BAR_SHOW_BYTES] = showBytes }
    }

    /**
     * Which template the badge draws. Raised is the default because it gives the digits the full
     * height of the slot; an unknown stored value falls back to it rather than crashing.
     */
    val statusBarBadgeStyle: Flow<SpeedBadgeStyle> = store.data
        .map { preferences ->
            preferences[STATUS_BAR_BADGE_STYLE]
                ?.let { stored -> SpeedBadgeStyle.entries.firstOrNull { it.name == stored } }
                ?: SpeedBadgeStyle.RAISED
        }
        .distinctUntilChanged()

    suspend fun setStatusBarBadgeStyle(style: SpeedBadgeStyle) {
        store.edit { preferences -> preferences[STATUS_BAR_BADGE_STYLE] = style.name }
    }

    /**
     * Whether the badge glyphs are drawn heavy. Bold is the default because a regular weight
     * thins into the system icons beside it at 24dp; the light option is for whoever finds the
     * badge louder than the rest of the bar.
     */
    val statusBarBoldGlyphs: Flow<Boolean> = store.data
        .map { preferences -> preferences[STATUS_BAR_BOLD_GLYPHS] ?: true }
        .distinctUntilChanged()

    suspend fun setStatusBarBoldGlyphs(bold: Boolean) {
        store.edit { preferences -> preferences[STATUS_BAR_BOLD_GLYPHS] = bold }
    }

    private companion object {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val CYCLE_START_DAY = intPreferencesKey("cycle_start_day")
        val DATA_SAVER_ENABLED = booleanPreferencesKey("data_saver_enabled")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("data_saver_blocked_packages")
        val PROTECT_EVERY_NETWORK = booleanPreferencesKey("data_saver_protect_every_network")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val STATUS_BAR_SPEED_MODE = stringPreferencesKey("status_bar_speed_mode")
        val STATUS_BAR_SHOW_BYTES = booleanPreferencesKey("status_bar_show_bytes")
        val STATUS_BAR_BADGE_STYLE = stringPreferencesKey("status_bar_badge_style")
        val STATUS_BAR_BOLD_GLYPHS = booleanPreferencesKey("status_bar_bold_glyphs")
        const val DEFAULT_CYCLE_START_DAY = BillingCycleCalculator.MIN_START_DAY
    }
}
