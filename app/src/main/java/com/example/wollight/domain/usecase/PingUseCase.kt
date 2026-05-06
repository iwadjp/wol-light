package com.example.wollight.domain.usecase

import com.example.wollight.model.Device
import com.example.wollight.network.PingResult
import com.example.wollight.network.PingSender
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PingUseCase @Inject constructor(private val pingSender: PingSender) {

    operator fun invoke(device: Device): Flow<PingResult> =
        pingSender.sendPing(device.ipAddress)
}
