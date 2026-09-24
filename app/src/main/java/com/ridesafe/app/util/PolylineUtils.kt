package com.ridesafe.app.util

import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utility functions for polyline encoding/decoding, GeoJSON parsing,
 * formatting distance/durations, and calculating route bounding boxes.
 */
object PolylineUtils {

    /**
     * Decodes an encoded polyline string (Google Polyline algorithm, precision 5)
     * into a list of osmdroid GeoPoint objects.
     */
    fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val pLat = lat / 1e5
            val pLng = lng / 1e5
            poly.add(GeoPoint(pLat, pLng))
        }
        return poly
    }

    /**
     * Encodes a list of GeoPoints into an encoded polyline string (precision 5).
     */
    fun encodePolyline(points: List<GeoPoint>): String {
        val result = StringBuilder()
        var lastLat = 0
        var lastLng = 0

        for (point in points) {
            val lat = (point.latitude * 1e5).roundToInt()
            val lng = (point.longitude * 1e5).roundToInt()

            encodeSignedNumber(lat - lastLat, result)
            encodeSignedNumber(lng - lastLng, result)

            lastLat = lat
            lastLng = lng
        }
        return result.toString()
    }

    private fun encodeSignedNumber(num: Int, result: StringBuilder) {
        var sgnNum = num shl 1
        if (num < 0) {
            sgnNum = sgnNum.inv()
        }
        encodeNumber(sgnNum, result)
    }

    private fun encodeNumber(num: Int, result: StringBuilder) {
        var n = num
        while (n >= 0x20) {
            val nextVal = (0x20 or (n and 0x1f)) + 63
            result.append(nextVal.toChar())
            n = n shr 5
        }
        result.append((n + 63).toChar())
    }

    /**
     * Decodes a route geometry string which may either be an encoded polyline
     * or a GeoJSON coordinate array/object.
     */
    fun decodeGeometry(geometry: String): List<GeoPoint> {
        val trimmed = geometry.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (trimmed.isEmpty()) return emptyList()

        // Check if it's JSON formatted
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                val coordsArray: JSONArray = if (trimmed.startsWith("{")) {
                    val json = JSONObject(trimmed)
                    json.optJSONArray("coordinates") ?: JSONArray()
                } else {
                    JSONArray(trimmed)
                }

                val points = ArrayList<GeoPoint>(coordsArray.length())
                for (i in 0 until coordsArray.length()) {
                    val pair = coordsArray.optJSONArray(i)
                    if (pair != null && pair.length() >= 2) {
                        // GeoJSON stores coordinates as [longitude, latitude]
                        val lng = pair.optDouble(0, 0.0)
                        val lat = pair.optDouble(1, 0.0)
                        if (lat != 0.0 || lng != 0.0) {
                            points.add(GeoPoint(lat, lng))
                        }
                    }
                }
                if (points.isNotEmpty()) return points
            } catch (e: Exception) {
                // Fallback to polyline decoding if JSON parse fails
            }
        }

        // Standard polyline decode with unescaped backslashes if any
        val unescaped = if (trimmed.contains("\\\\")) trimmed.replace("\\\\", "\\") else trimmed
        return try {
            decodePolyline(unescaped)
        } catch (e: Exception) {
            emptyList()
        }
    }


    /**
     * Formats distance in meters into human-readable string (e.g. "850 m" or "45.2 km").
     */
    fun formatDistance(meters: Double): String {
        return when {
            meters <= 0.0 -> "0 km"
            meters < 1000 -> "${meters.roundToInt()} m"
            else -> {
                val km = meters / 1000.0
                if (km < 10) {
                    String.format(Locale.getDefault(), "%.1f km", km)
                } else {
                    "${km.roundToInt()} km"
                }
            }
        }
    }

    /**
     * Formats duration in seconds into human-readable string (e.g. "45 min" or "1 hr 25 min").
     */
    fun formatDuration(seconds: Double): String {
        if (seconds <= 0.0) return "0 min"
        val totalMinutes = (seconds / 60.0).roundToInt()
        return when {
            totalMinutes < 60 -> "$totalMinutes min"
            else -> {
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                if (mins == 0) {
                    "$hours hr"
                } else {
                    "$hours hr $mins min"
                }
            }
        }
    }

    /**
     * Calculates a BoundingBox that tightly encloses all provided route points
     * and any additional points (such as rider positions and start/dest markers).
     */
    fun calculateRouteBoundingBox(
        routePoints: List<GeoPoint>,
        additionalPoints: List<GeoPoint> = emptyList()
    ): BoundingBox? {
        val allPoints = mutableListOf<GeoPoint>()
        allPoints.addAll(routePoints)
        allPoints.addAll(additionalPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 })

        if (allPoints.isEmpty()) return null

        var maxLat = -90.0
        var minLat = 90.0
        var maxLng = -180.0
        var minLng = 180.0

        for (pt in allPoints) {
            if (pt.latitude > maxLat) maxLat = pt.latitude
            if (pt.latitude < minLat) minLat = pt.latitude
            if (pt.longitude > maxLng) maxLng = pt.longitude
            if (pt.longitude < minLng) minLng = pt.longitude
        }

        // Add 5% padding around bounds so markers don't touch screen edges
        val latPadding = ((maxLat - minLat).coerceAtLeast(0.005)) * 0.08
        val lngPadding = ((maxLng - minLng).coerceAtLeast(0.005)) * 0.08

        return BoundingBox(
            (maxLat + latPadding).coerceAtMost(90.0),
            (maxLng + lngPadding).coerceAtMost(180.0),
            (minLat - latPadding).coerceAtLeast(-90.0),
            (minLng - lngPadding).coerceAtLeast(-180.0)
        )
    }
}
