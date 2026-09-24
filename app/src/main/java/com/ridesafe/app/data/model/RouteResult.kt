package com.ridesafe.app.data.model

import org.osmdroid.util.GeoPoint

/**
 * Parsed route details returned by the OSRM Routing API.
 */
data class RouteResult(
    val routePoints: List<GeoPoint>,
    val encodedPolyline: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val summary: String = ""
)
