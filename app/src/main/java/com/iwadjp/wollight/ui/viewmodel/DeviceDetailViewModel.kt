package com.iwadjp.wollight.ui.viewmodel

import android.os.SystemClock
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
import com.iwadjp.wollight.network.BootMonitorConfig
import com.iwadjp.wollight.network.BootMonitorOutcome
import com.iwadjp.wollight.network.PingResult
import com.iwadjp.wollight.network.runBootMonitor
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

/**
 * WoL送信後の起動監視の状態。手動Ping(pingResults/isPinging)とは独立。
 * キャンセル・WoL送信失敗はIdleへ戻る（失敗はSnackbarで通知）。
 */
sealed class BootMonitorState {
    data object Idle : BootMonitorState()
    /** WoL送信中。Ping監視はまだ始まっていない。 */
    data object Starting : BootMonitorState()
    data class Running(
        val startedAt: Long,
        val attempts: List<PingResult>,
        val elapsedMs: Long
    ) : BootMonitorState()
    data class Responded(
        val startedAt: Long,
        val attempts: List<PingResult>,
        val elapsedMs: Long
    ) : BootMonitorState()
    data class TimedOut(
        val startedAt: Long,
        val attempts: List<PingResult>,
        val elapsedMs: Long
    ) : BootMonitorState()

    val isActive: Boolean get() = this is Starting || this is Running
}

/**
 * Snackbar message shown on the detail screen. Holds the meaning only; the screen
 * resolves it to a localized string. [detail] is OS/library error text, shown as-is.
 */
sealed class DeviceDetailMessage {
    data object WolSent : DeviceDetailMessage()
    data class WolFailed(val detail: String?) : DeviceDetailMessage()
    data class IpUpdateFailed(val detail: String?) : DeviceDetailMessage()
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

    private val _wolMessage = MutableStateFlow<DeviceDetailMessage?>(null)
    val wolMessage: StateFlow<DeviceDetailMessage?> = _wolMessage.asStateFlow()

    private val _pingResults = MutableStateFlow<List<PingResult>>(emptyList())
    val pingResults: StateFlow<List<PingResult>> = _pingResults.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _bootMonitor = MutableStateFlow<BootMonitorState>(BootMonitorState.Idle)
    val bootMonitor: StateFlow<BootMonitorState> = _bootMonitor.asStateFlow()

    private val _isRecoveringIp = MutableStateFlow(false)
    val isRecoveringIp: StateFlow<Boolean> = _isRecoveringIp.asStateFlow()

    private val _ipDriftCandidate = MutableStateFlow<IpDriftCandidateUi?>(null)
    val ipDriftCandidate: StateFlow<IpDriftCandidateUi?> = _ipDriftCandidate.asStateFlow()

    private val _debugStaleIpState = MutableStateFlow<DebugStaleIpState>(DebugStaleIpState.Off)
    val debugStaleIpState: StateFlow<DebugStaleIpState> = _debugStaleIpState.asStateFlow()

    private var loadJob: Job? = null
    private var pingJob: Job? = null
    private var monitorJob: Job? = null
    /** 監視の世代。キャンセル済み/古い監視の遅延結果でUIを書き換えないための照合に使う。 */
    private var monitorRunId = 0
    private var burstGeneration = 0
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
        // 多重実行防止: 送信中・監視中は新規開始しない（UI無効化に加えVM側でも保証）。
        if (_bootMonitor.value.isActive || _isLoading.value) return
        val runId = ++monitorRunId
        _bootMonitor.value = BootMonitorState.Starting
        monitorJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = try {
                    wolUseCase(current)
                } finally {
                    _isLoading.value = false
                }
                result.onSuccess { _wolMessage.value = DeviceDetailMessage.WolSent }
                    .onFailure { _wolMessage.value = DeviceDetailMessage.WolFailed(it.message) }

