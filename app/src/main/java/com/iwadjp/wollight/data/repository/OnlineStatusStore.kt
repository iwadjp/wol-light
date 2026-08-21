package com.iwadjp.wollight.data.repository

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

    private val _lastResult = MutableStateFlow<Map<Long, Boolean?>>(emptyMap())

    fun updateWithResult(deviceId: Long, result: Boolean) {
        val last = _lastResult.value[deviceId]
        val current = _status.value[deviceId]
        if (last == result && current != result) {
            _status.update { it + (deviceId to result) }
        }
        _lastResult.update { it + (deviceId to result) }
    }
}
