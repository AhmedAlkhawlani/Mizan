package com.maknoon.mizan.ui.saver

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.data.network.NetworkProfilesRepository
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.domain.network.NetworkSnapshot
import com.maknoon.mizan.core.domain.network.ResolvedNetwork
import com.maknoon.mizan.core.domain.saver.BlockedTrafficSnapshot
import com.maknoon.mizan.core.platform.network.AndroidNetworkStateSource
import com.maknoon.mizan.core.platform.usage.AndroidInstalledAppsSource
import com.maknoon.mizan.saver.SaverControl
import com.maknoon.mizan.saver.SaverFailure
import com.maknoon.mizan.saver.SaverStateHolder
import com.maknoon.mizan.saver.SaverTrafficHolder
import java.util.LinkedHashSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One selectable app, with the label the package manager reported for the current locale. */
data class SaverApp(
    val packageName: String,
    val label: String,
)

data class SaverUiState(
    val enabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val apps: List<SaverApp> = emptyList(),
    val appsLoading: Boolean = true,
    val serviceRunning: Boolean = false,
    val active: Boolean = false,
    val activeBlockedCount: Int = 0,
    val failure: SaverFailure? = null,
    val unmetered: Boolean = false,
    val network: ResolvedNetwork? = null,
    val blockedTraffic: BlockedTrafficSnapshot = BlockedTrafficSnapshot(),
)

/**
 * Connects the Guard screen to the persisted blocklist and the VPN service.
 *
 * The service is the only component that decides whether a tunnel exists; this view model just
 * writes the selection and asks [SaverControl] to start or stop. A failed start is reported back
 * through [SaverStateHolder], which also turns the switch off.
 *
 * The current connection is resolved here with the same 4.6 rules the service uses, so the
 * status screen can explain what the firewall would do even before it is switched on. The
 * callback is transport-filtered like the service's, so the tunnel becoming the default network
 * cannot trigger a re-read loop.
 */
class SaverViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val settings = MizanSettings(app)
    private val installed = MutableStateFlow(InstalledApps())

    private val networkSource = AndroidNetworkStateSource(app)
    private val networkProfiles = NetworkProfilesRepository(
        MizanDatabaseProvider.networkProfileDao(app),
    )
    private val network = MutableStateFlow<ResolvedNetwork?>(null)
    private val connectivityManager = app.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var lastSnapshot: NetworkSnapshot? = null

    private val networkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkIfChanged()

        override fun onLost(network: Network) = refreshNetworkIfChanged()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refreshNetworkIfChanged()
    }

    // Read-then-write of a String set is not atomic; every selection change shares one lock.
    private val writeMutex = Mutex()
    private var applyJob: Job? = null

    private val networkAndTraffic = combine(
        network,
        SaverTrafficHolder.snapshot,
    ) { resolved, traffic -> resolved to traffic }

    val uiState: StateFlow<SaverUiState> = combine(
        settings.dataSaverEnabled,
        settings.blockedPackages,
        installed,
        SaverStateHolder.state,
        networkAndTraffic,
    ) { enabled, blocked, appsState, runtime, netAndTraffic ->
        val (resolved, traffic) = netAndTraffic
        val installedNames = appsState.apps.mapTo(HashSet()) { it.packageName }
        SaverUiState(
            enabled = enabled,
            blockedPackages = blocked.filterTo(LinkedHashSet()) { it in installedNames },
            apps = appsState.apps,
            appsLoading = appsState.loading,
            serviceRunning = runtime.serviceRunning,
            active = runtime.active,
            activeBlockedCount = runtime.blockedCount,
            failure = runtime.failure,
            unmetered = runtime.unmetered,
            network = resolved,
            blockedTraffic = traffic,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SaverUiState(),
    )

    init {
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
        refreshNetwork()

        viewModelScope.launch {
            val self = app.packageName
            val apps = withContext(Dispatchers.IO) {
                AndroidInstalledAppsSource(app)
                    .appsWithInternetAccess()
                    .asSequence()
                    .filter { it.packageName != self }
                    .map { SaverApp(packageName = it.packageName, label = it.label) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                    .toList()
            }
            installed.value = InstalledApps(loading = false, apps = apps)
        }
    }

    override fun onCleared() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    /** Re-reads the connection when the screen resumes, in case the callback was too early. */
    fun refreshNetwork() {
        val snapshot = snapshotOrNull() ?: return
        lastSnapshot = snapshot
        viewModelScope.launch {
            network.value = networkProfiles.resolve(snapshot)
        }
    }

    private fun refreshNetworkIfChanged() {
        val snapshot = snapshotOrNull() ?: return
        if (snapshot == lastSnapshot && network.value != null) return
        lastSnapshot = snapshot
        viewModelScope.launch {
            network.value = networkProfiles.resolve(snapshot)
        }
    }

    private fun snapshotOrNull(): NetworkSnapshot? =
        runCatching { networkSource.snapshot() }.getOrNull()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            applyJob?.cancel()
            if (!enabled) {
                settings.setDataSaverEnabled(false)
                SaverControl.stop(app)
                return@launch
            }
            val apps = installed.value
            if (apps.loading) return@launch
            val installedNames = apps.apps.mapTo(HashSet()) { it.packageName }
            val runnable = settings.blockedPackages.first()
                .filterTo(LinkedHashSet()) { it in installedNames }
            if (runnable.isEmpty()) return@launch
            // Persist first so the switch stays on while the tunnel is being established.
            settings.setBlockedPackages(runnable)
            settings.setDataSaverEnabled(true)
            SaverControl.start(app)
        }
    }

    fun setAppBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            if (installed.value.loading) return@launch
            writeMutex.withLock {
                val installedNames = installed.value.apps.mapTo(HashSet()) { it.packageName }
                if (packageName !in installedNames) return@withLock
                val current = settings.blockedPackages.first()
                val next = if (blocked) current + packageName else current - packageName
                // Dropping names that are no longer installed keeps the stored list runnable.
                settings.setBlockedPackages(
                    next.filterTo(LinkedHashSet()) { it in installedNames },
                )
            }
            scheduleRuntimeApply()
        }
    }

    /**
     * A running tunnel cannot change its allowlist, so every selection change rebuilds it.
     * Debouncing turns a burst of picker taps into one brief re-establishment.
     */
    private fun scheduleRuntimeApply() {
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            delay(APPLY_DEBOUNCE_MILLIS)
            if (!settings.dataSaverEnabled.first()) return@launch
            val installedNames = installed.value.apps.mapTo(HashSet()) { it.packageName }
            val runnable = settings.blockedPackages.first()
                .filterTo(LinkedHashSet()) { it in installedNames }
            if (runnable.isEmpty()) {
                settings.setDataSaverEnabled(false)
                SaverControl.stop(app)
            } else {
                SaverControl.start(app)
            }
        }
    }

    private data class InstalledApps(
        val loading: Boolean = true,
        val apps: List<SaverApp> = emptyList(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val APPLY_DEBOUNCE_MILLIS = 400L
    }
}
