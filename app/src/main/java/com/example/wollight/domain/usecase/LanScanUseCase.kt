package com.example.wollight.domain.usecase

import com.example.wollight.model.Device
import com.example.wollight.network.ArpResolver
import com.example.wollight.network.IpScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject

sealed class LanScanState {
    data class Progress(val count: Int) : LanScanState()
    data class Result(val devices: List<Device>) : LanScanState()
}

class LanScanUseCase @Inject constructor(
    private val ipScanner: IpScanner,
    private val arpResolver: ArpResolver
) {
    operator fun invoke(): Flow<LanScanState> = channelFlow {
        val aliveIps = ipScanner.scan { count ->
            trySend(LanScanState.Progress(count))
        }
        val devices = aliveIps.map { ip ->
            Device(
                name = ip,
                ipAddress = ip,
                macAddress = arpResolver.resolve(ip)
            )
        }
        send(LanScanState.Result(devices))
    }
}
