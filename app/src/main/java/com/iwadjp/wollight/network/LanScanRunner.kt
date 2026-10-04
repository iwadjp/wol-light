package com.iwadjp.wollight.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

internal suspend fun scanLanTargets(
    subnet: IpSubnet,
    tracker: LanNetworkTracker,
    onProgress: (Int, Int) -> Unit,
    probe: suspend (String) -> Boolean,
    resolve: suspend (String) -> String,
): List<Pair<String, String>> = coroutineScope {
    val targets = scanTargets(subnet)
    val counter = AtomicInteger()
    val permits = Semaphore(32)
    tracker.checkValid()
    onProgress(0, targets.size)
    val work = async {
        targets.map { ip ->
            async {
                permits.withPermit {
                    coroutineContext.ensureActive()
                    tracker.checkValid()
                    val alive = probe(ip)
                    coroutineContext.ensureActive()
                    tracker.checkValid()
                    val result = if (alive) Pair(ip, resolve(ip)) else null
                    coroutineContext.ensureActive()
                    tracker.checkValid()
                    onProgress(counter.incrementAndGet(), targets.size)
                    result
                }
            }
        }.awaitAll().filterNotNull()
    }
    select {
        tracker.changed.onAwait {
            work.cancel()
            throw LanScanException(LanScanFailure.NETWORK_CHANGED)
        }
        work.onAwait { results ->
            tracker.checkValid()
            results
        }
    }
}
