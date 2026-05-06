package com.example.wollight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wollight.data.repository.DeviceRepository
import com.example.wollight.domain.usecase.PingUseCase
import com.example.wollight.domain.usecase.WolUseCase
import com.example.wollight.model.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val wolUseCase: WolUseCase,
    private val pingUseCase: PingUseCase
) : ViewModel() {

    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _wolMessage = MutableStateFlow<String?>(null)
    val wolMessage: StateFlow<String?> = _wolMessage.asStateFlow()

    private val _pingMessage = MutableStateFlow<String?>(null)
    val pingMessage: StateFlow<String?> = _pingMessage.asStateFlow()

    fun loadDevice(device: Device) {
        _device.value = device
    }

    fun sendWol() {
        val current = _device.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            wolUseCase(current)
                .onSuccess { _wolMessage.value = "WoL送信完了" }
                .onFailure { _wolMessage.value = "送信失敗: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun sendPing() {
        val current = _device.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            pingUseCase(current)
                .catch { _pingMessage.value = "Ping失敗: ${it.message}" }
                .collect { result ->
                    _pingMessage.value = if (result.reachable) {
                        "[${result.attemptNumber}] 到達可能 (${result.elapsedMs}ms)"
                    } else {
                        "[${result.attemptNumber}] 到達不可 (${result.elapsedMs}ms)"
                    }
                }
            _isLoading.value = false
        }
    }

    fun updateDevice(updated: Device) {
        viewModelScope.launch {
            repository.update(updated)
            _device.value = updated
        }
    }

    fun clearWolMessage() { _wolMessage.value = null }
    fun clearPingMessage() { _pingMessage.value = null }
}
