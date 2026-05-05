package com.example.wollight.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.wollight.model.Device
import com.example.wollight.ui.screen.DeviceDetailScreen
import com.example.wollight.ui.screen.DeviceListScreen
import com.example.wollight.ui.screen.ScanScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var selectedDevice by remember { mutableStateOf<Device?>(null) }

    NavHost(navController = navController, startDestination = "device_list") {
        composable("device_list") {
            DeviceListScreen(
                onDeviceClick = { device ->
                    selectedDevice = device
                    navController.navigate("device_detail/${device.id}")
                },
                onScanClick = { navController.navigate("scan") }
            )
        }
        composable("scan") {
            ScanScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = "device_detail/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.LongType })
        ) {
            val device = selectedDevice
            if (device != null) {
                DeviceDetailScreen(
                    device = device,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
