package com.iwadjp.wollight.domain.usecase

import com.iwadjp.wollight.model.Device
import com.iwadjp.wollight.util.MacAddressNormalizer
import javax.inject.Inject

sealed class IpDriftCandidate {
    data object None : IpDriftCandidate()
    data class Found(val newIpAddress: String) : IpDriftCandidate()
    data object Ambiguous : IpDriftCandidate()
}

/**
 * Compares a registered device against a fresh LAN scan result set, looking for the
 * same MAC address at a different IP address (stale-IP recovery candidate).
 */
class FindIpDriftCandidateUseCase @Inject constructor() {

    operator fun invoke(registered: Device, scanResults: List<Device>): IpDriftCandidate {
        val registeredMac = MacAddressNormalizer.normalize(registered.macAddress) ?: return IpDriftCandidate.None

        val matchingIps = scanResults
            .mapNotNull { MacAddressNormalizer.normalize(it.macAddress)?.let { mac -> mac to it.ipAddress } }
            .filter { (mac, _) -> mac == registeredMac }
            .map { (_, ip) -> ip }
            .distinct()

        return when (matchingIps.size) {
            0 -> IpDriftCandidate.None
            1 -> {
                val newIp = matchingIps.first()
                if (newIp == registered.ipAddress) IpDriftCandidate.None else IpDriftCandidate.Found(newIp)
            }
            else -> IpDriftCandidate.Ambiguous
        }
    }
}
