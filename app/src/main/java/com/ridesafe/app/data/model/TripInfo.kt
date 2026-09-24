package com.ridesafe.app.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.ridesafe.app.util.PolylineUtils
import org.osmdroid.util.GeoPoint

/**
 * TripInfo represents the planned route details for a group ride session in Firebase RTDB.
 * Stored at: rides/{rideCode}/tripInfo
 *
 * Supports both Android schema (routeGeometry, distanceMeters, durationSeconds)
 * and Web schema (encodedPolyline, distanceKm, durationMin, isTripPlanned).
 */
@IgnoreExtraProperties
data class TripInfo(
    var startName: String = "",
    var startLat: Double = 0.0,
    var startLng: Double = 0.0,
    var destName: String = "",
    var destLat: Double = 0.0,
    var destLng: Double = 0.0,
    var routeGeometry: String = "", // Encoded polyline or GeoJSON string (Android)
    var encodedPolyline: String = "", // Encoded polyline string (Web)
    var geometry: String = "", // Fallback
    var distanceMeters: Double = 0.0,
    var durationSeconds: Double = 0.0,
    var distanceKm: Double = 0.0,
    var durationMin: Double = 0.0,
    var hasPlannedTrip: Boolean = false,
    var createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Resolves the non-blank route geometry from either routeGeometry, encodedPolyline, or geometry.
     */
    @get:Exclude
    val effectiveGeometry: String
        get() = when {
            routeGeometry.isNotBlank() -> routeGeometry.trim()
            encodedPolyline.isNotBlank() -> encodedPolyline.trim()
            geometry.isNotBlank() -> geometry.trim()
            else -> ""
        }

    /**
     * isTripPlanned evaluates to true if explicitly marked as planned, or if
     * routeGeometry/encodedPolyline is present, or if start and destination points are valid.
     */
    var isTripPlanned: Boolean
        get() = hasPlannedTrip ||
                effectiveGeometry.isNotBlank() ||
                ((startLat != 0.0 || startLng != 0.0) && (destLat != 0.0 || destLng != 0.0))
        set(value) {
            hasPlannedTrip = value
        }

    @get:Exclude
    val effectiveDistanceMeters: Double
        get() = when {
            distanceMeters > 0.0 -> distanceMeters
            distanceKm > 0.0 -> distanceKm * 1000.0
            else -> 0.0
        }

    @get:Exclude
    val effectiveDurationSeconds: Double
        get() = when {
            durationSeconds > 0.0 -> durationSeconds
            durationMin > 0.0 -> durationMin * 60.0
            else -> 0.0
        }

    @get:Exclude
    val formattedDistance: String
        get() = PolylineUtils.formatDistance(effectiveDistanceMeters)

    @get:Exclude
    val formattedDuration: String
        get() = PolylineUtils.formatDuration(effectiveDurationSeconds)

    @get:Exclude
    val startGeoPoint: GeoPoint
        get() = GeoPoint(startLat, startLng)

    @get:Exclude
    val destGeoPoint: GeoPoint
        get() = GeoPoint(destLat, destLng)
}

