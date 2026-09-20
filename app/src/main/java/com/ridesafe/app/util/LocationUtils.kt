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
     * Formats distance cleanly (e.g. "10 KM", "5 KM", "350 M").
     */
    fun formatCleanDistance(meters: Float): String {
        return if (meters < 1000f) {
            "${meters.roundToInt()} M"
        } else {
            val km = meters / 1000f
            if (km >= 10f || (km * 10f).roundToInt() % 10 == 0) {
                "${km.roundToInt()} KM"
            } else {
                String.format(Locale.getDefault(), "%.1f KM", km)
            }
        }
    }

    /**
     * Calculates the initial bearing in degrees (0..360) from start to end location.
     */
    fun calculateBearing(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val startLoc = Location("start").apply {
            latitude = startLat
            longitude = startLng
        }
        val endLoc = Location("end").apply {
            latitude = endLat
            longitude = endLng
        }
        val bearing = startLoc.bearingTo(endLoc)
        return (bearing + 360f) % 360f
    }

    /**
     * Determines whether target rider is ahead or behind the current rider.
     * Uses heading angle if moving, or direction vector if stationary.
     */
    fun isRiderAhead(
        myLat: Double,
        myLng: Double,
        myHeading: Float?,
        targetLat: Double,
        targetLng: Double
    ): Boolean {
        val bearingToTarget = calculateBearing(myLat, myLng, targetLat, targetLng)
        return if (myHeading != null && myHeading >= 0f) {
            val diff = kotlin.math.abs((bearingToTarget - myHeading + 180f) % 360f - 180f)
            diff <= 90f
        } else {
            // If user has no travel heading yet, check bearing relative to North (0-90 or 270-360)
            bearingToTarget in 0f..90f || bearingToTarget in 270f..360f
        }
    }

    /**
     * Builds the exact display string requested by the user:
     * e.g., "Rahul is 10 KM ahead of You" or "Sahil is 5 KM behind you"
     */
    fun getRelativePositionDescription(
        riderName: String,
        distanceMeters: Float,
        isAhead: Boolean
    ): String {
        val cleanName = riderName.trim().ifEmpty { "Rider" }
        val distStr = formatCleanDistance(distanceMeters)
        val dirStr = if (isAhead) "ahead of You" else "behind you"
        return "$cleanName is $distStr $dirStr"
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
