package com.example.wollight.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineStatusStore @Inject constructor() {
    private val _status = MutableStateFlow<Map<Long, Boolean?>>(emptyMap())
    val status: StateFlow<Map<Long, Boolean?>> = _status.asStateFlow()

    fun update(deviceId: Long, isOnline: Boolean) {
        _status.update { it + (deviceId to isOnline) }
    }
}
