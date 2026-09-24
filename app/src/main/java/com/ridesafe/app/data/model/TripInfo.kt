package com.ridesafe.app.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.ridesafe.app.util.PolylineUtils
import org.osmdroid.util.GeoPoint

/**
 * TripInfo represents the planned route details for a group ride session in Firebase RTDB.
 * Stored at: rides/{rideCode}/tripInfo
 */
@IgnoreExtraProperties
data class TripInfo(
    val startName: String = "",
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val destName: String = "",
    val destLat: Double = 0.0,
    val destLng: Double = 0.0,
    val routeGeometry: String = "", // Encoded polyline or GeoJSON string
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    @get:Exclude
    val isTripPlanned: Boolean
        get() = (startLat != 0.0 || startLng != 0.0) &&
                (destLat != 0.0 || destLng != 0.0) &&
                routeGeometry.isNotBlank()

    @get:Exclude
    val formattedDistance: String
        get() = PolylineUtils.formatDistance(distanceMeters)

    @get:Exclude
    val formattedDuration: String
        get() = PolylineUtils.formatDuration(durationSeconds)

    @get:Exclude
    val startGeoPoint: GeoPoint
        get() = GeoPoint(startLat, startLng)

    @get:Exclude
    val destGeoPoint: GeoPoint
        get() = GeoPoint(destLat, destLng)
}
