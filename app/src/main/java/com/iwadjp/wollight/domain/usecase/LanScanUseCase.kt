package com.iwadjp.wollight.domain.usecase

import com.iwadjp.wollight.model.Device
import com.iwadjp.wollight.network.ArpResolver
import com.iwadjp.wollight.network.IpScanner
import com.iwadjp.wollight.network.LanScanException
import com.iwadjp.wollight.network.LanScanFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

sealed class LanScanState {
    data class Progress(val count: Int, val total: Int) : LanScanState()
    data class Error(val reason: LanScanFailure) : LanScanState()
    data class Result(val devices: List<Device>) : LanScanState()
}

class LanScanUseCase @Inject constructor(
    private val ipScanner: IpScanner,
    private val arpResolver: ArpResolver
) {
    operator fun invoke(): Flow<LanScanState> = channelFlow {
        val aliveHosts = try {
            ipScanner.scan { count, total ->
                trySend(LanScanState.Progress(count, total))
            }
        } catch (error: LanScanException) {
            send(LanScanState.Error(error.reason))
            return@channelFlow
        }
        val devices = aliveHosts.map { (ip, name) ->
            Device(
                name = name,
                ipAddress = ip,
                macAddress = arpResolver.resolve(ip)
            )
        }
        send(LanScanState.Result(devices))
    }
}
