package com.iwadjp.wollight.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress
import javax.inject.Inject

data class PingResult(val reachable: Boolean, val elapsedMs: Long, val attemptNumber: Int)

class PingSender @Inject constructor() {

    fun sendPing(ipAddress: String, times: Int = 10, intervalMs: Long = 1000): Flow<PingResult> = flow {
        repeat(times) { attempt ->
            val result = runCatching {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(ipAddress).isReachable(2000)
                val elapsed = System.currentTimeMillis() - start
                PingResult(reachable, elapsed, attempt + 1)
            }.getOrElse { PingResult(false, 0L, attempt + 1) }
            emit(result)
            if (attempt < times - 1) {
                delay(intervalMs)
            }
        }
    }.flowOn(Dispatchers.IO)
}
