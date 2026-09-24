package com.ridesafe.app.data.model

/**
 * Autocomplete place suggestion returned by Photon geocoding API.
 */
data class PlaceSuggestion(
    val name: String,
    val locality: String = "",
    val fullDisplayName: String = "",
    val lat: Double,
    val lng: Double,
    val osmKey: String? = null,
    val osmValue: String? = null
)
