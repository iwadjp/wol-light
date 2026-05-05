package com.example.wollight.data.repository

import com.example.wollight.data.db.DeviceDao
import com.example.wollight.data.db.DeviceEntity
import com.example.wollight.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DeviceRepository @Inject constructor(private val dao: DeviceDao) {

    fun getAll(): Flow<List<Device>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun insert(device: Device) = dao.insert(device.toEntity())

    suspend fun update(device: Device) = dao.update(device.toEntity())

    suspend fun delete(device: Device) = dao.delete(device.toEntity())

    private fun DeviceEntity.toDomain() = Device(
        id = id,
        name = name,
        ipAddress = ipAddress,
        macAddress = macAddress,
        broadcastAddress = broadcastAddress,
        port = port
    )

    private fun Device.toEntity() = DeviceEntity(
        id = id,
        name = name,
        ipAddress = ipAddress,
        macAddress = macAddress,
        broadcastAddress = broadcastAddress,
        port = port
    )
}
