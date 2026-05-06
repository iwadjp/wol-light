package com.example.wollight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wollight.data.repository.DeviceRepository
import com.example.wollight.data.repository.OnlineStatusStore
import com.example.wollight.domain.usecase.PingUseCase
import com.example.wollight.domain.usecase.WolUseCase
import com.example.wollight.model.Device
import com.example.wollight.network.PingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val wolUseCase: WolUseCase,
    private val pingUseCase: PingUseCase,
    private val onlineStatusStore: OnlineStatusStore
) : ViewModel() {

    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _wolMessage = MutableStateFlow<String?>(null)
    val wolMessage: StateFlow<String?> = _wolMessage.asStateFlow()

    private val _pingResults = MutableStateFlow<List<PingResult>>(emptyList())
    val pingResults: StateFlow<List<PingResult>> = _pingResults.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

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
            _pingResults.value = emptyList()
            _isPinging.value = true
            pingUseCase(current)
                .catch { }
                .collect { result ->
                    _pingResults.update { it + result }
                    onlineStatusStore.updateWithResult(current.id, result.reachable)
                }
            _isPinging.value = false
        }
    }

    fun updateDevice(updated: Device) {
        viewModelScope.launch {
            repository.update(updated)
            _device.value = updated
        }
    }

    fun clearWolMessage() { _wolMessage.value = null }
}
