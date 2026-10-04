package com.iwadjp.wollight.network

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** Callback-fed state. Once pinned, losing/changing it permanently invalidates this scan. */
internal class LanNetworkTracker {
    private val networks = mutableMapOf<String, LanNetworkSnapshot>()
    private var selected: LanNetworkSnapshot? = null
    val changed = CompletableDeferred<Unit>()

    @Synchronized fun update(snapshot: LanNetworkSnapshot) {
        networks[snapshot.id] = snapshot
        val pinned = selected
        if (pinned?.id == snapshot.id && pinned != snapshot) changed.complete(Unit)
    }

    @Synchronized fun lost(id: String) {
        networks.remove(id)
        if (selected?.id == id) changed.complete(Unit)
    }

    @Synchronized fun pin(activeId: String?): IpSubnet {
        selected = selectLanNetwork(networks.values.toList(), activeId)
        return selectLanSubnet(listOf(selected!!), activeId)
    }

    fun checkValid() {
        if (changed.isCompleted) throw LanScanException(LanScanFailure.NETWORK_CHANGED)
    }
}

/** Observe existing LANs only; never request connectivity or bypass a VPN. API 26+. */
internal class LanNetworkMonitor(private val cm: ConnectivityManager) : AutoCloseable {
    val tracker = LanNetworkTracker()
    private val updates = Channel<Unit>(Channel.CONFLATED)
    // Only accessed from this callback's serial delivery thread.
    private val capabilities = mutableMapOf<Network, NetworkCapabilities>()
    private val links = mutableMapOf<Network, LinkProperties>()
    private val blockedNetworks = mutableSetOf<Network>()
    private var registered = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { updates.trySend(Unit) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            capabilities[network] = NetworkCapabilities(caps)
            publish(network)
        }
        override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
            links[network] = props
            publish(network)
        }
        override fun onLost(network: Network) {
            capabilities.remove(network)
            links.remove(network)
            blockedNetworks.remove(network)
            tracker.lost(network.toString())
            updates.trySend(Unit)
        }
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            if (blocked) {
                blockedNetworks.add(network)
                tracker.lost(network.toString())
            } else {
                blockedNetworks.remove(network)
                publish(network)
            }
            updates.trySend(Unit)
        }
        override fun onLosing(network: Network, maxMsToLive: Int) { tracker.lost(network.toString()) }
    }

    private fun publish(network: Network) {
        if (network in blockedNetworks) return
        val caps = capabilities[network] ?: return
        val props = links[network] ?: return
        tracker.update(LanNetworkSnapshot(
            network.toString(),
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            props.linkAddresses.map { IpSubnet(it.address, it.prefixLength) },
            props.toString(),
        ))
        updates.trySend(Unit)
    }

    suspend fun discover(): IpSubnet {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
            registered = true
            // Settle initial callbacks for all LANs. Continuous churn fails closed.
            val settled = withTimeoutOrNull(1500) {
                if (withTimeoutOrNull(750) { updates.receive() } != null) {
                    while (withTimeoutOrNull(200) { updates.receive() } != null) { /* drain */ }
                }
                true
            } ?: false
            if (!settled) throw LanScanException(LanScanFailure.NETWORK_UNAVAILABLE)
            return tracker.pin(cm.activeNetwork?.toString())
        } catch (_: SecurityException) {
            throw LanScanException(LanScanFailure.NETWORK_UNAVAILABLE)
        }
    }

    override fun close() {
        if (registered) cm.unregisterNetworkCallback(callback)
        updates.close()
    }
}
