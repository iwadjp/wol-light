package com.iwadjp.wollight.model

data class Device(
    val id: Long = 0,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val broadcastAddress: String = "255.255.255.255",
    val port: Int = 9,
    val isOnline: Boolean = false
)
