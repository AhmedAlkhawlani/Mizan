package com.maknoon.mizan.core.domain.saver

private const val IPPROTO_TCP = 6
private const val IPPROTO_UDP = 17

/**
 * One IP packet read from the blocking-only tunnel, reduced to the fields the counter needs.
 *
 * [protocol] is the IPv4 protocol / IPv6 next-header value (TCP = 6, UDP = 17). [sourcePort] and
 * [destinationPort] are meaningful only for TCP and UDP; for any other protocol they are -1 and
 * the packet can only be counted as unknown. [byteCount] is the full IP packet size including
 * headers, as declared by the packet itself.
 */
data class ParsedIpPacket(
    val protocol: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val byteCount: Int,
)

/**
 * Parses [length] bytes of [bytes] as one IPv4 or IPv6 packet.
 *
 * Returns null for anything that is not a well-formed IPv4/IPv6 packet. The tunnel only ever
 * delivers IP packets, so a null here means the read cannot be trusted rather than a counter
 * error, and the caller counts those bytes as unknown.
 */
fun parseIpPacket(bytes: ByteArray, length: Int): ParsedIpPacket? {
    if (length <= 0) return null
    val version = (bytes[0].toInt() shr 4) and 0xF
    return when (version) {
        4 -> parseIpv4(bytes, length)
        6 -> parseIpv6(bytes, length)
        else -> null
    }
}

private fun parseIpv4(bytes: ByteArray, length: Int): ParsedIpPacket? {
    if (length < 20) return null
    val headerLength = (bytes[0].toInt() and 0xF) * 4
    if (headerLength < 20 || headerLength > length) return null
    val totalLength = u16(bytes, 2)
    if (totalLength < headerLength) return null
    val protocol = bytes[9].toInt() and 0xFF
    val sourceAddress = bytes.copyOfRange(12, 16)
    val destinationAddress = bytes.copyOfRange(16, 20)
    val (sourcePort, destinationPort) =
        if (protocol == IPPROTO_TCP || protocol == IPPROTO_UDP) {
            if (headerLength + 4 > length) return null
            u16(bytes, headerLength) to u16(bytes, headerLength + 2)
        } else {
            -1 to -1
        }
    return ParsedIpPacket(
        protocol = protocol,
        sourceAddress = sourceAddress,
        destinationAddress = destinationAddress,
        sourcePort = sourcePort,
        destinationPort = destinationPort,
        byteCount = minOf(totalLength, length),
    )
}

private fun parseIpv6(bytes: ByteArray, length: Int): ParsedIpPacket? {
    if (length < 40) return null
    val payloadLength = u16(bytes, 4)
    val nextHeader = bytes[6].toInt() and 0xFF
    val sourceAddress = bytes.copyOfRange(8, 24)
    val destinationAddress = bytes.copyOfRange(24, 40)
    // Extension headers are left in place: their bytes still count, but without a TCP/UDP header
    // at offset 40 the packet has no ports and is counted as unknown rather than misattributed.
    val (sourcePort, destinationPort) =
        if (nextHeader == IPPROTO_TCP || nextHeader == IPPROTO_UDP) {
            if (40 + 4 > length) return null
            u16(bytes, 40) to u16(bytes, 42)
        } else {
            -1 to -1
        }
    val totalLength = 40 + payloadLength
    return ParsedIpPacket(
        protocol = nextHeader,
        sourceAddress = sourceAddress,
        destinationAddress = destinationAddress,
        sourcePort = sourcePort,
        destinationPort = destinationPort,
        byteCount = minOf(totalLength, length),
    )
}

private fun u16(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
