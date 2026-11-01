package com.maknoon.mizan.core.platform.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.maknoon.mizan.core.domain.network.NetworkSnapshot

/**
 * Reads the active connection's transport and metered flag (docs/02 §3).
 *
 * The SSID is never read: it would require location permission on Android 10+. Profiles are
 * bound to the transport plus the system's metered flag instead.
 */
class AndroidNetworkStateSource(private val context: Context) {

    fun snapshot(): NetworkSnapshot {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkStates.disconnected()
        val network = manager.activeNetwork ?: return NetworkStates.disconnected()
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return NetworkStates.disconnected()

        return NetworkStates.of(
            hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            systemMetered = manager.isActiveNetworkMetered,
            temporarilyUnmetered = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED),
            roaming = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING),
        )
    }
}
