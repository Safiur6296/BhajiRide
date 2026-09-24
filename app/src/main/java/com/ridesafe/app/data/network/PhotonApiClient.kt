package com.ridesafe.app.data.network

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.ridesafe.app.data.model.PlaceSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * PhotonApiClient communicates with Komoot's free, keyless Photon geocoding service:
 * https://photon.komoot.io/
 *
 * Photon is built on OpenStreetMap data and Elasticsearch, designed specifically
 * for fast, type-ahead place suggestions without requiring any billing or API keys.
 */
class PhotonApiClient(
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "PhotonApiClient"
        private const val BASE_URL = "https://photon.komoot.io"
        private const val USER_AGENT = "RideSafe-Android-App/1.0 (contact: info@ridesafe.app)"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Searches for place suggestions matching [query].
     * If [biasLat] and [biasLng] are provided, Photon prioritizes places closer to that location.
     */
    suspend fun searchPlaces(
        query: String,
        biasLat: Double? = null,
        biasLng: Double? = null,
        limit: Int = 6
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val urlBuilder = StringBuilder("$BASE_URL/api/?q=$encodedQuery&limit=$limit&lang=en")

            if (biasLat != null && biasLng != null && biasLat != 0.0 && biasLng != 0.0) {
                urlBuilder.append("&lat=$biasLat&lon=$biasLng")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Photon search failed with HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val bodyString = response.body?.string() ?: return@withContext emptyList()
                return@withContext parsePhotonFeatures(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Photon search: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Reverse-geocodes a GPS coordinate (lat/lng) into a readable place name.
     * Uses Photon's reverse endpoint with a fallback to Android's built-in Geocoder.
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): PlaceSuggestion? = withContext(Dispatchers.IO) {
        if (lat == 0.0 && lng == 0.0) return@withContext null

        // 1. Try Photon reverse endpoint
        try {
            val url = "$BASE_URL/reverse?lat=$lat&lon=$lng&lang=en"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val places = parsePhotonFeatures(bodyString)
                        if (places.isNotEmpty()) {
                            return@withContext places.first()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Photon reverse geocoding failed: ${e.message}. Trying local Geocoder...")
        }

        // 2. Fallback to Android native Geocoder
        if (context != null && Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val title = addr.featureName ?: addr.subLocality ?: addr.locality ?: "Current Location"
                    val localityParts = listOfNotNull(
                        addr.subLocality,
                        addr.locality,
                        addr.adminArea,
                        addr.countryName
                    ).distinct().filter { it != title }
                    val locality = localityParts.joinToString(", ")

                    return@withContext PlaceSuggestion(
                        name = title,
                        locality = locality,
                        fullDisplayName = if (locality.isNotBlank()) "$title, $locality" else title,
                        lat = lat,
                        lng = lng
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Local Geocoder failed: ${e.message}")
            }
        }

        // 3. Fallback coordinates representation
        PlaceSuggestion(
            name = "Current Location",
            locality = String.format(Locale.getDefault(), "%.4f, %.4f", lat, lng),
            fullDisplayName = "Current Location",
            lat = lat,
            lng = lng
        )
    }

    private fun parsePhotonFeatures(jsonString: String): List<PlaceSuggestion> {
        val results = mutableListOf<PlaceSuggestion>()
        try {
            val root = JSONObject(jsonString)
            val features = root.optJSONArray("features") ?: return emptyList()

            for (i in 0 until features.length()) {
                val feat = features.optJSONObject(i) ?: continue
                val geometry = feat.optJSONObject("geometry") ?: continue
                val coords = geometry.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue

                // GeoJSON format is [longitude, latitude]
                val lng = coords.optDouble(0, 0.0)
                val lat = coords.optDouble(1, 0.0)
                if (lat == 0.0 && lng == 0.0) continue

                val props = feat.optJSONObject("properties") ?: JSONObject()

                val name = props.optString("name").trim().ifEmpty {
                    val street = props.optString("street").trim()
                    val houseNumber = props.optString("housenumber").trim()
                    if (street.isNotEmpty()) {
                        if (houseNumber.isNotEmpty()) "$street $houseNumber" else street
                    } else {
                        props.optString("city").trim().ifEmpty {
                            props.optString("state").trim()
                        }
                    }
                }

                if (name.isEmpty()) continue

                val localityList = mutableListOf<String>()
                props.optString("district").trim().takeIf { it.isNotEmpty() && it != name }?.let { localityList.add(it) }
                props.optString("city").trim().takeIf { it.isNotEmpty() && it != name }?.let { localityList.add(it) }
                props.optString("state").trim().takeIf { it.isNotEmpty() && it != name }?.let { localityList.add(it) }
                props.optString("country").trim().takeIf { it.isNotEmpty() && it != name }?.let { localityList.add(it) }

                val locality = localityList.distinct().joinToString(", ")
                val fullDisplayName = if (locality.isNotEmpty()) "$name, $locality" else name

                results.add(
                    PlaceSuggestion(
                        name = name,
                        locality = locality,
                        fullDisplayName = fullDisplayName,
                        lat = lat,
                        lng = lng,
                        osmKey = props.optString("osm_key").takeIf { it.isNotEmpty() },
                        osmValue = props.optString("osm_value").takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Photon JSON: ${e.message}", e)
        }
        return results
    }
}
