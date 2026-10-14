package com.iwadjp.wollight.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * WoL送信後の起動監視の時間設定。試行回数は固定せず、締切(DEADLINE_MS)から導出する。
 */
object BootMonitorConfig {
    const val DEADLINE_MS = 60_000L
    const val PERIOD_MS = 2_000L
    const val TIMEOUT_MS = 1_000L

    /** UIのセル数（試行履歴の表示上限）。判定ロジックは回数に依存しない。 */
    const val DISPLAY_CELLS = (DEADLINE_MS / PERIOD_MS).toInt()
}

enum class BootMonitorOutcome { Responded, TimedOut }

/**
 * 最初の応答または締切まで、約[periodMs]周期でPingを繰り返す。
 *
 * - 締切を超える新しいPingは開始しない。残り時間が[timeoutMs]より短い場合はタイムアウトを残り時間に切り詰める。
 * - 経過時間は[now]（本番では SystemClock.elapsedRealtime）の差分で測る。
 * - キャンセルは[kotlinx.coroutines.CancellationException]としてそのまま伝播する（通信失敗として扱わない）。
 */
suspend fun runBootMonitor(
    startedAt: Long,
    now: () -> Long,
    pingOnce: suspend (timeoutMs: Long, attemptNumber: Int) -> PingResult,
    onAttempt: (result: PingResult, elapsedMs: Long) -> Unit,
    deadlineMs: Long = BootMonitorConfig.DEADLINE_MS,
    periodMs: Long = BootMonitorConfig.PERIOD_MS,
    timeoutMs: Long = BootMonitorConfig.TIMEOUT_MS,
    wait: suspend (Long) -> Unit = { delay(it) }
): BootMonitorOutcome {
    var attempt = 1
    while (true) {
        coroutineContext.ensureActive()
        val remainingBefore = deadlineMs - (now() - startedAt)
        if (remainingBefore <= 0L) return BootMonitorOutcome.TimedOut

        val cycleStart = now()
        val result = pingOnce(minOf(timeoutMs, remainingBefore), attempt)
        coroutineContext.ensureActive()
        val elapsed = now() - startedAt
        onAttempt(result, elapsed)
        if (result.reachable) return BootMonitorOutcome.Responded

        val remainingAfter = deadlineMs - elapsed
        if (remainingAfter <= 0L) return BootMonitorOutcome.TimedOut
        val pause = minOf(periodMs - (now() - cycleStart), remainingAfter)
        if (pause > 0L) wait(pause)
        attempt++
    }
}
