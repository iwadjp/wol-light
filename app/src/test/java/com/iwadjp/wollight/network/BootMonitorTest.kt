package com.iwadjp.wollight.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BootMonitorTest {

    /** 仮想時計。pingは応答なしなら指定タイムアウト分だけ時間が進む。 */
    private class Fake(private val replyOnAttempt: Int? = null, private val replyCostMs: Long = 30L) {
        var t = 1_000L
        val pingStarts = mutableListOf<Long>()
        val timeouts = mutableListOf<Long>()
        val reported = mutableListOf<Pair<PingResult, Long>>()

        val now: () -> Long = { t }
        val wait: suspend (Long) -> Unit = { t += it }
        val pingOnce: suspend (Long, Int) -> PingResult = { timeout, attempt ->
            pingStarts += t
            timeouts += timeout
            if (attempt == replyOnAttempt) {
                t += replyCostMs
                PingResult(true, replyCostMs, attempt)
            } else {
                t += timeout
                PingResult(false, timeout, attempt)
            }
        }
        val onAttempt: (PingResult, Long) -> Unit = { r, e -> reported += r to e }
    }

    @Test
    fun firstReplyEndsMonitoringWithoutFurtherPings() = runBlocking {
        val f = Fake(replyOnAttempt = 3)
        val start = f.t
        val outcome = runBootMonitor(start, f.now, f.pingOnce, f.onAttempt, wait = f.wait)
        assertEquals(BootMonitorOutcome.Responded, outcome)
        assertEquals(3, f.pingStarts.size)
        assertTrue(f.reported.last().first.reachable)
        // 周期約2秒: 1回目0ms, 2回目2000ms, 3回目4000ms
        assertEquals(listOf(0L, 2_000L, 4_000L), f.pingStarts.map { it - start })
    }

    @Test
    fun noReplyTimesOutAtDeadlineAndNeverStartsPingAfterIt() = runBlocking {
        val f = Fake(replyOnAttempt = null)
        val start = f.t
        val outcome = runBootMonitor(start, f.now, f.pingOnce, f.onAttempt, wait = f.wait)
        assertEquals(BootMonitorOutcome.TimedOut, outcome)
        assertTrue(f.pingStarts.all { it - start < BootMonitorConfig.DEADLINE_MS })
        assertTrue(f.t - start <= BootMonitorConfig.DEADLINE_MS)
        assertFalse(f.reported.any { it.first.reachable })
        // 回数は固定ではなく締切/周期から導かれる（この設定では30回）。
        assertEquals(
            (BootMonitorConfig.DEADLINE_MS / BootMonitorConfig.PERIOD_MS).toInt(),
            f.pingStarts.size
        )
    }

    @Test
    fun lastPingTimeoutIsClampedToRemainingTime() = runBlocking {
        val f = Fake(replyOnAttempt = null)
        val start = f.t
        runBootMonitor(
            start, f.now, f.pingOnce, f.onAttempt,
            deadlineMs = 2_500L, wait = f.wait
        )
        assertEquals(listOf(1_000L, 500L), f.timeouts)
        assertEquals(2_500L, f.t - start)
    }

    @Test
    fun cancellationPropagatesAndIsNotReportedAsFailure() {
        val f = Fake()
        val cancelling: suspend (Long, Int) -> PingResult = { _, _ -> throw CancellationException("user") }
        try {
            runBlocking {
                runBootMonitor(f.t, f.now, cancelling, f.onAttempt, wait = f.wait)
            }
            fail("CancellationException expected")
        } catch (e: CancellationException) {
            // expected
        }
        assertTrue(f.reported.isEmpty())
    }
}
