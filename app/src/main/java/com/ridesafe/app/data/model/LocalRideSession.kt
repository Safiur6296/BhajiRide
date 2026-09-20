package com.ridesafe.app.data.model

import org.json.JSONObject

/**
 * LocalRideSession represents a ride session that this device has participated in (created or joined).
 * Stored locally via Jetpack DataStore to persist ride history across app restarts.
 */
data class LocalRideSession(
    val rideCode: String,
    val riderId: String,
    val riderName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isHost: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("rideCode", rideCode)
            put("riderId", riderId)
            put("riderName", riderName)
            put("timestamp", timestamp)
            put("isHost", isHost)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): LocalRideSession {
            return LocalRideSession(
                rideCode = json.optString("rideCode", ""),
                riderId = json.optString("riderId", ""),
                riderName = json.optString("riderName", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isHost = json.optBoolean("isHost", false)
            )
        }
    }
}

/**
 * UI representation of a local ride session, including its live Firebase status.
 */
data class LocalRideSessionUi(
    val session: LocalRideSession,
    val isActive: Boolean = false,
    val isChecking: Boolean = false
) {
    val rideCode: String get() = session.rideCode
    val riderId: String get() = session.riderId
    val riderName: String get() = session.riderName
    val timestamp: Long get() = session.timestamp
    val isHost: Boolean get() = session.isHost
}
