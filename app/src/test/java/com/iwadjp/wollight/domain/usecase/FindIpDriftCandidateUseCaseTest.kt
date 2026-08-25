package com.iwadjp.wollight.domain.usecase

import com.iwadjp.wollight.model.Device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindIpDriftCandidateUseCaseTest {

    private val useCase = FindIpDriftCandidateUseCase()

    private fun device(ip: String, mac: String, id: Long = 1L) =
        Device(id = id, name = "test", ipAddress = ip, macAddress = mac)

    @Test
    fun `no scan results yields None`() {
        val registered = device("192.168.1.10", "AA:BB:CC:DD:EE:FF")
        val result = useCase(registered, emptyList())
        assertTrue(result is IpDriftCandidate.None)
    }

    @Test
    fun `same MAC and same IP yields None`() {
        val registered = device("192.168.1.10", "AA:BB:CC:DD:EE:FF")
        val scanResults = listOf(device("192.168.1.10", "aa:bb:cc:dd:ee:ff"))
        val result = useCase(registered, scanResults)
        assertTrue(result is IpDriftCandidate.None)
    }

    @Test
    fun `same MAC different IP yields Found with new IP`() {
        val registered = device("192.168.1.10", "AA:BB:CC:DD:EE:FF")
        val scanResults = listOf(device("192.168.1.99", "aa:bb:cc:dd:ee:ff"))
        val result = useCase(registered, scanResults)
        assertTrue(result is IpDriftCandidate.Found)
        assertEquals("192.168.1.99", (result as IpDriftCandidate.Found).newIpAddress)
    }

    @Test
    fun `same MAC found at multiple distinct IPs yields Ambiguous`() {
        val registered = device("192.168.1.10", "AA:BB:CC:DD:EE:FF")
        val scanResults = listOf(
            device("192.168.1.99", "aa:bb:cc:dd:ee:ff"),
            device("192.168.1.100", "aa-bb-cc-dd-ee-ff")
        )
        val result = useCase(registered, scanResults)
        assertTrue(result is IpDriftCandidate.Ambiguous)
    }

    @Test
    fun `registered device with invalid MAC yields None without matching`() {
        val registered = device("192.168.1.10", "not-a-mac")
        val scanResults = listOf(device("192.168.1.99", "AA:BB:CC:DD:EE:FF"))
        val result = useCase(registered, scanResults)
        assertTrue(result is IpDriftCandidate.None)
    }

    @Test
    fun `registered device with empty MAC yields None without matching`() {
        val registered = device("192.168.1.10", "")
        val scanResults = listOf(device("192.168.1.99", ""))
        val result = useCase(registered, scanResults)
        assertTrue(result is IpDriftCandidate.None)
    }

    @Test
    fun `scan entries with empty MAC are ignored as candidates`() {
        val registered = device("192.168.1.10", "AA:BB:CC:DD:EE:FF")
        val scanResults = listOf(device("192.168.1.99", ""))
        val result = useCase(registered, scanResults)
        assertTrue(result is IpDriftCandidate.None)
    }

    @Test
    fun `duplicate scan rows with the same IP for the same MAC still count as a single match`() {
        val registered = device("192.168.1.10", "AA:BB:CC:DD:EE:FF")
        val scanResults = listOf(
            device("192.168.1.99", "aa:bb:cc:dd:ee:ff"),
            device("192.168.1.99", "AA:BB:CC:DD:EE:FF")
        )
        val result = useCase(registered, scanResults)
        assertTrue(result is IpDriftCandidate.Found)
        assertEquals("192.168.1.99", (result as IpDriftCandidate.Found).newIpAddress)
    }
}
