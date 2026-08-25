package com.iwadjp.wollight.debug

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleIpSimulatorTest {

    @Test
    fun `parseIpv4 accepts a plain dotted-quad`() {
        val octets = StaleIpSimulator.parseIpv4("192.168.1.10")
        assertEquals(listOf(192, 168, 1, 10), octets?.toList())
    }

    @Test
    fun `parseIpv4 rejects wrong segment count`() {
        assertNull(StaleIpSimulator.parseIpv4("192.168.1"))
    }

    @Test
    fun `parseIpv4 rejects out-of-range octet`() {
        assertNull(StaleIpSimulator.parseIpv4("192.168.1.999"))
    }

    @Test
    fun `parseIpv4 rejects non-numeric segment`() {
        assertNull(StaleIpSimulator.parseIpv4("192.168.1.abc"))
    }

    @Test
    fun `candidateOctets excludes network, broadcast, gateway-like and current address`() {
        val candidates = StaleIpSimulator.candidateOctets(currentLastOctet = 44)
        assertFalse(candidates.contains(0))
        assertFalse(candidates.contains(1))
        assertFalse(candidates.contains(255))
        assertFalse(candidates.contains(44))
        assertTrue(candidates.isNotEmpty())
    }

    @Test
    fun `candidateOctets omits a candidate that equals the current last octet`() {
        val candidates = StaleIpSimulator.candidateOctets(currentLastOctet = 253)
        assertFalse(candidates.contains(253))
    }

    @Test
    fun `findStaleIp returns null for a non-IPv4 address without any network probe`() = runBlocking {
        val result = StaleIpSimulator.findStaleIp("not-an-ip")
        assertNull(result)
    }

    @Test
    fun `findStaleIp returns null for an out-of-range address without any network probe`() = runBlocking {
        val result = StaleIpSimulator.findStaleIp("192.168.1.999")
        assertNull(result)
    }
}
