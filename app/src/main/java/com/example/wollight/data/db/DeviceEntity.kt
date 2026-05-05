package com.example.wollight.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String = "255.255.255.255",
    val port: Int = 9
)
