package com.iwadjp.wollight.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iwadjp.wollight.ui.screen.DeviceDetailScreen
import com.iwadjp.wollight.ui.screen.DeviceListScreen
import com.iwadjp.wollight.ui.screen.ScanScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "device_list") {
        composable("device_list") {
            DeviceListScreen(
                onDeviceClick = { device ->
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
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getLong("deviceId") ?: return@composable
            DeviceDetailScreen(
                deviceId = deviceId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
