package com.iwadjp.wollight.network

import java.net.Inet4Address
import java.net.InetAddress

data class IpSubnet(val address: InetAddress, val prefixLength: Int)

data class LanNetworkSnapshot(
    val id: String,
    val isVpn: Boolean,
    val isLan: Boolean,
    val addresses: List<IpSubnet>,
    val linkIdentity: String = "",
)

enum class LanScanFailure { NO_LAN, NO_IPV4, AMBIGUOUS_LAN, SUBNET_TOO_LARGE, NO_TARGETS, NETWORK_UNAVAILABLE, NETWORK_CHANGED }

class LanScanException(val reason: LanScanFailure) : IllegalStateException(reason.name)

// Bound allocation and work before enumerating addresses. A /22 fits; larger
// subnets are rejected, never narrowed to an unrelated /24.
const val MAX_SCAN_TARGETS = 1024

fun selectLanSubnet(networks: List<LanNetworkSnapshot>, activeNetworkId: String?): IpSubnet {
    return selectLanNetwork(networks, activeNetworkId).addresses.filter { usableIpv4(it) }.distinct().single()
}

private fun usableIpv4(subnet: IpSubnet) = subnet.address is Inet4Address &&
    !subnet.address.isLoopbackAddress && !subnet.address.isAnyLocalAddress && !subnet.address.isMulticastAddress

internal fun selectLanNetwork(networks: List<LanNetworkSnapshot>, activeNetworkId: String?): LanNetworkSnapshot {
    val lans = networks.filter { it.isLan && !it.isVpn }
    if (lans.isEmpty()) throw LanScanException(LanScanFailure.NO_LAN)
    val ipv4Lans = lans.map { network ->
        network to network.addresses.filter {
            usableIpv4(it)
        }.distinct()
    }.filter { it.second.isNotEmpty() }
    if (ipv4Lans.isEmpty()) throw LanScanException(LanScanFailure.NO_IPV4)
    val selected = ipv4Lans.singleOrNull { it.first.id == activeNetworkId }
        ?: ipv4Lans.singleOrNull()
        ?: throw LanScanException(LanScanFailure.AMBIGUOUS_LAN)
    if (selected.second.size != 1) throw LanScanException(LanScanFailure.AMBIGUOUS_LAN)
    return selected.first
}

fun scanTargets(subnet: IpSubnet): List<String> {
    if (subnet.address !is Inet4Address || subnet.prefixLength !in 0..32) {
        throw LanScanException(LanScanFailure.NETWORK_UNAVAILABLE)
    }
    val self = subnet.address.address.fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 255) }
    val mask = (0xffffffffL shl (32 - subnet.prefixLength)) and 0xffffffffL
    val network = self and mask
    val broadcast = network or (0xffffffffL xor mask)
    val count = (broadcast - network - 1).coerceAtLeast(0) -
        if (self > network && self < broadcast) 1 else 0
    if (count > MAX_SCAN_TARGETS) throw LanScanException(LanScanFailure.SUBNET_TOO_LARGE)
    if (count == 0L) throw LanScanException(LanScanFailure.NO_TARGETS)
    return (network + 1 until broadcast).filter { it != self }.map { ip ->
        (24 downTo 0 step 8).joinToString(".") { shift -> ((ip shr shift) and 255).toString() }
    }
}
