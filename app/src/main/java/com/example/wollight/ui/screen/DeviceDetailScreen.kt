package com.example.wollight.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wollight.model.Device
import com.example.wollight.ui.viewmodel.DeviceDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: Device,
    onNavigateBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(device) { viewModel.loadDevice(device) }

    val currentDevice by viewModel.device.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val wolMessage by viewModel.wolMessage.collectAsState()
    val pingResults by viewModel.pingResults.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(wolMessage) {
        wolMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearWolMessage()
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editMac by remember { mutableStateOf("") }
    var editBroadcast by remember { mutableStateOf("") }
    var editPort by remember { mutableStateOf("") }

    val displayed = currentDevice ?: device

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayed.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
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
                        Icon(Icons.Default.Edit, contentDescription = "編集")
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
                    InfoRow("名前", displayed.name)
                    InfoRow("IPアドレス", displayed.ipAddress)
                    InfoRow("MACアドレス", displayed.macAddress.ifEmpty { "未設定" })
                    InfoRow("ブロードキャスト", displayed.broadcastAddress)
                    InfoRow("ポート", displayed.port.toString())
                }
            }

            if (displayed.macAddress.isEmpty()) {
                Text("MACアドレスが未設定です。編集ボタンから設定してください。")
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
                    Text("WoL送信")
                }
                OutlinedButton(
                    onClick = { viewModel.sendPing() },
                    enabled = !isPinging,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isPinging) "Ping実行中..." else "Ping")
                }
            }

            if (pingResults.isNotEmpty() || isPinging) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        pingResults.forEach { result ->
                            val ordinal = "${result.attemptNumber}回目"
                            if (result.reachable) {
                                Text("✅ $ordinal: ${result.elapsedMs}ms")
                            } else {
                                Text("❌ $ordinal: timeout")
                            }
                        }
                        if (!isPinging && pingResults.size == 10) {
                            val successCount = pingResults.count { it.reachable }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            if (successCount > 0) {
                                val avgMs = pingResults
                                    .filter { it.reachable }
                                    .map { it.elapsedMs }
                                    .average()
                                    .toLong()
                                Text("10回中${successCount}回成功、平均応答時間 ${avgMs}ms")
                            } else {
                                Text("10回中0回成功")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("デバイス情報を編集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("デバイス名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editMac,
                        onValueChange = { editMac = it },
                        label = { Text("MACアドレス (AA:BB:CC:DD:EE:FF)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editBroadcast,
                        onValueChange = { editBroadcast = it },
                        label = { Text("ブロードキャストアドレス") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPort,
                        onValueChange = { editPort = it },
                        label = { Text("ポート") },
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
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = "$label: ", modifier = Modifier.weight(0.4f))
        Text(text = value, modifier = Modifier.weight(0.6f))
    }
}
