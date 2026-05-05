package com.example.wollight.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wollight.model.Device
import com.example.wollight.ui.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()

    var deviceToRegister by remember { mutableStateOf<Device?>(null) }
    var deviceName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LANスキャン") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isScanning) {
                Text("スキャン中... ($progress / 254)")
                LinearProgressIndicator(
                    progress = { progress / 254f },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { viewModel.stopScan() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("中断")
                }
            } else {
                Button(
                    onClick = { viewModel.startScan() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("スキャン開始")
                }
            }

            if (scannedDevices.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(scannedDevices, key = { it.ipAddress }) { device ->
                        ListItem(
                            headlineContent = { Text(device.ipAddress) },
                            supportingContent = {
                                Text(device.macAddress.ifEmpty { "MAC未取得" })
                            },
                            trailingContent = {
                                TextButton(onClick = {
                                    deviceToRegister = device
                                    deviceName = device.ipAddress
                                }) {
                                    Text("登録")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            } else if (!isScanning) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("スキャン開始ボタンを押してください")
                }
            }
        }
    }

    deviceToRegister?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToRegister = null },
            title = { Text("デバイス名を入力") },
            text = {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("デバイス名") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deviceName.isNotBlank()) {
                            viewModel.registerDevice(device, deviceName)
                            deviceToRegister = null
                        }
                    }
                ) {
                    Text("登録")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRegister = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}
