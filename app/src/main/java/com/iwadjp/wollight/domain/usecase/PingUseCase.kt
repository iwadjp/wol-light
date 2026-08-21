package com.iwadjp.wollight.domain.usecase

import com.iwadjp.wollight.model.Device
import com.iwadjp.wollight.network.PingResult
import com.iwadjp.wollight.network.PingSender
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PingUseCase @Inject constructor(private val pingSender: PingSender) {

    operator fun invoke(device: Device): Flow<PingResult> =
        pingSender.sendPing(device.ipAddress)
}
