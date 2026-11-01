package com.maknoon.mizan.core.domain.network

/**
 * How the device is connected, reduced to what a profile can be bound to.
 *
 * The SSID is deliberately absent: reading it needs location permission on Android 10+, which
 * Mizan does not request. A profile therefore binds to the kind of connection, and the system's
 * own metered flag separates a home Wi-Fi (unmetered) from a phone hotspot (metered).
 */
enum class NetworkTransport { CELLULAR, WIFI, ETHERNET, VPN, OTHER }

/**
 * What the platform reports about the active connection, with no profile applied yet.
 *
 * [temporarilyUnmetered] is the user's short-lived "treat as unmetered" choice in the system's
 * data-warning dialog (API 30+), [roaming] is kept for display only: roaming is not
 * automatically metered because plans differ.
 */
data class NetworkSnapshot(
    val connected: Boolean,
    val transport: NetworkTransport,
    val systemMetered: Boolean,
    val temporarilyUnmetered: Boolean = false,
    val roaming: Boolean = false,
)

/**
 * A user-named profile for one kind of connection ("البيت", "هوتسبوت").
 *
 * [systemMetered] is the platform's flag captured for the connection the profile was bound to;
 * together with [transport] it identifies the kind of network. [metered] is the user's
 * declaration for that kind, which can disagree with the platform (for example a carrier that
 * reports Wi-Fi tethering as unmetered).
 */
data class NetworkProfile(
    val id: Long = 0L,
    val name: String,
    val transport: NetworkTransport,
    val systemMetered: Boolean,
    val metered: Boolean,
    val monthlyBudgetBytes: Long? = null,
    val blockedPackages: List<String> = emptyList(),
    val createdAt: Long = 0L,
)

/** The connection after applying a matching profile, ready for display and for Data Saver. */
data class ResolvedNetwork(
    val connected: Boolean,
    val transport: NetworkTransport,
    val metered: Boolean,
    val roaming: Boolean,
    val profile: NetworkProfile?,
)

/**
 * Decides whether the current connection counts as metered (docs/01, docs/02 §3).
 *
 * Precedence, most specific first:
 * 1. The system's "temporarily unmetered" capability, which the user sets for the current
 *    network and expects to win immediately.
 * 2. A profile bound to the same [NetworkTransport] and the same platform metered flag. The
 *    profile's [NetworkProfile.metered] wins because it is an explicit in-app decision.
 * 3. The platform's own metered flag.
 *
 * A disconnected device is never metered: there is no plan to spend. The SSID is not consulted,
 * so a profile follows the type of network rather than one specific access point.
 */
class NetworkProfileResolver {

    fun resolve(
        snapshot: NetworkSnapshot,
        profiles: List<NetworkProfile>,
    ): ResolvedNetwork {
        if (!snapshot.connected) {
            return ResolvedNetwork(
                connected = false,
                transport = snapshot.transport,
                metered = false,
                roaming = false,
                profile = null,
            )
        }

        val profile = profiles.firstOrNull {
            it.transport == snapshot.transport && it.systemMetered == snapshot.systemMetered
        }
        val metered = when {
            snapshot.temporarilyUnmetered -> false
            profile != null -> profile.metered
            else -> snapshot.systemMetered
        }

        return ResolvedNetwork(
            connected = true,
            transport = snapshot.transport,
            metered = metered,
            roaming = snapshot.roaming,
            profile = profile,
        )
    }
}
