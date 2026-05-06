package com.example.wollight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wollight.data.repository.DeviceRepository
import com.example.wollight.data.repository.OnlineStatusStore
import com.example.wollight.model.Device
import com.example.wollight.network.PingSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val onlineStatusStore: OnlineStatusStore,
    private val pingSender: PingSender
) : ViewModel() {

    val devices: StateFlow<List<Device>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val onlineStatus: StateFlow<Map<Long, Boolean?>> = onlineStatusStore.status

    private var pingJob: Job? = null

    init {
        viewModelScope.launch {
            val initialDevices = repository.getAll().first()
            startPingAll(initialDevices)
        }
    }

    fun refreshOnlineStatus() {
        val currentDevices = devices.value
        if (currentDevices.isNotEmpty()) {
            startPingAll(currentDevices)
        }
    }

    private fun startPingAll(deviceList: List<Device>) {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            deviceList.forEach { device ->
                launch(Dispatchers.IO) {
                    pingSender.sendPing(device.ipAddress, times = 2)
                        .collect { result ->
                            onlineStatusStore.updateWithResult(device.id, result.reachable)
                        }
                }
            }
        }
    }

    fun delete(device: Device) {
        viewModelScope.launch { repository.delete(device) }
    }
}
