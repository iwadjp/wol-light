package com.example.wollight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wollight.data.repository.DeviceRepository
import com.example.wollight.model.Device
import com.example.wollight.network.PingSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val pingSender: PingSender
) : ViewModel() {

    private val _pingingIds = MutableStateFlow<Set<Long>>(emptySet())
    val pingingIds: StateFlow<Set<Long>> = _pingingIds.asStateFlow()

    private val _onlineIds = MutableStateFlow<Set<Long>>(emptySet())

    val devices: StateFlow<List<Device>> = combine(
        repository.getAll(),
        _onlineIds
    ) { list, onlineIds ->
        list.map { device -> device.copy(isOnline = device.id in onlineIds) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val initialDevices = repository.getAll().first()
            pingAll(initialDevices)
        }
    }

    private fun pingAll(deviceList: List<Device>) {
        _pingingIds.value = deviceList.map { it.id }.toSet()
        deviceList.forEach { device ->
            viewModelScope.launch(Dispatchers.IO) {
                val reachable = runCatching {
                    pingSender.sendPing(device.ipAddress, times = 1).first().reachable
                }.getOrDefault(false)
                if (reachable) {
                    _onlineIds.update { it + device.id }
                } else {
                    _onlineIds.update { it - device.id }
                }
                _pingingIds.update { it - device.id }
            }
        }
    }

    fun delete(device: Device) {
        viewModelScope.launch { repository.delete(device) }
    }
}
