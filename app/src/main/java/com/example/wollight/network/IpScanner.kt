package com.example.wollight.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class IpScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val netBiosResolver: NetBiosResolver,
    private val mdnsResolver: MdnsResolver,
) {

    suspend fun scan(onProgress: (Int) -> Unit): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val prefix = getSubnetPrefix() ?: return@withContext emptyList()
        val counter = AtomicInteger(0)
        coroutineScope {
            (1..254).map { i ->
                async {
                    val ip = "$prefix.$i"
                    val alive = runCatching { InetAddress.getByName(ip).isReachable(300) }
                        .getOrDefault(false)
                    onProgress(counter.incrementAndGet())
                    if (alive) {
                        val name = resolveHostname(ip)
                        Pair(ip, name)
                    } else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun resolveHostname(ip: String): String = coroutineScope {
        val netbios = async(Dispatchers.IO) { netBiosResolver.resolve(ip) }
        val mdns = async(Dispatchers.IO) { mdnsResolver.resolve(ip) }
        netbios.await() ?: mdns.await() ?: ip
    }

    private fun getSubnetPrefix(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSubnetPrefixApi31()
        } else {
            getSubnetPrefixLegacy()
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getSubnetPrefixApi31(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val props = cm.getLinkProperties(cm.activeNetwork ?: return null) ?: return null
        val addr = props.linkAddresses
            .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address?.hostAddress ?: return null
        return addr.substringBeforeLast(".")
    }

    @Suppress("DEPRECATION")
    private fun getSubnetPrefixLegacy(): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wm.connectionInfo.ipAddress
        if (ipInt == 0) return null
        return Formatter.formatIpAddress(ipInt).substringBeforeLast(".")
    }
}
