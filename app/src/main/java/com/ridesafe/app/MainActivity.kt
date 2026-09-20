package com.ridesafe.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ridesafe.app.data.model.AppUpdateInfo
import com.ridesafe.app.ui.components.UpdateDialog
import com.ridesafe.app.ui.navigation.RideNavGraph
import com.ridesafe.app.ui.theme.RideSafeTheme
import com.ridesafe.app.util.AppUpdateManager
import com.ridesafe.app.util.PermissionHelper

/**
 * MainActivity is the single Activity hosting our entire Jetpack Compose UI.
 *
 * In modern Android architecture, multi-screen apps use a "Single Activity" pattern:
 * MainActivity hosts the NavHost, which swaps composable screens without the heavy
 * overhead of multiple activities or complex Fragment lifecycles.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Register permission launcher in Compose
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsMap ->
                val fineGranted = permissionsMap[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseGranted = permissionsMap[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                val notifGranted = permissionsMap[android.Manifest.permission.POST_NOTIFICATIONS] ?: true
                android.util.Log.d(
                    "RideSafeDebug",
                    "[Permission] Permission result: fine=$fineGranted, coarse=$coarseGranted, notification=$notifGranted"
                )
                if (fineGranted) {
                    Toast.makeText(this, "GPS Location granted!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "BhaijiRide needs location permissions to track group rides.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // In-app direct update checking (no external testing tool required)
            var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
            val updateManager = remember { AppUpdateManager(this@MainActivity) }

            LaunchedEffect(Unit) {
                try {
                    val update = updateManager.checkForUpdates()
                    if (update != null) {
                        availableUpdate = update
                    }
                } catch (e: Exception) {
                    android.util.Log.w("BhaijiRide", "Update check failed: ${e.message}")
                }
            }

            RideSafeTheme {
                RideNavGraph(
                    onRequestPermissions = {
                        val required = PermissionHelper.getRequiredRidePermissions()
                        permissionLauncher.launch(required)
                    }
                )

                // Render in-app update prompt dialog if a newer build is available
                availableUpdate?.let { updateInfo ->
                    UpdateDialog(
                        updateInfo = updateInfo,
                        onDismiss = { availableUpdate = null }
                    )
                }
            }
        }
    }
}
