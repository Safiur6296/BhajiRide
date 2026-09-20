package com.ridesafe.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

/**
 * RideSafeApp is the Application class for the app.
 *
 * In Android (like Java's main application context), this class is instantiated before
 * any activity or service starts. We use it here to:
 * 1. Initialize Firebase services and offline persistence.
 * 2. Create the Notification Channel required by Android 8.0+ (API 26) for our
 *    Foreground Location Tracking Service.
 */
class RideSafeApp : Application() {

    companion object {
        // Notification channel ID for the foreground location tracking service
        const val LOCATION_CHANNEL_ID = "location_tracking_channel"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Enable Firebase Realtime Database disk persistence so that if a rider briefly
        // loses cell signal on the highway, updates are queued locally and synced once reconnected.
        try {
            FirebaseDatabase.getInstance("https://ridesafe-a46dc-default-rtdb.asia-southeast1.firebasedatabase.app")
                .setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Persistence must be set before any database reference is created.
        }
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Fallback for default instance
        }

        // 2. Create Notification Channel for the Foreground Service
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Notification channels are only required on Android 8.0 (API level 26) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.tracking_notification_channel_name)
            val channelDescription = getString(R.string.tracking_notification_channel_desc)

            // IMPORTANCE_LOW ensures the notification is shown persistently in the status bar
            // without making an intrusive sound every time location updates.
            val channel = NotificationChannel(
                LOCATION_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = channelDescription
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
