package com.iwadjp.wollight.network

import com.iwadjp.wollight.domain.usecase.FindIpDriftCandidateUseCase
import com.iwadjp.wollight.domain.usecase.IpDriftCandidate
import com.iwadjp.wollight.model.Device
import java.net.InetAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LanScanRunnerTest {
    private val lan = LanNetworkSnapshot("wifi", false, true,
        listOf(IpSubnet(InetAddress.getByName("192.168.1.1"), 30)), "original link")
    private fun tracker() = LanNetworkTracker().apply { update(lan); pin("wifi") }

    @Test fun changedAddressInvalidatesPinnedScan() {
        val state = tracker()
        state.update(lan.copy(addresses = listOf(IpSubnet(InetAddress.getByName("192.168.2.1"), 30))))
        assertThrows(LanScanException::class.java) { state.checkValid() }
    }
    @Test fun changedPrefixInvalidatesPinnedScan() {
        val state = tracker()
        state.update(lan.copy(addresses = listOf(lan.addresses.single().copy(prefixLength = 29))))
        assertTrue(state.changed.isCompleted)
    }
    @Test fun changedLinkInvalidatesPinnedScan() {
        val state = tracker(); state.update(lan.copy(linkIdentity = "different interface or routes"))
        assertTrue(state.changed.isCompleted)
    }
    @Test fun vpnOrTransportChangeInvalidatesPinnedScan() {
        val state = tracker(); state.update(lan.copy(isVpn = true))
        assertTrue(state.changed.isCompleted)
        val second = tracker(); second.update(lan.copy(isLan = false))
        assertTrue(second.changed.isCompleted)
    }
    @Test fun lossThenReturnDoesNotResumeOrFallback() {
        val state = tracker(); state.lost("wifi"); state.update(lan)
        state.update(lan.copy(id = "ethernet"))
        assertTrue(state.changed.isCompleted)
    }
    @Test fun duplicateAndOtherNetworkUpdatesDoNotAbort() {
        val state = tracker(); state.update(lan); state.update(lan.copy(id = "ethernet")); state.lost("ethernet")
        state.checkValid()
    }
    @Test fun actualTargetCountAndResultsSupportIpRecovery(): Unit = runBlocking {
        val counts = mutableListOf<Pair<Int, Int>>()
        val results = scanLanTargets(lan.addresses.single(), tracker(),
            { count, total -> counts.add(count to total) }, { true }, { "PC" })
        assertEquals(listOf(0 to 1, 1 to 1), counts)
        assertEquals(listOf("192.168.1.2" to "PC"), results)
        val mac = "AA:BB:CC:DD:EE:FF"
        val old = Device(name = "PC", ipAddress = "192.168.1.3", macAddress = mac)
        assertEquals(IpDriftCandidate.Found("192.168.1.2"), FindIpDriftCandidateUseCase()(old,
            results.map { (ip, name) -> Device(name = name, ipAddress = ip, macAddress = mac) }))
    }
    @Test fun cidr25ProgressCompletesAllTargetsWithBoundedConcurrency(): Unit = runBlocking {
        val counts = mutableListOf<Int>()
        var inFlight = 0; var maximum = 0
        val subnet = lan.addresses.single().copy(prefixLength = 25)
        val results = scanLanTargets(subnet, tracker(), { count, total ->
            assertEquals(125, total); counts.add(count)
        }, {
            inFlight++; maximum = maxOf(maximum, inFlight)
            delay(1); inFlight--; false
        }, { it })
        assertTrue(results.isEmpty())
        assertEquals((0..125).toList(), counts)
        assertTrue(maximum <= 32)
    }
    @Test fun networkLossDiscardsResultsAndStopsProgress(): Unit = runBlocking {
        val state = tracker(); val counts = mutableListOf<Int>()
        try {
            scanLanTargets(lan.addresses.single(), state, { count, _ -> counts.add(count) },
                { state.lost("wifi"); true }, { "PC" })
            fail("Must not publish results after loss")
        } catch (error: LanScanException) { assertEquals(LanScanFailure.NETWORK_CHANGED, error.reason) }
        assertEquals(listOf(0), counts)
    }
    @Test fun networkChangeDuringNameResolutionDiscardsResults(): Unit = runBlocking {
        val state = tracker()
        try {
            scanLanTargets(lan.addresses.single(), state, { _, _ -> }, { true },
                { state.update(lan.copy(linkIdentity = "changed")); "PC" })
            fail("Must not publish partial results")
        } catch (error: LanScanException) { assertEquals(LanScanFailure.NETWORK_CHANGED, error.reason) }
    }
    @Test fun cancelStopsWorkWithoutFinalProgress(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>(); val counts = mutableListOf<Int>()
        val work = async {
            scanLanTargets(lan.addresses.single(), tracker(), { count, _ -> counts.add(count) },
                { started.complete(Unit); awaitCancellation() }, { it })
        }
        started.await(); work.cancelAndJoin()
        assertTrue(work.isCancelled)
        assertEquals(listOf(0), counts)
    }
}
