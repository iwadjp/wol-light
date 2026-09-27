package com.iwadjp.wollight.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iwadjp.wollight.BuildConfig
import com.iwadjp.wollight.R
import com.iwadjp.wollight.model.Device
import com.iwadjp.wollight.ui.viewmodel.DebugStaleIpState
import com.iwadjp.wollight.ui.viewmodel.DeviceDetailMessage
import com.iwadjp.wollight.ui.viewmodel.DeviceDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: Long,
    onNavigateBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(deviceId) { viewModel.loadDevice(deviceId) }

    val currentDevice by viewModel.device.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val wolMessage by viewModel.wolMessage.collectAsState()
    val pingResults by viewModel.pingResults.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()
    val isRecoveringIp by viewModel.isRecoveringIp.collectAsState()
    val ipDriftCandidate by viewModel.ipDriftCandidate.collectAsState()
    val debugStaleIpState by viewModel.debugStaleIpState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(wolMessage) {
        wolMessage?.let {
            val text = when (it) {
                DeviceDetailMessage.WolSent -> resources.getString(R.string.message_wol_sent)
                is DeviceDetailMessage.WolFailed ->
                    resources.getString(R.string.message_wol_failed, it.detail.toString())
                is DeviceDetailMessage.IpUpdateFailed ->
                    resources.getString(R.string.message_ip_update_failed, it.detail.toString())
            }
            snackbarHostState.showSnackbar(text)
            viewModel.clearWolMessage()
        }
    }

    if (currentDevice == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.detail_loading)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val displayed = currentDevice!!

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editMac by remember { mutableStateOf("") }
    var editBroadcast by remember { mutableStateOf("") }
    var editPort by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayed.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editName = displayed.name
                        editMac = displayed.macAddress
                        editBroadcast = displayed.broadcastAddress
                        editPort = displayed.port.toString()
                        showEditDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.detail_edit))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoRow(stringResource(R.string.detail_label_name), displayed.name)
                    InfoRow(stringResource(R.string.detail_label_ip), displayed.ipAddress)
                    InfoRow(
                        stringResource(R.string.detail_label_mac),
                        displayed.macAddress.ifEmpty { stringResource(R.string.detail_mac_not_set) }
                    )
                    InfoRow(stringResource(R.string.detail_label_broadcast), displayed.broadcastAddress)
                    InfoRow(stringResource(R.string.detail_label_port), displayed.port.toString())
                }
            }

            if (displayed.macAddress.isEmpty()) {
                Text(stringResource(R.string.detail_mac_missing_hint))
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.sendWol() },
                    enabled = !isLoading && displayed.macAddress.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.detail_send_wol))
                }
                OutlinedButton(
                    onClick = { viewModel.sendPing() },
                    enabled = !isPinging,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(if (isPinging) R.string.detail_pinging else R.string.detail_ping))
                }
            }

            if (BuildConfig.DEBUG) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = debugStaleIpState is DebugStaleIpState.On ||
                                    debugStaleIpState is DebugStaleIpState.Generating,
                                onCheckedChange = { viewModel.setDebugStaleIpSimulation(it) }
                            )
                            Text(stringResource(R.string.detail_debug_stale_ip_toggle), fontSize = 12.sp)
                        }
                        when (val state = debugStaleIpState) {
                            is DebugStaleIpState.On -> Text(
                                stringResource(R.string.detail_debug_stale_ip_active, state.staleIpAddress),
                                fontSize = 11.sp
                            )
                            DebugStaleIpState.Generating -> Text(
                                stringResource(R.string.detail_debug_stale_ip_generating),
                                fontSize = 11.sp
                            )
                            DebugStaleIpState.Unavailable -> Text(
                                stringResource(R.string.detail_debug_stale_ip_unavailable),
                                fontSize = 11.sp
                            )
                            DebugStaleIpState.Off -> Unit
                        }
                    }
                }
            }

            if (isRecoveringIp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                    Text(stringResource(R.string.detail_recovering_ip), fontSize = 13.sp)
                }
            }

            if (pingResults.isNotEmpty() || isPinging) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val pairs = pingResults.chunked(2)
                        pairs.forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEach { result ->
                                    val icon = if (result.reachable) "✅" else "❌"
                                    val label = if (result.reachable) "${result.elapsedMs}ms" else stringResource(R.string.detail_ping_failed)
                                    Text(
                                        text = "$icon ${result.attemptNumber}: $label",
                                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (!isPinging && pingResults.size == 10) {
                            val successCount = pingResults.count { it.reachable }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            val summary = if (successCount > 0) {
                                val avgMs = pingResults.filter { it.reachable }.map { it.elapsedMs }.average().toLong()
                                stringResource(R.string.detail_ping_summary, successCount, avgMs)
                            } else {
                                stringResource(R.string.detail_ping_summary_none)
                            }
                            Text(summary, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    ipDriftCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissIpDriftCandidate() },
            title = { Text(stringResource(R.string.ip_drift_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ip_drift_message,
                        candidate.oldIpAddress,
                        candidate.newIpAddress
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmIpDriftUpdate() }) {
                    Text(stringResource(R.string.ip_drift_update))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissIpDriftCandidate() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.common_device_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editMac,
                        onValueChange = { editMac = it },
                        label = { Text(stringResource(R.string.edit_mac_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editBroadcast,
                        onValueChange = { editBroadcast = it },
                        label = { Text(stringResource(R.string.edit_broadcast_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPort,
                        onValueChange = { editPort = it },
                        label = { Text(stringResource(R.string.edit_port_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) {
                        viewModel.updateDevice(
                            displayed.copy(
                                name = editName,
                                macAddress = editMac,
                                broadcastAddress = editBroadcast.ifBlank { displayed.broadcastAddress },
                                port = editPort.toIntOrNull() ?: displayed.port
                            )
                        )
                        showEditDialog = false
                    }
                }) {
                    Text(stringResource(R.string.edit_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            modifier = Modifier.width(88.dp),
            maxLines = 1
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
