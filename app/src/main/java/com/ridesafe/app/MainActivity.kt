package com.ridesafe.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.ridesafe.app.ui.navigation.RideNavGraph
import com.ridesafe.app.ui.theme.RideSafeTheme
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

            RideSafeTheme {
                RideNavGraph(
                    onRequestPermissions = {
                        val required = PermissionHelper.getRequiredRidePermissions()
                        permissionLauncher.launch(required)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Prompt user to update whenever a new build is uploaded to Firebase App Distribution
        try {
            com.google.firebase.appdistribution.FirebaseAppDistribution.getInstance().updateIfNewReleaseAvailable()
        } catch (e: Exception) {
            // Silently ignore if running local debug or preview
        }
    }
}
