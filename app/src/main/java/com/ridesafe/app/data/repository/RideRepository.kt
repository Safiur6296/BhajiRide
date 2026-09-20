package com.ridesafe.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ridesafe.app.data.model.RideSession
import com.ridesafe.app.data.model.Rider
import com.ridesafe.app.data.model.RiderStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * RideRepository handles all communication with Firebase Realtime Database and Auth.
 *
 * For Java Developers:
 * - 'suspend' functions are Kotlin's coroutine primitives (like non-blocking async tasks
 *   without callback hell).
 * - 'Flow' is Kotlin's reactive stream (similar to RxJava Observable or Java 9 Flow).
 * - 'callbackFlow' converts asynchronous listener-based APIs (like Firebase ValueEventListener)
 *   into a clean reactive Stream/Flow.
 */
class RideRepository {

    private val database by lazy {
        try {
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            FirebaseDatabase.getInstance("https://ridesafe-a46dc-default-rtdb.firebaseio.com")
        }
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val ridesRef by lazy {
        database.getReference("rides").also {
            // Keep ride data actively synced to local cache so joinRide can
            // resolve from cache on slow connections
            it.keepSynced(true)
        }
    }

    /**
     * Ensures the current user is authenticated anonymously with Firebase.
     * This assigns a unique, persistent UID to each device without requiring emails/passwords.
     */
    suspend fun getOrCreateRiderId(): String {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid

        return try {
            val result = withTimeoutOrNull(4000L) {
                auth.signInAnonymously().await()
            }
            result?.user?.uid ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            // Local fallback ensures the app NEVER gets stuck if auth is pending or offline
            UUID.randomUUID().toString()
        }
    }

    /**
     * Generates a short, memorable 6-character ride code (e.g. "MOTO74" or "RIDE29").
     */
    fun generateRideCode(): String {
        val prefixes = listOf("MOTO", "RIDE", "BIKE", "CREW", "ROAD")
        val prefix = prefixes.random()
        val number = (10..99).random()
        return "$prefix$number"
    }

    /**
     * Creates a new ride session in Firebase with the given rider as the initial creator.
     * Returns a Result containing Pair(rideCode, riderId).
     */
    suspend fun createRide(riderName: String): Result<Pair<String, String>> {
        return try {
            val riderId = getOrCreateRiderId()
            val rideCode = generateRideCode()

            val session = RideSession(
                code = rideCode,
                createdBy = riderId,
                createdAt = System.currentTimeMillis(),
                active = true
            )

            val initialRider = Rider(
                id = riderId,
                name = riderName.trim(),
                status = RiderStatus.RIDING.name,
                lastUpdated = System.currentTimeMillis()
            )

            // Write session metadata and the creator into Firebase.
            // In Firebase RTDB, writes are cached locally and synchronized to the cloud.
            val sessionRef = ridesRef.child(rideCode)
            sessionRef.child("session").setValue(session)
            sessionRef.child("riders").child(riderId).setValue(initialRider)

            Result.success(Pair(rideCode, riderId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Joins an existing ride session using its 6-character code.
     * Uses addListenerForSingleValueEvent (cache-friendly) instead of .get() (server-only)
     * to prevent timeouts on slow mobile connections.
     */
    suspend fun joinRide(rideCode: String, riderName: String): Result<String> {
        return try {
            val cleanCode = rideCode.trim().uppercase()
            val riderId = getOrCreateRiderId()

            // Enable local disk persistence for the rides path so it doesn't require
            // a fresh server fetch every time
            val sessionRef = ridesRef.child(cleanCode).child("session")

            // Use addListenerForSingleValueEvent which can serve from Firebase's
            // local cache, unlike .get() which forces a server round-trip
            val snapshot = withTimeoutOrNull(15000L) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (continuation.isActive) {
                                continuation.resume(dataSnapshot)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) {
                                Log.e("RideRepository", "joinRide query cancelled: ${error.message}")
                                continuation.resumeWithException(error.toException())
                            }
                        }
                    }
                    sessionRef.addListenerForSingleValueEvent(listener)

                    // Clean up listener if coroutine is cancelled
                    continuation.invokeOnCancellation {
                        sessionRef.removeEventListener(listener)
                    }
                }
            }

            if (snapshot == null) {
                return Result.failure(
                    Exception("Could not reach the server. Please check your internet and try again.")
                )
            }

            if (!snapshot.exists()) {
                return Result.failure(
                    IllegalArgumentException("Ride '$cleanCode' not found. Check the code and try again.")
                )
            }

            val rider = Rider(
                id = riderId,
                name = riderName.trim(),
                status = RiderStatus.RIDING.name,
                lastUpdated = System.currentTimeMillis()
            )

            // Add this rider to the ride's riders node.
            // setValue is cached locally and synced when online, so this won't block.
            ridesRef.child(cleanCode).child("riders").child(riderId).setValue(rider).await()

            Result.success(riderId)
        } catch (e: Exception) {
            Log.e("RideRepository", "joinRide failed", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the current rider's real-time GPS coordinates and speed.
     */
    fun updateLocation(
        rideCode: String,
        riderId: String,
        lat: Double,
        lng: Double,
        speed: Float
    ) {
        val updates = mapOf<String, Any>(
            "lat" to lat,
            "lng" to lng,
            "speed" to speed,
            "lastUpdated" to System.currentTimeMillis()
        )
        ridesRef.child(rideCode).child("riders").child(riderId).updateChildren(updates)
    }

    /**
     * Updates the current rider's stop status (e.g. Refueling, Emergency, Rest Stop).
     */
    fun updateStatus(rideCode: String, riderId: String, status: RiderStatus) {
        val updates = mapOf<String, Any>(
            "status" to status.name,
            "lastUpdated" to System.currentTimeMillis()
        )
        ridesRef.child(rideCode).child("riders").child(riderId).updateChildren(updates)
    }

    /**
     * Observes all riders currently in the ride session in real-time.
     * Uses Kotlin Flow to emit the updated list whenever Firebase notifies of changes.
     */
    fun observeRiders(rideCode: String): Flow<List<Rider>> = callbackFlow {
        val cleanCode = rideCode.trim().uppercase()
        val ridersRef = ridesRef.child(cleanCode).child("riders")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ridersList = mutableListOf<Rider>()
                for (child in snapshot.children) {
                    val rider = child.getValue(Rider::class.java)
                    if (rider != null) {
                        ridersList.add(rider.copy(id = child.key ?: rider.id))
                    }
                }
                // Emit the new list to the Flow collector (ViewModel)
                trySend(ridersList)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        // Attach listener to Firebase Realtime Database
        ridersRef.addValueEventListener(listener)

        // When the Flow collector cancels (e.g. user leaves screen), remove the listener
        awaitClose {
            ridersRef.removeEventListener(listener)
        }
    }

    /**
     * Removes the rider from the ride session upon leaving.
     */
    fun leaveRide(rideCode: String, riderId: String) {
        try {
            ridesRef.child(rideCode).child("riders").child(riderId).removeValue()
        } catch (e: Exception) {
            // Log or ignore network errors on exit
        }
    }
}
