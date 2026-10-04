package com.iwadjp.wollight.network

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

class IpScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val netBiosResolver: NetBiosResolver,
    private val mdnsResolver: MdnsResolver,
) {
    suspend fun scan(onProgress: (Int, Int) -> Unit): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val monitor = newMonitor()
        try {
            scanLanTargets(monitor.discover(), monitor.tracker, onProgress,
                probe = { ip -> runCatching { InetAddress.getByName(ip).isReachable(300) }.getOrDefault(false) },
                resolve = { ip -> resolveHostname(ip) })
        } finally {
            monitor.close()
        }
    }

    private suspend fun resolveHostname(ip: String): String = coroutineScope {
        val netbios = async(Dispatchers.IO) { netBiosResolver.resolve(ip) }
        val mdns = async(Dispatchers.IO) { mdnsResolver.resolve(ip) }
        netbios.await() ?: mdns.await() ?: ip
    }

    private fun newMonitor(): LanNetworkMonitor {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: throw LanScanException(LanScanFailure.NETWORK_UNAVAILABLE)
        return LanNetworkMonitor(cm)
    }

    internal suspend fun currentLanSubnet(): IpSubnet {
        val monitor = newMonitor()
        return try { monitor.discover() } finally { monitor.close() }
    }
}
