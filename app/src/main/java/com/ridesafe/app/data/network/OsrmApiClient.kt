package com.ridesafe.app.data.network

import android.util.Log
import com.ridesafe.app.data.model.RouteResult
import com.ridesafe.app.util.PolylineUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * OsrmApiClient handles communication with the Open Source Routing Machine (OSRM) service.
 *
 * ## Usage & Rate Limit Notes:
 * By default, this client queries OSRM's public demo routing server:
 * `https://router.project-osrm.org/`
 *
 * ⚠️ **IMPORTANT CONSTRAINTS & LIMITATIONS OF THE PUBLIC DEMO SERVER:**
 * 1. **No SLA or Guarantees:** The public server is maintained by project volunteers for testing
 *    and demo purposes only. It is not an enterprise SLA service.
 * 2. **Rate Limits:** The demo server enforces informal rate limits (typically ~1 request/sec per IP).
 *    Spamming or heavy automated queries may result in temporary IP bans (HTTP 429).
 * 3. **Car Profile Only:** The public demo server exposes only the default `driving` car profile.
 *
 * ### Production Upgrade Paths:
 * When your group-ride app outgrows the public demo server, you have two excellent, cost-effective options:
 * 1. **Self-hosted OSRM with Docker:**
 *    You can run your own dedicated OSRM server on a modest VPS (e.g. Hetzner, AWS Lightsail, DigitalOcean)
 *    for $5–$10/month. Download your region's OpenStreetMap `.osm.pbf` extract (e.g. from Geofabrik)
 *    and run `osrm-routed`. This gives you:
 *    - Zero rate limits & ultra-low latency (<20ms response time)
 *    - Custom motorbike routing profiles (optimizing for twisty scenic roads or avoiding dirt tracks)
 *    - Complete data privacy.
 * 2. **OpenRouteService (ORS) Free Tier:**
 *    Switching to OpenRouteService (https://openrouteservice.org/) gives you:
 *    - 2,000 free routing requests per day with an API key
 *    - Dedicated cycling / motorcycle routing profiles
 *    - Turn-by-turn navigation instructions.
 */
class OsrmApiClient(
    private val customBaseUrl: String? = null
) {
    companion object {
        private const val TAG = "OsrmApiClient"
        const val DEFAULT_BASE_URL = "https://router.project-osrm.org"
        private const val USER_AGENT = "RideSafe-Android-App/1.0 (contact: info@ridesafe.app)"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = customBaseUrl?.trimEnd('/') ?: DEFAULT_BASE_URL

    /**
     * Calculates the shortest driving route between start ([startLat], [startLng])
     * and destination ([destLat], [destLng]).
     *
     * Returns a [Result] containing [RouteResult] with decoded [GeoPoint] list,
     * total distance in meters, and estimated duration in seconds.
     */
    suspend fun getRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<RouteResult> = withContext(Dispatchers.IO) {
        if (startLat == 0.0 && startLng == 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Invalid start coordinates"))
        }
        if (destLat == 0.0 && destLng == 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Invalid destination coordinates"))
        }

        try {
            // OSRM expects coordinates in standard lon,lat order: {startLng},{startLat};{destLng},{destLat}
            val coordinates = String.format(
                Locale.US,
                "%.6f,%.6f;%.6f,%.6f",
                startLng, startLat, destLng, destLat
            )

            // Request full polyline overview
            val url = "$baseUrl/route/v1/driving/$coordinates?overview=full&geometries=polyline"
            Log.d(TAG, "Requesting OSRM route: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorMsg = when (code) {
                        429 -> "Routing service rate limited. Please try again in a few moments."
                        400 -> "Invalid routing coordinates or no road path found."
                        else -> "Failed to calculate route (HTTP $code)."
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(
                    Exception("Empty response received from routing server.")
                )

                parseOsrmResponse(body, startLat, startLng, destLat, destLng)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OSRM route request error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseOsrmResponse(
        jsonString: String,
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<RouteResult> {
        return try {
            val root = JSONObject(jsonString)
            val code = root.optString("code", "")
            if (!code.equals("Ok", ignoreCase = true)) {
                val message = root.optString("message", "Route calculation failed.")
                return Result.failure(Exception("OSRM error ($code): $message"))
            }

            val routes = root.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                return Result.failure(Exception("No route found between the specified points."))
            }

            val firstRoute = routes.getJSONObject(0)
            val distance = firstRoute.optDouble("distance", 0.0) // meters
            val duration = firstRoute.optDouble("duration", 0.0) // seconds

            // Geometry can be a polyline string or a GeoJSON object
            val geometryObj = firstRoute.opt("geometry")
            val (points, polylineString) = when (geometryObj) {
                is String -> {
                    val decoded = PolylineUtils.decodePolyline(geometryObj)
                    Pair(decoded, geometryObj)
                }
                is JSONObject -> {
                    val coords = geometryObj.optJSONArray("coordinates")
                    val decoded = if (coords != null) {
                        PolylineUtils.decodeGeometry(coords.toString())
                    } else {
                        emptyList()
                    }
                    val encoded = PolylineUtils.encodePolyline(decoded)
                    Pair(decoded, encoded)
                }
                else -> {
                    Pair(emptyList<GeoPoint>(), "")
                }
            }

            // Ensure start and destination points are at least present if decoding produced nothing
            val finalPoints = if (points.isNotEmpty()) {
                points
            } else {
                listOf(GeoPoint(startLat, startLng), GeoPoint(destLat, destLng))
            }

            // Extract summary road name if available
            val legs = firstRoute.optJSONArray("legs")
            var summary = ""
            if (legs != null && legs.length() > 0) {
                summary = legs.getJSONObject(0).optString("summary", "")
            }

            Result.success(
                RouteResult(
                    routePoints = finalPoints,
                    encodedPolyline = polylineString.ifEmpty { PolylineUtils.encodePolyline(finalPoints) },
                    distanceMeters = distance,
                    durationSeconds = duration,
                    summary = summary
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OSRM response: ${e.message}", e)
            Result.failure(e)
        }
    }
}
