package com.iwadjp.wollight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iwadjp.wollight.BuildConfig
import com.iwadjp.wollight.data.repository.DeviceRepository
import com.iwadjp.wollight.data.repository.OnlineStatusStore
import com.iwadjp.wollight.debug.StaleIpSimulator
import com.iwadjp.wollight.domain.usecase.FindIpDriftCandidateUseCase
import com.iwadjp.wollight.domain.usecase.IpDriftCandidate
import com.iwadjp.wollight.domain.usecase.LanScanState
import com.iwadjp.wollight.domain.usecase.LanScanUseCase
import com.iwadjp.wollight.domain.usecase.PingUseCase
import com.iwadjp.wollight.domain.usecase.WolUseCase
import com.iwadjp.wollight.model.Device
import com.iwadjp.wollight.network.PingResult
import com.iwadjp.wollight.util.MacAddressNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IpDriftCandidateUi(val deviceId: Long, val oldIpAddress: String, val newIpAddress: String)

/**
 * DEBUG-only UI state for simulating a stale registered IP address during manual
 * verification. Never persisted; resets whenever the ViewModel is recreated
 * (screen leave / app restart).
 */
sealed class DebugStaleIpState {
    data object Off : DebugStaleIpState()
    data object Generating : DebugStaleIpState()
    data class On(val staleIpAddress: String) : DebugStaleIpState()
    data object Unavailable : DebugStaleIpState()
}

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val wolUseCase: WolUseCase,
    private val pingUseCase: PingUseCase,
    private val onlineStatusStore: OnlineStatusStore,
    private val lanScanUseCase: LanScanUseCase,
    private val findIpDriftCandidateUseCase: FindIpDriftCandidateUseCase
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

    private val _isRecoveringIp = MutableStateFlow(false)
    val isRecoveringIp: StateFlow<Boolean> = _isRecoveringIp.asStateFlow()

    private val _ipDriftCandidate = MutableStateFlow<IpDriftCandidateUi?>(null)
    val ipDriftCandidate: StateFlow<IpDriftCandidateUi?> = _ipDriftCandidate.asStateFlow()

    private val _debugStaleIpState = MutableStateFlow<DebugStaleIpState>(DebugStaleIpState.Off)
    val debugStaleIpState: StateFlow<DebugStaleIpState> = _debugStaleIpState.asStateFlow()

    private var loadJob: Job? = null
    private var pingJob: Job? = null
    private var recoveryScanJob: Job? = null
    private var debugStaleIpJob: Job? = null

    fun loadDevice(deviceId: Long) {
        if (_device.value?.id == deviceId) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.getById(deviceId).collect { device ->
                _device.value = device
            }
        }
    }

    fun sendWol() {
        val current = _device.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = wolUseCase(current)
            result.onSuccess { _wolMessage.value = "WoL送信完了" }
                .onFailure { _wolMessage.value = "送信失敗: ${it.message}" }
            _isLoading.value = false

            // Wake送信自体の成否と、送信後の起動確認Pingの成否は別問題として扱う。
            // magic packet送信が成功した場合のみ、起動確認としてPingを実行する。
            if (result.isSuccess) {
                val pingTarget = effectiveDeviceForPing(current)
                pingJob?.cancel()
                pingJob = viewModelScope.launch {
                    val anySuccess = executePingBurst(pingTarget)
                    if (!anySuccess) attemptIpDriftRecovery(pingTarget)
                }
            }
        }
    }

    fun sendPing() {
        val current = _device.value ?: return
        val pingTarget = effectiveDeviceForPing(current)
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            val anySuccess = executePingBurst(pingTarget)
            if (!anySuccess) attemptIpDriftRecovery(pingTarget)
        }
    }

    /**
     * DEBUG buildでstale IPシミュレーションが有効な場合のみ、Ping/recovery flowが参照する
     * IPを一時的な未使用IPへ差し替えたin-memoryコピーを返す。永続データは一切変更しない。
     */
    private fun effectiveDeviceForPing(device: Device): Device {
        val state = _debugStaleIpState.value
        return if (BuildConfig.DEBUG && state is DebugStaleIpState.On) {
            device.copy(ipAddress = state.staleIpAddress)
        } else {
            device
        }
    }

    /**
     * DEBUG-only: 実機確認用に、登録済みdeviceの実IPと同一/24内の未使用と思われるIPを
     * 生成し、以降のPing/recovery flowにだけ一時的に適用する。DeviceRepository.update()は
     * 呼ばない。安全な候補が決められない場合は無効状態のままにする。
     */
    fun setDebugStaleIpSimulation(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        debugStaleIpJob?.cancel()
        if (!enabled) {
            _debugStaleIpState.value = DebugStaleIpState.Off
            return
        }
        val current = _device.value ?: return
        _debugStaleIpState.value = DebugStaleIpState.Generating
        debugStaleIpJob = viewModelScope.launch {
            val staleIp = StaleIpSimulator.findStaleIp(current.ipAddress)
            _debugStaleIpState.value =
                if (staleIp != null) DebugStaleIpState.On(staleIp) else DebugStaleIpState.Unavailable
        }
    }

    private suspend fun executePingBurst(device: Device): Boolean {
        _pingResults.value = emptyList()
        _isPinging.value = true
        var anySuccess = false
        pingUseCase(device)
            .catch { }
            .collect { result ->
                _pingResults.update { it + result }
                onlineStatusStore.updateWithResult(device.id, result.reachable)
                if (result.reachable) anySuccess = true
            }
        _isPinging.value = false
        return anySuccess
    }

    /**
     * Ping失敗後に一度だけLANスキャンを行い、登録済みMACが別IPで見つかった場合に
     * ユーザー確認用の候補を提示する。scanが既に進行中の場合は二重起動しない。
     */
    private fun attemptIpDriftRecovery(device: Device) {
        if (_isRecoveringIp.value) return
        if (MacAddressNormalizer.normalize(device.macAddress) == null) return
        recoveryScanJob?.cancel()
        recoveryScanJob = viewModelScope.launch {
            _isRecoveringIp.value = true
            try {
                var scanDevices: List<Device> = emptyList()
                lanScanUseCase().collect { state ->
                    if (state is LanScanState.Result) scanDevices = state.devices
                }
                val candidate = findIpDriftCandidateUseCase(device, scanDevices)
                if (candidate is IpDriftCandidate.Found) {
                    _ipDriftCandidate.value = IpDriftCandidateUi(
                        deviceId = device.id,
                        oldIpAddress = device.ipAddress,
                        newIpAddress = candidate.newIpAddress
                    )
                }
            } finally {
                _isRecoveringIp.value = false
            }
        }
    }

    fun confirmIpDriftUpdate() {
        val candidate = _ipDriftCandidate.value ?: return
        val current = _device.value ?: return
        _ipDriftCandidate.value = null
        if (current.id != candidate.deviceId) return

        viewModelScope.launch {
            val updated = current.copy(ipAddress = candidate.newIpAddress)
            val updateResult = runCatching { repository.update(updated) }
            updateResult.onFailure { _wolMessage.value = "IPアドレスの更新に失敗しました: ${it.message}" }
            if (updateResult.isSuccess) {
                pingJob?.cancel()
                pingJob = viewModelScope.launch { executePingBurst(updated) }
            }
        }
    }

    fun dismissIpDriftCandidate() {
        _ipDriftCandidate.value = null
    }

    fun updateDevice(updated: Device) {
        viewModelScope.launch {
            repository.update(updated)
        }
    }

    fun clearWolMessage() { _wolMessage.value = null }

    override fun onCleared() {
        super.onCleared()
        pingJob?.cancel()
        loadJob?.cancel()
        recoveryScanJob?.cancel()
        debugStaleIpJob?.cancel()
    }
}
