package com.example.wollight.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import javax.inject.Inject

class MdnsResolver @Inject constructor(@ApplicationContext private val context: Context) {

    fun resolve(ip: String): String? = runCatching {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifiManager.createMulticastLock("MdnsResolver")
        lock.acquire()
        try {
            queryMdns(ip)
        } finally {
            lock.release()
        }
    }.getOrElse { e ->
        Log.d(TAG, "mDNS resolve failed for $ip: ${e.message}")
        null
    }

    private fun queryMdns(ip: String): String? {
        val ptrName = buildPtrName(ip)
        val query = buildDnsQuery(ptrName)
        val mdnsGroup = InetAddress.getByName(MDNS_ADDRESS)

        val socket = MulticastSocket(MDNS_PORT)
        socket.soTimeout = 500
        socket.joinGroup(mdnsGroup)
        try {
            socket.send(DatagramPacket(query, query.size, mdnsGroup, MDNS_PORT))
            val buf = ByteArray(1024)
            val response = DatagramPacket(buf, buf.size)
            socket.receive(response)
            return parseLocalHostname(buf, response.length)
        } finally {
            socket.leaveGroup(mdnsGroup)
            socket.close()
        }
    }

    private fun buildPtrName(ip: String): String {
        val parts = ip.split(".")
        return "${parts[3]}.${parts[2]}.${parts[1]}.${parts[0]}.in-addr.arpa"
    }

    private fun buildDnsQuery(name: String): ByteArray {
        val buf = mutableListOf<Byte>()
        // Transaction ID
        buf.add(0x00); buf.add(0x00)
        // Flags: standard query
        buf.add(0x00); buf.add(0x00)
        // Questions: 1
        buf.add(0x00); buf.add(0x01)
        // Answer/Authority/Additional RRs: 0
        repeat(6) { buf.add(0x00) }

        // Encode name
        for (label in name.split(".")) {
            buf.add(label.length.toByte())
            label.forEach { buf.add(it.code.toByte()) }
        }
        buf.add(0x00) // end of name

        // Type: PTR (0x000C)
        buf.add(0x00); buf.add(0x0C)
        // Class: IN (0x0001)
        buf.add(0x00); buf.add(0x01)

        return buf.toByteArray()
    }

    private fun parseLocalHostname(buf: ByteArray, length: Int): String? {
        val data = buf.copyOf(length)
        val text = String(data)
        // Find .local hostname by searching for the pattern in the raw bytes
        val localIdx = text.indexOf(".local")
        if (localIdx <= 0) return null

        // Walk backwards to find the start of the hostname label
        var start = localIdx - 1
        while (start > 0 && data[start] != 0x00.toByte() && data[start] >= 0x20) {
            start--
        }
        val hostname = text.substring(start + 1, localIdx + 6).trimStart()
        return if (hostname.isNotBlank() && hostname.contains(".local")) hostname else null
    }

    companion object {
        private const val TAG = "MdnsResolver"
        private const val MDNS_ADDRESS = "224.0.0.251"
        private const val MDNS_PORT = 5353
    }
}
