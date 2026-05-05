package com.example.wollight.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

data class PingResult(val reachable: Boolean, val elapsedMs: Long)

class PingSender @Inject constructor() {

    suspend fun ping(ipAddress: String): Result<PingResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(ipAddress).isReachable(2000)
                val elapsed = System.currentTimeMillis() - start
                PingResult(reachable, elapsed)
            }
        }
}
