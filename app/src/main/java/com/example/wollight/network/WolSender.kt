package com.example.wollight.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject

class WolSender @Inject constructor() {

    suspend fun send(macAddress: String, broadcastAddress: String, port: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val packet = buildMagicPacket(macAddress)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val address = InetAddress.getByName(broadcastAddress)
                    socket.send(DatagramPacket(packet, packet.size, address, port))
                }
            }
        }

    private fun buildMagicPacket(macAddress: String): ByteArray {
        require(isValidMac(macAddress)) { "Invalid MAC address: $macAddress" }
        val macBytes = macAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { macBytes[it % 6] }
    }

    fun isValidMac(macAddress: String): Boolean =
        macAddress.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))
}
