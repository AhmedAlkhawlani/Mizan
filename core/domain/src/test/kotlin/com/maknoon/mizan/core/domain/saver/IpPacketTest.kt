package com.maknoon.mizan.core.domain.saver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IpPacketTest {

    private fun ipv4(protocol: Int, headerLengthWords: Int = 5, totalLength: Int = 40): ByteArray {
        val b = ByteArray(40)
        b[0] = ((4 shl 4) or headerLengthWords).toByte()
        b[2] = (totalLength shr 8).toByte()
        b[3] = totalLength.toByte()
        b[8] = 64
        b[9] = protocol.toByte()
        b[12] = 10; b[13] = 0; b[14] = 0; b[15] = 1
        b[16] = 8; b[17] = 8; b[18] = 8; b[19] = 8
        return b
    }

    private fun ipv6(nextHeader: Int, payloadLength: Int): ByteArray {
        val b = ByteArray(40 + payloadLength)
        b[0] = 0x60
        b[4] = (payloadLength shr 8).toByte()
        b[5] = payloadLength.toByte()
        b[6] = nextHeader.toByte()
        b[22] = 0; b[23] = 1   // src 2001:db8::1
        b[38] = 0; b[39] = 2   // dst 2001:db8::2
        return b
    }

    private fun ByteArray.setU16(offset: Int, value: Int) {
        this[offset] = (value shr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    @Test
    fun `parses an IPv4 TCP packet`() {
        val bytes = ipv4(protocol = 6)
        bytes.setU16(20, 12345)
        bytes.setU16(22, 443)

        val packet = parseIpPacket(bytes, 40)!!

        assertThat(packet.protocol).isEqualTo(6)
        assertThat(packet.sourcePort).isEqualTo(12345)
        assertThat(packet.destinationPort).isEqualTo(443)
        assertThat(packet.byteCount).isEqualTo(40)
        assertThat(packet.sourceAddress).isEqualTo(byteArrayOf(10, 0, 0, 1))
        assertThat(packet.destinationAddress).isEqualTo(byteArrayOf(8, 8, 8, 8))
    }

    @Test
    fun `parses an IPv4 UDP packet`() {
        val bytes = ipv4(protocol = 17)
        bytes.setU16(20, 54321)
        bytes.setU16(22, 53)

        val packet = parseIpPacket(bytes, 40)!!

        assertThat(packet.protocol).isEqualTo(17)
        assertThat(packet.sourcePort).isEqualTo(54321)
        assertThat(packet.destinationPort).isEqualTo(53)
    }

    @Test
    fun `a non TCP or UDP packet carries no ports`() {
        val packet = parseIpPacket(ipv4(protocol = 1), 40)!!

        assertThat(packet.protocol).isEqualTo(1)
        assertThat(packet.sourcePort).isEqualTo(-1)
        assertThat(packet.destinationPort).isEqualTo(-1)
        assertThat(packet.byteCount).isEqualTo(40)
    }

    @Test
    fun `ports follow an IPv4 options header`() {
        val bytes = ByteArray(44)
        bytes[0] = ((4 shl 4) or 6).toByte()   // IHL 6 -> 24-byte header
        bytes[2] = 0x00
        bytes[3] = 0x2C                       // total length 44
        bytes[9] = 6
        bytes[12] = 10; bytes[13] = 0; bytes[14] = 0; bytes[15] = 1
        bytes[16] = 8; bytes[17] = 8; bytes[18] = 8; bytes[19] = 8
        bytes.setU16(24, 3000)
        bytes.setU16(26, 443)

        val packet = parseIpPacket(bytes, 44)!!

        assertThat(packet.sourcePort).isEqualTo(3000)
        assertThat(packet.destinationPort).isEqualTo(443)
        assertThat(packet.byteCount).isEqualTo(44)
    }

    @Test
    fun `a shorter buffer than the IPv4 header is null`() {
        assertThat(parseIpPacket(ipv4(protocol = 6), 10)).isNull()
    }

    @Test
    fun `byte count is clamped to the bytes actually read`() {
        val bytes = ipv4(protocol = 6, totalLength = 65535)

        val packet = parseIpPacket(bytes, 40)!!

        assertThat(packet.byteCount).isEqualTo(40)
    }

    @Test
    fun `parses an IPv6 TCP packet`() {
        val bytes = ipv6(nextHeader = 6, payloadLength = 4)
        bytes.setU16(40, 12345)
        bytes.setU16(42, 443)

        val packet = parseIpPacket(bytes, 44)!!

        assertThat(packet.protocol).isEqualTo(6)
        assertThat(packet.sourcePort).isEqualTo(12345)
        assertThat(packet.destinationPort).isEqualTo(443)
        assertThat(packet.byteCount).isEqualTo(44)
    }

    @Test
    fun `parses an IPv6 UDP packet`() {
        val bytes = ipv6(nextHeader = 17, payloadLength = 4)
        bytes.setU16(40, 54321)
        bytes.setU16(42, 53)

        val packet = parseIpPacket(bytes, 44)!!

        assertThat(packet.protocol).isEqualTo(17)
        assertThat(packet.sourcePort).isEqualTo(54321)
        assertThat(packet.destinationPort).isEqualTo(53)
    }

    @Test
    fun `an IPv6 extension header leaves the packet portless`() {
        val packet = parseIpPacket(ipv6(nextHeader = 44, payloadLength = 8), 48)!!

        assertThat(packet.protocol).isEqualTo(44)
        assertThat(packet.sourcePort).isEqualTo(-1)
        assertThat(packet.destinationPort).isEqualTo(-1)
        assertThat(packet.byteCount).isEqualTo(48)
    }

    @Test
    fun `an unknown version is null`() {
        val bytes = ipv4(protocol = 6)
        bytes[0] = 0x50   // version 5

        assertThat(parseIpPacket(bytes, 40)).isNull()
    }

    @Test
    fun `an empty read is null`() {
        assertThat(parseIpPacket(ByteArray(0), 0)).isNull()
    }
}
