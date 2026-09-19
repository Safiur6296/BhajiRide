package com.ridesafe.app.data.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Rider represents an active participant in a group ride.
 *
 * Kotlin 'data class' automatically generates equals(), hashCode(), toString(),
 * and copy() methods that in Java would take 50+ lines of boilerplate.
 *
 * By providing default values for all parameters, Kotlin also generates the parameterless
 * (no-argument) constructor required by Firebase Realtime Database to deserialize JSON.
 */
@IgnoreExtraProperties
data class Rider(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speed: Float = 0f,
    val status: String = RiderStatus.RIDING.name,
    val lastUpdated: Long = 0L
) {
    /**
     * Helper to get the strongly-typed RiderStatus enum.
     * @Exclude prevents Firebase from trying to serialize this computed property back to JSON.
     */
    @get:Exclude
    val riderStatus: RiderStatus
        get() = RiderStatus.fromString(status)

    /**
     * Checks if this rider's location was updated within the last 60 seconds.
     */
    @get:Exclude
    val isOnline: Boolean
        get() = (System.currentTimeMillis() - lastUpdated) < 60_000L
}
