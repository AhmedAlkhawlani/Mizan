package com.maknoon.mizan.core.domain.saver

/** Accumulated packets and bytes for one attribution bucket. */
data class TrafficCount(val packets: Long = 0, val bytes: Long = 0) {
    operator fun plus(other: TrafficCount): TrafficCount =
        TrafficCount(packets + other.packets, bytes + other.bytes)

    companion object {
        val ZERO = TrafficCount()
    }
}

/**
 * The blocked attempts counted so far for the active tunnel.
 *
 * [perPackage] holds one entry per unambiguous package. Anything the platform cannot attribute —
 * a shared UID, a non-TCP/UDP packet, or a device below API 29 — lands in [unknown], so a byte
 * is never guessed onto an app it may not belong to.
 */
data class BlockedTrafficSnapshot(
    val perPackage: Map<String, TrafficCount> = emptyMap(),
    val unknown: TrafficCount = TrafficCount.ZERO,
) {
    val totalPackets: Long get() = perPackage.values.sumOf { it.packets } + unknown.packets
    val totalBytes: Long get() = perPackage.values.sumOf { it.bytes } + unknown.bytes
}

/** Returns a copy of [this] with one dropped packet of [bytes] bytes added to [packageName]. */
fun BlockedTrafficSnapshot.count(packageName: String?, bytes: Int): BlockedTrafficSnapshot {
    val increment = TrafficCount(packets = 1, bytes = bytes.toLong())
    return if (packageName == null) {
        copy(unknown = unknown + increment)
    } else {
        val updated = (perPackage[packageName] ?: TrafficCount.ZERO) + increment
        copy(perPackage = perPackage + (packageName to updated))
    }
}
