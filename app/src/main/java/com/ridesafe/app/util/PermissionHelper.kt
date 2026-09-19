package com.ridesafe.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * PermissionHelper provides utility methods to check runtime permissions.
 *
 * Android Permission Lifecycle Guide for Beginners:
 * 1. Android 6.0 (API 23)+ introduced Runtime Permissions (user must approve via dialog).
 * 2. Android 10.0 (API 29)+ separated Foreground and Background location.
 * 3. Android 13.0 (API 33)+ made Notifications a runtime permission (POST_NOTIFICATIONS).
 * 4. Android 14.0 (API 34)+ requires FOREGROUND_SERVICE_LOCATION declared in Manifest.
 *
 * KEY TAKEAWAY:
 * With a Foreground Service and an active ongoing Notification, Android considers the app
 * to be running in the FOREGROUND for location purposes! Therefore, FINE_LOCATION +
 * POST_NOTIFICATIONS is sufficient to track riders even with the screen off or when
 * viewing another app (e.g. Google Maps navigation).
 */
object PermissionHelper {

    /**
     * Returns the list of base permissions required to start a ride and track location.
     */
    fun getRequiredRidePermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Android 13 (API 33) and above requires runtime permission for notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }

    /**
     * Checks if all mandatory location and notification permissions are granted.
     */
    fun hasRequiredRidePermissions(context: Context): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return hasFine && hasCoarse && hasNotification
    }

    /**
     * Checks if Background Location (ACCESS_BACKGROUND_LOCATION) is granted.
     * Note: Android policy forbids requesting background location simultaneously with foreground location.
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Prior to Android 10, fine location covered background execution
        }
    }
}
