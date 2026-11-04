package com.maknoon.mizan.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.maknoon.mizan.core.data.db.MizanDatabaseProvider
import com.maknoon.mizan.core.data.network.NetworkProfilesRepository
import com.maknoon.mizan.core.domain.network.NetworkSnapshot
import com.maknoon.mizan.core.domain.network.ResolvedNetwork
import com.maknoon.mizan.core.platform.network.AndroidNetworkStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One process-wide reading of the current connection, resolved with the same 4.6 rules the VPN
 * service applies, so the Dashboard chip and the Guard screen can never disagree with the
 * firewall itself.
 *
 * The callback is transport-filtered like the service's: the tunnel becoming the default network
 * is invisible here, so registering it cannot start a re-read loop.
 */
// The retained context is the application context, which lives exactly as long as this
// process-wide holder does, so there is no activity to leak.
@SuppressLint("StaticFieldLeak")
object ResolvedNetworkHolder {

    private val _state = MutableStateFlow<ResolvedNetwork?>(null)
    val state: StateFlow<ResolvedNetwork?> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var source: AndroidNetworkStateSource? = null
    private var profiles: NetworkProfilesRepository? = null
    private var connectivity: ConnectivityManager? = null

    @Volatile
    private var lastSnapshot: NetworkSnapshot? = null

    private val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshIfChanged()

        override fun onLost(network: Network) = refreshIfChanged()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refreshIfChanged()
    }

    /** Idempotent: the first screen to need the reading owns the registration for the process. */
    fun start(context: Context) {
        if (source != null) return
        val app = context.applicationContext
        source = AndroidNetworkStateSource(app)
        profiles = NetworkProfilesRepository(MizanDatabaseProvider.networkProfileDao(app))
        connectivity = app.getSystemService(ConnectivityManager::class.java)
        connectivity?.registerNetworkCallback(request, callback)
        refresh()
    }

    /** Re-reads now, for screens returning to the foreground after a system-settings change. */
    fun refresh() {
        val snapshot = snapshotOrNull() ?: return
        lastSnapshot = snapshot
        resolve(snapshot)
    }

    private fun refreshIfChanged() {
        val snapshot = snapshotOrNull() ?: return
        if (snapshot == lastSnapshot && _state.value != null) return
        lastSnapshot = snapshot
        resolve(snapshot)
    }

    private fun resolve(snapshot: NetworkSnapshot) {
        val repository = profiles ?: return
        scope.launch { _state.value = repository.resolve(snapshot) }
    }

    private fun snapshotOrNull(): NetworkSnapshot? =
        runCatching { source?.snapshot() }.getOrNull()
}
