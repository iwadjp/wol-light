package com.example.wollight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wollight.data.repository.DeviceRepository
import com.example.wollight.data.repository.OnlineStatusStore
import com.example.wollight.model.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val onlineStatusStore: OnlineStatusStore
) : ViewModel() {

    val devices: StateFlow<List<Device>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val onlineStatus: StateFlow<Map<Long, Boolean?>> = onlineStatusStore.status

    fun updateOnlineStatus(deviceId: Long, isOnline: Boolean) {
        onlineStatusStore.update(deviceId, isOnline)
    }

    fun delete(device: Device) {
        viewModelScope.launch { repository.delete(device) }
    }
}
