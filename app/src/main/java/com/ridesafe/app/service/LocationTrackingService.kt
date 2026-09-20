package com.ridesafe.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ridesafe.app.MainActivity
import com.ridesafe.app.R
import com.ridesafe.app.RideSafeApp
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.data.repository.RideRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * LocationTrackingService is a Foreground Service that continuously tracks GPS location
 * and syncs it to Firebase Realtime Database.
 *
 * Why a Foreground Service?
 * Standard Android location listeners stop receiving updates when the user locks their phone
 * or switches to Google Maps / music app. A Foreground Service with an ongoing notification
 * keeps the tracking alive continuously throughout the motorbike ride.
 */
class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = RideRepository()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentRideCode: String = ""
    private var currentRiderId: String = ""
    private var currentRiderName: String = ""
    private var currentStatus: RiderStatus = RiderStatus.RIDING

    companion object {
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_UPDATE_STATUS = "ACTION_UPDATE_STATUS"

        const val EXTRA_RIDE_CODE = "EXTRA_RIDE_CODE"
        const val EXTRA_RIDER_ID = "EXTRA_RIDER_ID"
        const val EXTRA_RIDER_NAME = "EXTRA_RIDER_NAME"
        const val EXTRA_STATUS = "EXTRA_STATUS"

        /**
         * Helper method to start or update the tracking service from an Activity or Composable.
         */
        fun startTracking(
            context: Context,
            rideCode: String,
            riderId: String,
            riderName: String
        ) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RIDE_CODE, rideCode)
                putExtra(EXTRA_RIDER_ID, riderId)
                putExtra(EXTRA_RIDER_NAME, riderName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Helper method to update the stop status (e.g. Refueling, Emergency).
         */
        fun updateStatus(context: Context, status: RiderStatus) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra(EXTRA_STATUS, status.name)
            }
            context.startService(intent)
        }

        /**
         * Helper method to stop the tracking service.
         */
        fun stopTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentRideCode = intent.getStringExtra(EXTRA_RIDE_CODE) ?: ""
                currentRiderId = intent.getStringExtra(EXTRA_RIDER_ID) ?: ""
                currentRiderName = intent.getStringExtra(EXTRA_RIDER_NAME) ?: "Rider"

                android.util.Log.d("RideSafeDebug", "[Service] onStartCommand ACTION_START: rideCode='$currentRideCode', riderId='$currentRiderId', name='$currentRiderName'")
                startInForeground()
                startLocationUpdates()
            }
            ACTION_UPDATE_STATUS -> {
                val statusName = intent.getStringExtra(EXTRA_STATUS)
                currentStatus = RiderStatus.fromString(statusName)

                // Push status update to Firebase
                if (currentRideCode.isNotEmpty() && currentRiderId.isNotEmpty()) {
                    repository.updateStatus(currentRideCode, currentRiderId, currentStatus)
                }
                // Update notification text to reflect new status
                val notification = buildNotification()
                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // START_STICKY tells the OS to recreate the service if killed due to memory pressure
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        // Tapping the notification brings the rider back to MainActivity
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action button directly on the notification to Stop/Leave Ride
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (currentStatus == RiderStatus.RIDING) {
            "Sharing live GPS with Ride $currentRideCode"
        } else {
            "Status: ${currentStatus.emoji} ${currentStatus.displayName} (Ride $currentRideCode)"
        }

        return NotificationCompat.Builder(this, RideSafeApp.LOCATION_CHANNEL_ID)
            .setContentTitle("BhaijiRide • Group Tracking Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true) // Cannot be swiped away by user while ride is active
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Leave Ride", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // 1. Immediately push last known location if available
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                android.util.Log.d("RideSafeDebug", "[Location] Service lastLocation callback: location=$location")
                if (location != null && currentRideCode.isNotEmpty() && currentRiderId.isNotEmpty()) {
                    android.util.Log.d("RideSafeDebug", "[Location] Service pushing initial lastLocation: lat=${location.latitude}, lng=${location.longitude}")
                    repository.updateLocation(
                        rideCode = currentRideCode,
                        riderId = currentRiderId,
                        lat = location.latitude,
                        lng = location.longitude,
                        speed = location.speed
                    )
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("RideSafeDebug", "[Location] Service SecurityException on lastLocation: ${e.message}", e)
        }

        // 2. Continuous location requests:
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).apply {
            setMinUpdateIntervalMillis(2500L)
            setMinUpdateDistanceMeters(0f)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            android.util.Log.d("RideSafeDebug", "[Location] Service requesting continuous location updates...")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            android.util.Log.d("RideSafeDebug", "[Location] Service requestLocationUpdates registered successfully.")
        } catch (e: SecurityException) {
            android.util.Log.e("RideSafeDebug", "[Location] Service SecurityException on requestLocationUpdates: ${e.message}. Calling stopSelf()!", e)
            stopSelf()
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                android.util.Log.d(
                    "RideSafeDebug",
                    "[Location] Service onLocationResult: lat=${location.latitude}, lng=${location.longitude}, speed=${location.speed}, accuracy=${location.accuracy}"
                )

                if (currentRideCode.isNotEmpty() && currentRiderId.isNotEmpty()) {
                    // Update Firebase with fresh coordinates, speed, and timestamp
                    repository.updateLocation(
                        rideCode = currentRideCode,
                        riderId = currentRiderId,
                        lat = location.latitude,
                        lng = location.longitude,
                        speed = location.speed
                    )
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        // Cleanly remove rider from Firebase upon stopping
        if (currentRideCode.isNotEmpty() && currentRiderId.isNotEmpty()) {
            repository.leaveRide(currentRideCode, currentRiderId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
