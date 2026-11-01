package com.maknoon.mizan.core.platform.network

import com.maknoon.mizan.core.domain.network.NetworkSnapshot
import com.maknoon.mizan.core.domain.network.NetworkTransport

/**
 * Turns the platform's capability booleans into a [NetworkSnapshot].
 *
 * Kept separate from [AndroidNetworkStateSource] so the transport priority and the flag
 * pass-through are testable on the JVM, where `ConnectivityManager` does not exist.
 */
internal object NetworkStates {

    fun disconnected(): NetworkSnapshot = NetworkSnapshot(
        connected = false,
        transport = NetworkTransport.OTHER,
        systemMetered = false,
    )

    /**
     * Wi-Fi wins over cellular because a phone can briefly hold both capabilities; the active
     * network the user is spending on is the Wi-Fi one in that case.
     */
    fun of(
        hasWifi: Boolean,
        hasCellular: Boolean,
        hasEthernet: Boolean,
        hasVpn: Boolean,
        systemMetered: Boolean,
        temporarilyUnmetered: Boolean,
        roaming: Boolean,
    ): NetworkSnapshot {
        val transport = when {
            hasWifi -> NetworkTransport.WIFI
            hasCellular -> NetworkTransport.CELLULAR
            hasEthernet -> NetworkTransport.ETHERNET
            hasVpn -> NetworkTransport.VPN
            else -> NetworkTransport.OTHER
        }
        return NetworkSnapshot(
            connected = true,
            transport = transport,
            systemMetered = systemMetered,
            temporarilyUnmetered = temporarilyUnmetered,
            roaming = roaming,
        )
    }
}
