package com.example.wollight.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject

class NetBiosResolver @Inject constructor() {

    fun resolve(ip: String): String? = runCatching {
        val socket = DatagramSocket()
        socket.soTimeout = 500
        try {
            val request = buildNodeStatusRequest()
            val target = InetAddress.getByName(ip)
            socket.send(DatagramPacket(request, request.size, target, 137))

            val buf = ByteArray(1024)
            val response = DatagramPacket(buf, buf.size)
            socket.receive(response)
            parseNodeStatusResponse(buf, response.length)
        } finally {
            socket.close()
        }
    }.getOrElse { e ->
        Log.d(TAG, "NetBIOS resolve failed for $ip: ${e.message}")
        null
    }

    private fun buildNodeStatusRequest(): ByteArray {
        val packet = ByteArray(50)
        // Transaction ID
        packet[0] = 0x00
        packet[1] = 0x00
        // Flags: query, opcode=0, not recursive
        packet[2] = 0x00
        packet[3] = 0x00
        // Questions: 1
        packet[4] = 0x00
        packet[5] = 0x01
        // Answer/Authority/Additional RRs: 0
        // (bytes 6-11 remain 0x00)

        // Encoded "*" (wildcard) NetBIOS name: 0x20 bytes
        packet[12] = 0x20
        // "*" encoded as CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
        val encoded = "CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        for (i in encoded.indices) {
            packet[13 + i] = encoded[i].code.toByte()
        }
        packet[45] = 0x00 // end of name

        // Type: NBSTAT (0x21)
        packet[46] = 0x00
        packet[47] = 0x21
        // Class: IN (0x01)
        packet[48] = 0x00
        packet[49] = 0x01
        return packet
    }

    private fun parseNodeStatusResponse(buf: ByteArray, length: Int): String? {
        if (length < 57) return null
        val numNames = buf[56].toInt() and 0xFF
        if (numNames == 0) return null

        for (i in 0 until numNames) {
            val offset = 57 + i * 18
            if (offset + 15 >= length) break
            val flags = ((buf[offset + 16].toInt() and 0xFF) shl 8) or (buf[offset + 17].toInt() and 0xFF)
            val isGroup = (flags and 0x80) != 0
            val nameType = buf[offset + 15].toInt() and 0xFF
            // Type 0x00 = workstation/computer name (not group)
            if (!isGroup && nameType == 0x00) {
                val name = String(buf, offset, 15).trimEnd()
                if (name.isNotBlank()) return name
            }
        }
        return null
    }

    companion object {
        private const val TAG = "NetBiosResolver"
    }
}
