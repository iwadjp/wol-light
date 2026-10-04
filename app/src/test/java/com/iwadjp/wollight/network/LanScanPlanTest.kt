package com.iwadjp.wollight.network

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class LanScanPlanTest {
    private fun subnet(ip: String = "192.168.1.42", prefix: Int = 24) =
        IpSubnet(InetAddress.getByName(ip), prefix)
    private fun lan(id: String = "wifi", addresses: List<IpSubnet> = listOf(subnet())) =
        LanNetworkSnapshot(id, isVpn = false, isLan = true, addresses)
    private val vpn = LanNetworkSnapshot("vpn", true, false, listOf(subnet("10.8.0.2")))
    private fun fails(reason: LanScanFailure, block: () -> Unit) {
        val error = assertThrows(LanScanException::class.java, block)
        assertEquals(reason, error.reason)
    }

    @Test fun vpnAndWifiSelectWifi() {
        assertEquals(subnet(), selectLanSubnet(listOf(vpn, lan()), "vpn"))
    }
    @Test fun vpnAndEthernetSelectEthernet() {
        val ethernet = lan("ethernet", listOf(subnet("192.168.2.10")))
        assertEquals(ethernet.addresses.single(), selectLanSubnet(listOf(vpn, ethernet), "vpn"))
    }
    @Test fun vpnOnlyFails() = fails(LanScanFailure.NO_LAN) {
        selectLanSubnet(listOf(vpn), "vpn")
    }
    @Test fun vpnAdvertisingWifiIsStillRejected() = fails(LanScanFailure.NO_LAN) {
        selectLanSubnet(listOf(vpn.copy(isLan = true)), "vpn")
    }
    @Test fun noLanFails() = fails(LanScanFailure.NO_LAN) {
        selectLanSubnet(listOf(lan("cellular").copy(isLan = false)), "cellular")
    }
    @Test fun noNetworksFails() = fails(LanScanFailure.NO_LAN) { selectLanSubnet(emptyList(), null) }
    @Test fun noIpv4Fails() = fails(LanScanFailure.NO_IPV4) {
        selectLanSubnet(listOf(lan(addresses = listOf(subnet("2001:db8::1", 64)))), "wifi")
    }
    @Test fun mixedIpv4Ipv6SelectsIpv4() {
        assertEquals(subnet(), selectLanSubnet(listOf(lan(addresses = listOf(subnet("2001:db8::1", 64), subnet()))), "wifi"))
    }
    @Test fun activePhysicalLanBreaksTie() {
        assertEquals(subnet("192.168.2.10"), selectLanSubnet(listOf(lan(), lan("ethernet", listOf(subnet("192.168.2.10")))), "ethernet"))
    }
    @Test fun ambiguousPhysicalLansFailWhenVpnIsActive() = fails(LanScanFailure.AMBIGUOUS_LAN) {
        selectLanSubnet(listOf(vpn, lan(), lan("ethernet")), "vpn")
    }
    @Test fun multipleIpv4AddressesFail() = fails(LanScanFailure.AMBIGUOUS_LAN) {
        selectLanSubnet(listOf(lan(addresses = listOf(subnet(), subnet("192.168.2.10")))), "wifi")
    }
    @Test fun duplicateLinkAddressesAreHarmless() {
        assertEquals(subnet(), selectLanSubnet(listOf(lan(addresses = listOf(subnet(), subnet()))), "wifi"))
    }
    @Test fun unusableAddressesAreExcluded() = fails(LanScanFailure.NO_IPV4) {
        selectLanSubnet(listOf(lan(addresses = listOf(subnet("127.0.0.1"), subnet("0.0.0.0"), subnet("224.0.0.1")))), "wifi")
    }
    @Test fun cidr23CrossesOctetBoundaryAndExcludesNetworkBroadcastSelf() {
        val targets = scanTargets(subnet("192.168.1.42", 23))
        assertEquals(509, targets.size)
        assertEquals("192.168.0.1", targets.first())
        assertEquals("192.168.1.254", targets.last())
        assertTrue(targets.contains("192.168.0.255"))
        assertTrue(targets.contains("192.168.1.0"))
        assertFalse(targets.contains("192.168.0.0"))
        assertFalse(targets.contains("192.168.1.255"))
        assertFalse(targets.contains("192.168.1.42"))
    }
    @Test fun cidr24Has253Peers() {
        val targets = scanTargets(subnet())
        assertEquals(253, targets.size)
        assertEquals("192.168.1.1", targets.first())
        assertEquals("192.168.1.254", targets.last())
        assertFalse(targets.contains("192.168.1.0"))
        assertFalse(targets.contains("192.168.1.255"))
        assertFalse(targets.contains("192.168.1.42"))
    }
    @Test fun cidr25UsesCorrectUpperHalf() {
        val targets = scanTargets(subnet("192.168.1.200", 25))
        assertEquals(125, targets.size)
        assertEquals("192.168.1.129", targets.first())
        assertEquals("192.168.1.254", targets.last())
        assertFalse(targets.contains("192.168.1.128"))
        assertFalse(targets.contains("192.168.1.255"))
        assertFalse(targets.contains("192.168.1.200"))
    }
    @Test fun cidr25UsesCorrectLowerHalf() {
        assertEquals("192.168.1.126", scanTargets(subnet("192.168.1.42", 25)).last())
    }
    @Test fun cidr30HasOnePeer() = assertEquals(listOf("10.0.0.2"), scanTargets(subnet("10.0.0.1", 30)))
    @Test fun cidr31HasNoTargets() = fails(LanScanFailure.NO_TARGETS) { scanTargets(subnet("10.0.0.0", 31)) }
    @Test fun cidr32HasNoTargets() = fails(LanScanFailure.NO_TARGETS) { scanTargets(subnet("10.0.0.1", 32)) }
    @Test fun cidr22FitsSafetyLimit() = assertEquals(1021, scanTargets(subnet(prefix = 22)).size)
    @Test fun largeSubnetIsRejectedWithoutTruncation() = fails(LanScanFailure.SUBNET_TOO_LARGE) { scanTargets(subnet(prefix = 21)) }
    @Test fun entireIpv4RangeDoesNotOverflowOrAllocate() = fails(LanScanFailure.SUBNET_TOO_LARGE) { scanTargets(subnet(prefix = 0)) }
    @Test fun upperIpv4RangeDoesNotOverflow() {
        assertEquals(listOf("255.255.255.254"), scanTargets(subnet("255.255.255.253", 30)))
    }
    @Test fun invalidPrefixFails() = fails(LanScanFailure.NETWORK_UNAVAILABLE) { scanTargets(subnet(prefix = 33)) }
    @Test fun ipv6RangeFails() = fails(LanScanFailure.NETWORK_UNAVAILABLE) { scanTargets(subnet("2001:db8::1", 64)) }
}
