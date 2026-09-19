package com.ridesafe.app.util

import android.location.Location
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utility functions for location calculations and human-friendly formatting.
 */
object LocationUtils {

    /**
     * Calculates distance in meters between two lat/lng points using Android's built-in geodesy.
     */
    fun calculateDistanceMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }

    /**
     * Formats distance in meters into a rider-friendly string (e.g., "350 m" or "2.4 km").
     */
    fun formatDistance(meters: Float): String {
        return if (meters < 1000f) {
            "${meters.roundToInt()} m"
        } else {
            String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
        }
    }

    /**
     * Formats speed from meters/sec to kilometers/hour.
     */
    fun formatSpeed(speedMps: Float): String {
        val kmh = (speedMps * 3.6f).roundToInt()
        return "$kmh km/h"
    }

    /**
     * Formats a timestamp into a relative "last updated" indicator.
     */
    fun formatTimeAgo(timestamp: Long): String {
        if (timestamp <= 0L) return "Unknown"
        val diffMs = System.currentTimeMillis() - timestamp
        val seconds = diffMs / 1000
        return when {
            seconds < 10 -> "Just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }
}
