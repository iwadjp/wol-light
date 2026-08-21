package com.iwadjp.wollight.data.repository

import com.iwadjp.wollight.data.db.DeviceDao
import com.iwadjp.wollight.data.db.DeviceEntity
import com.iwadjp.wollight.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DeviceRepository @Inject constructor(private val dao: DeviceDao) {

    fun getAll(): Flow<List<Device>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    fun getById(id: Long): Flow<Device?> = dao.getById(id).map { it?.toDomain() }

    suspend fun insert(device: Device) = dao.insert(device.toEntity())

    suspend fun update(device: Device) = dao.update(device.toEntity())

    suspend fun delete(device: Device) = dao.delete(device.toEntity())

    private fun DeviceEntity.toDomain() = Device(
        id = id,
        name = name,
        ipAddress = ipAddress,
        macAddress = macAddress,
        broadcastAddress = broadcastAddress,
        port = port,
        isOnline = false
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
