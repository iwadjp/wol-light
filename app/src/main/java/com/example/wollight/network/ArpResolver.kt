package com.example.wollight.network

import android.util.Log
import javax.inject.Inject

class ArpResolver @Inject constructor() {

    fun resolve(ipAddress: String): String {
        return try {
            val process = ProcessBuilder("ip", "neigh", "show").start()
            val output = process.inputStream.bufferedReader().readText()
            parseArpTable(output, ipAddress)
        } catch (e: Exception) {
            Log.w(TAG, "ARP resolution failed for $ipAddress", e)
            ""
        }
    }

    private fun parseArpTable(arpOutput: String, targetIp: String): String {
        val pattern = Regex(
            """^${Regex.escape(targetIp)}\s+.*lladdr\s+([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})""",
            RegexOption.MULTILINE
        )
        return pattern.find(arpOutput)?.groupValues?.get(1) ?: ""
    }

    companion object {
        private const val TAG = "ArpResolver"
    }
}