                // Wake送信自体の成否と、送信後の起動確認の成否は別問題として扱う。
                // magic packet送信が成功した場合のみ、起動監視を開始する。
                if (result.isFailure) {
                    if (runId == monitorRunId) _bootMonitor.value = BootMonitorState.Idle
                    return@launch
                }
                val pingTarget = effectiveDeviceForPing(current)
                pingJob?.cancel()
                _pingResults.value = emptyList()
                _isPinging.value = false
                runBootMonitorFlow(runId, pingTarget)
            } finally {
                // キャンセル等で終端状態に到達しなかった場合は操作可能な状態へ戻す。
                if (runId == monitorRunId && _bootMonitor.value.isActive) {
                    _bootMonitor.value = BootMonitorState.Idle
                }
            }
        }
    }

    private suspend fun runBootMonitorFlow(runId: Int, target: Device) {
        val startedAt = SystemClock.elapsedRealtime()
        val attempts = mutableListOf<PingResult>()
        _bootMonitor.value = BootMonitorState.Running(startedAt, emptyList(), 0L)
        val outcome = runBootMonitor(
            startedAt = startedAt,
            now = { SystemClock.elapsedRealtime() },
            pingOnce = { timeoutMs, attempt -> pingUseCase.pingOnce(target, timeoutMs, attempt) },
            onAttempt = { result, elapsedMs ->
                if (runId != monitorRunId) return@runBootMonitor
                attempts += result
                onlineStatusStore.updateWithResult(target.id, result.reachable)
                _bootMonitor.value = BootMonitorState.Running(startedAt, attempts.toList(), elapsedMs)
            }
        )
        if (runId != monitorRunId) return
        val last = _bootMonitor.value as? BootMonitorState.Running ?: return
        when (outcome) {
            BootMonitorOutcome.Responded ->
                _bootMonitor.value = BootMonitorState.Responded(startedAt, last.attempts, last.elapsedMs)
            BootMonitorOutcome.TimedOut -> {
                _bootMonitor.value = BootMonitorState.TimedOut(
                    startedAt, last.attempts, BootMonitorConfig.DEADLINE_MS
                )
                // タイムアウト時のみ既存条件でIPドリフト復旧を検討する。
                attemptIpDriftRecovery(target)
            }
        }
    }

    /** 起動監視の中止。通信失敗ではなくユーザー操作として扱い、Idleへ戻す。 */
    fun cancelBootMonitor() {
        if (_bootMonitor.value !is BootMonitorState.Running) return
        monitorRunId++ // 遅延して返るPing結果を無効化
        monitorJob?.cancel()
        _bootMonitor.value = BootMonitorState.Idle
    }

    fun sendPing() {
        val current = _device.value ?: return
        if (_bootMonitor.value.isActive) return
        // 手動Ping開始時は、前回の監視結果パネルを閉じて手動表示へ戻す。
        _bootMonitor.value = BootMonitorState.Idle
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
        val generation = ++burstGeneration
        _pingResults.value = emptyList()
        _isPinging.value = true
        var anySuccess = false
        try {
            pingUseCase(device)
                .catch { }
                .collect { result ->
                    _pingResults.update { it + result }
                    onlineStatusStore.updateWithResult(device.id, result.reachable)
                    if (result.reachable) anySuccess = true
                }
        } finally {
            // キャンセル時も固着させない。新しいburstが走っている場合は触らない。
            if (generation == burstGeneration) _isPinging.value = false
        }
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
            updateResult.onFailure { _wolMessage.value = DeviceDetailMessage.IpUpdateFailed(it.message) }
            if (updateResult.isSuccess && !_bootMonitor.value.isActive) {
                // IP更新後の再Pingは従来どおり手動と同じ10回。監視結果パネルは閉じる。
                _bootMonitor.value = BootMonitorState.Idle
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
        monitorRunId++
        monitorJob?.cancel()
        loadJob?.cancel()
        recoveryScanJob?.cancel()
        debugStaleIpJob?.cancel()
    }
}
