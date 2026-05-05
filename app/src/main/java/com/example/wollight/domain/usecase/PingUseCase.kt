package com.example.wollight.domain.usecase

import com.example.wollight.model.Device
import com.example.wollight.network.PingResult
import com.example.wollight.network.PingSender
import javax.inject.Inject

class PingUseCase @Inject constructor(private val pingSender: PingSender) {

    suspend operator fun invoke(device: Device): Result<PingResult> =
        pingSender.ping(device.ipAddress)
}
