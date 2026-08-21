package com.iwadjp.wollight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iwadjp.wollight.data.repository.DeviceRepository
import com.iwadjp.wollight.domain.usecase.LanScanState
import com.iwadjp.wollight.domain.usecase.LanScanUseCase
import com.iwadjp.wollight.model.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val lanScanUseCase: LanScanUseCase,
    private val repository: DeviceRepository
) : ViewModel() {

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<Device>>(emptyList())
    val scannedDevices: StateFlow<List<Device>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        scanJob?.cancel()
        _progress.value = 0
        _scannedDevices.value = emptyList()
        _isScanning.value = true
        scanJob = viewModelScope.launch {
            lanScanUseCase().collect { state ->
                when (state) {
                    is LanScanState.Progress -> _progress.value = state.count
                    is LanScanState.Result -> {
                        _scannedDevices.value = state.devices
                        _isScanning.value = false
                    }
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    fun registerDevice(device: Device, name: String) {
        viewModelScope.launch {
            repository.insert(device.copy(name = name))
        }
    }
}
