package com.iwadjp.wollight.debug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * DEBUG-only helper used to simulate a stale registered IP address for manual
 * verification of the IP-drift recovery flow, without touching persisted device
 * data or any network/router configuration.
 *
 * The generated address is never written to the database; callers must only use
 * it as an in-memory substitute when invoking the ping/recovery flow.
 */
object StaleIpSimulator {

    /** Octets tried in order, excluding network/broadcast/gateway-like addresses. */
    private val CANDIDATE_LAST_OCTETS = listOf(253, 252, 251, 250, 249, 248, 247, 246, 245, 244)

    private const val PROBE_TIMEOUT_MS = 300

    /**
     * Returns an IPv4 address in the same /24 as [realIpAddress] that currently
     * does not respond to a reachability probe, or null if [realIpAddress] is not
     * a plain IPv4 dotted-quad, or no safe candidate could be confirmed unused.
     */
    suspend fun findStaleIp(realIpAddress: String): String? = withContext(Dispatchers.IO) {
        val octets = parseIpv4(realIpAddress) ?: return@withContext null
        val prefix = "${octets[0]}.${octets[1]}.${octets[2]}"
        val currentLastOctet = octets[3]

        for (lastOctet in candidateOctets(currentLastOctet)) {
            val candidateIp = "$prefix.$lastOctet"
            val respondingHost = runCatching {
                InetAddress.getByName(candidateIp).isReachable(PROBE_TIMEOUT_MS)
            }.getOrDefault(true) // treat probe errors as "can't confirm unused" -> skip candidate
            if (!respondingHost) return@withContext candidateIp
        }
        null
    }

    /**
     * Pure candidate-selection logic (no network I/O): the ordered list of last-octet
     * candidates to probe, with the network/broadcast/gateway-like addresses and the
     * currently registered address already excluded.
     */
    internal fun candidateOctets(currentLastOctet: Int): List<Int> {
        val reserved = setOf(0, 1, 255, currentLastOctet)
        return CANDIDATE_LAST_OCTETS.filter { it !in reserved }
    }

    internal fun parseIpv4(ip: String): IntArray? {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0..3) {
            val value = parts[i].toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets[i] = value
        }
        return octets
    }
}
