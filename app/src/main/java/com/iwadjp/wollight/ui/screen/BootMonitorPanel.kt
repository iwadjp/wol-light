package com.iwadjp.wollight.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwadjp.wollight.R
import com.iwadjp.wollight.network.BootMonitorConfig
import com.iwadjp.wollight.network.PingResult
import com.iwadjp.wollight.ui.viewmodel.BootMonitorState
import kotlinx.coroutines.delay

private enum class CellKind { Ok, Ng, Current, Todo, Skipped }

private fun cellKind(
    index: Int,
    attempts: List<PingResult>,
    running: Boolean
): CellKind = when {
    index < attempts.size -> if (attempts[index].reachable) CellKind.Ok else CellKind.Ng
    running && index == attempts.size -> CellKind.Current
    running -> CellKind.Todo
    else -> CellKind.Skipped
}

/** WoL送信後の起動監視パネル（Running / Responded / TimedOut）。それ以外の状態では何も描画しない。 */
@Composable
fun BootMonitorPanel(
    state: BootMonitorState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deadlineMs = BootMonitorConfig.DEADLINE_MS
    val deadlineSec = (deadlineMs / 1000).toInt()

    var tickElapsed by remember { mutableLongStateOf(0L) }
    if (state is BootMonitorState.Running) {
        // 表示用の経過時間のみ。判定はVM側のelapsedRealtime締切で行う。
        LaunchedEffect(state.startedAt) {
            while (true) {
                tickElapsed = (SystemClock.elapsedRealtime() - state.startedAt)
                    .coerceIn(0L, deadlineMs)
                delay(250)
            }
        }
    }

    val attempts: List<PingResult>
    val elapsedMs: Long
    val progress: Float
    val running = state is BootMonitorState.Running
    when (state) {
        is BootMonitorState.Running -> {
            attempts = state.attempts
            elapsedMs = maxOf(tickElapsed, state.elapsedMs).coerceAtMost(deadlineMs)
            progress = elapsedMs.toFloat() / deadlineMs
        }
        is BootMonitorState.Responded -> {
            attempts = state.attempts
            elapsedMs = state.elapsedMs.coerceIn(0L, deadlineMs)
            progress = elapsedMs.toFloat() / deadlineMs // 成功時も無条件で100%にしない
        }
        is BootMonitorState.TimedOut -> {
            attempts = state.attempts
            elapsedMs = deadlineMs
            progress = 1f
        }
        else -> return
    }

    val scheme = MaterialTheme.colorScheme
    val container = when (state) {
        is BootMonitorState.Running -> scheme.secondaryContainer
        is BootMonitorState.Responded -> scheme.primaryContainer
        else -> scheme.errorContainer
    }
    val content = when (state) {
        is BootMonitorState.Running -> scheme.onSecondaryContainer
        is BootMonitorState.Responded -> scheme.onPrimaryContainer
        else -> scheme.onErrorContainer
    }
    val title = when (state) {
        is BootMonitorState.Running -> stringResource(R.string.boot_monitor_running)
        is BootMonitorState.Responded -> stringResource(R.string.boot_monitor_responded)
        else -> stringResource(R.string.boot_monitor_timeout)
    }
    val sub = when (state) {
        is BootMonitorState.Running -> stringResource(R.string.boot_monitor_running_sub, deadlineSec)
        is BootMonitorState.Responded -> stringResource(R.string.boot_monitor_responded_sub)
        else -> stringResource(R.string.boot_monitor_timeout_sub, deadlineSec)
    }
    val foot = when (state) {
        is BootMonitorState.Running ->
            stringResource(R.string.boot_monitor_foot_running, attempts.size + 1)
        is BootMonitorState.Responded -> {
            val last = attempts.lastOrNull()
            stringResource(
                R.string.boot_monitor_foot_responded,
                last?.attemptNumber ?: attempts.size,
                last?.elapsedMs ?: 0L
            )
        }
        else -> stringResource(R.string.boot_monitor_foot_timeout, attempts.size)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(container, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = content, fontSize = 15.sp)
                    Text(sub, color = content, fontSize = 11.sp)
                }
                Text(
                    stringResource(R.string.boot_monitor_elapsed, (elapsedMs / 1000).toInt()),
                    color = content,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {} // 右端の既定ドット（stop indicator）は意図しない描画のため非表示
            )

            val cells = BootMonitorConfig.DISPLAY_CELLS
            val perRow = 10
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (rowStart in 0 until cells step perRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (rowStart + 1).toString(),
                            modifier = Modifier.width(22.dp),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        for (i in rowStart until minOf(rowStart + perRow, cells)) {
                            StripCell(cellKind(i, attempts, running), Modifier.weight(1f))
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(CellKind.Ok, stringResource(R.string.boot_monitor_legend_ok))
                LegendItem(CellKind.Ng, stringResource(R.string.boot_monitor_legend_ng))
                LegendItem(
                    if (running) CellKind.Todo else CellKind.Skipped,
                    stringResource(R.string.boot_monitor_legend_todo)
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(foot, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (state is BootMonitorState.Running) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.boot_monitor_cancel))
                    }
                }
            }
            when (state) {
                is BootMonitorState.Responded ->
                    Text(stringResource(R.string.boot_monitor_note_responded), fontSize = 11.sp)
                is BootMonitorState.TimedOut ->
                    Text(stringResource(R.string.boot_monitor_note_timeout), fontSize = 11.sp)
                else -> Unit
            }
        }
    }
}

@Composable
private fun LegendItem(kind: CellKind, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendMarker(kind)
        Text(label, fontSize = 10.sp)
    }
}

/** 凡例用の小型マーカー。Pingセルと同じ色・記号で、16dp枠の中央に記号を収める。 */
@Composable
private fun LegendMarker(kind: CellKind) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)
    val box = Modifier.size(16.dp)
    val glyphStyle = TextStyle(
        fontSize = 10.sp,
        lineHeight = 10.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
    when (kind) {
        CellKind.Ok -> Box(box.background(scheme.primary, shape), contentAlignment = Alignment.Center) {
            Text("✓", color = scheme.onPrimary, style = glyphStyle)
        }
        CellKind.Ng -> Box(box.background(scheme.errorContainer, shape), contentAlignment = Alignment.Center) {
            Text("×", color = scheme.error, style = glyphStyle)
        }
        else -> StripCell(kind, box)
    }
}

@Composable
private fun StripCell(kind: CellKind, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val base = modifier.aspectRatio(1f)
    val shape = RoundedCornerShape(4.dp)
    when (kind) {
        CellKind.Ok -> Box(base.background(scheme.primary, shape), contentAlignment = Alignment.Center) {
            Text("✓", color = scheme.onPrimary, fontSize = 11.sp)
        }
        CellKind.Ng -> Box(base.background(scheme.errorContainer, shape), contentAlignment = Alignment.Center) {
            Text("×", color = scheme.error, fontSize = 11.sp)
        }
        CellKind.Current -> Box(base.border(BorderStroke(2.dp, scheme.primary), shape))
        CellKind.Todo -> Box(base.border(BorderStroke(1.dp, scheme.outline.copy(alpha = 0.45f)), shape))
        CellKind.Skipped -> Box(base.border(BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)), shape))
    }
}
