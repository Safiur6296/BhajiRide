package com.ridesafe.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ridesafe.app.ui.screens.home.HomeScreen
import com.ridesafe.app.ui.screens.map.LiveMapScreen
import com.ridesafe.app.ui.screens.map.MapViewModel

/**
 * RideNavGraph defines the navigation routes for the app:
 * - "home": Onboarding / Create or Join a ride
 * - "map/{rideCode}/{riderId}/{riderName}": Live map tracking screen
 */
@Composable
fun RideNavGraph(
    onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onRequestPermissions = onRequestPermissions,
                onRideJoined = { rideCode, riderId, riderName ->
                    val encodedName = java.net.URLEncoder.encode(riderName, "UTF-8")
                    navController.navigate("map/$rideCode/$riderId/$encodedName") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = "map/{rideCode}/{riderId}/{riderName}",
            arguments = listOf(
                navArgument("rideCode") { type = NavType.StringType },
                navArgument("riderId") { type = NavType.StringType },
                navArgument("riderName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rideCode = backStackEntry.arguments?.getString("rideCode") ?: ""
            val riderId = backStackEntry.arguments?.getString("riderId") ?: ""
            val rawName = backStackEntry.arguments?.getString("riderName") ?: ""
            val riderName = java.net.URLDecoder.decode(rawName, "UTF-8")

            val mapViewModel: MapViewModel = viewModel()

            // Initialize session only once when entering this destination
            LaunchedEffect(rideCode, riderId) {
                mapViewModel.initSession(rideCode, riderId, riderName)
            }

            LiveMapScreen(
                viewModel = mapViewModel,
                onLeaveRide = {
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
