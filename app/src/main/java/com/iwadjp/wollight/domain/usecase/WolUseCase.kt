package com.iwadjp.wollight.domain.usecase

import com.iwadjp.wollight.model.Device
import com.iwadjp.wollight.network.WolSender
import javax.inject.Inject

class WolUseCase @Inject constructor(private val wolSender: WolSender) {

    suspend operator fun invoke(device: Device): Result<Unit> =
        wolSender.send(device.macAddress, device.broadcastAddress, device.port)
}
