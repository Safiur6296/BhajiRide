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
import com.ridesafe.app.data.model.TripInfo
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

    companion object {
        const val DATABASE_URL = "https://ridesafe-a46dc-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    private val database by lazy {
        try {
            FirebaseDatabase.getInstance(DATABASE_URL)
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val ridesRef by lazy { database.getReference("rides") }

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
     * Creates a new ride session in Firebase with the given rider as the initial creator,
     * and optionally attaches the planned trip details (start/destination/route geometry).
     * Returns a Result containing Pair(rideCode, riderId).
     */
    suspend fun createRide(
        riderName: String,
        tripInfo: TripInfo? = null
    ): Result<Pair<String, String>> {
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
            sessionRef.keepSynced(true)
            sessionRef.child("session").setValue(session)
                .addOnSuccessListener { Log.d("RideSafeDebug", "[FirebaseWrite] createRide session write SUCCESS: code=$rideCode") }
                .addOnFailureListener { e -> Log.e("RideSafeDebug", "[FirebaseWrite] createRide session write FAILED: ${e.message}", e) }
            sessionRef.child("riders").child(riderId).setValue(initialRider)
                .addOnSuccessListener { Log.d("RideSafeDebug", "[FirebaseWrite] createRide initialRider write SUCCESS: code=$rideCode, riderId=$riderId") }
                .addOnFailureListener { e -> Log.e("RideSafeDebug", "[FirebaseWrite] createRide initialRider write FAILED: ${e.message}", e) }

            // Write planned route (tripInfo) if provided
            if (tripInfo != null && tripInfo.isTripPlanned) {
                sessionRef.child("tripInfo").setValue(tripInfo)
                    .addOnSuccessListener { Log.d("RideSafeDebug", "[FirebaseWrite] createRide tripInfo write SUCCESS: code=$rideCode, start=${tripInfo.startName}, dest=${tripInfo.destName}") }
                    .addOnFailureListener { e -> Log.e("RideSafeDebug", "[FirebaseWrite] createRide tripInfo write FAILED: ${e.message}", e) }
            }

            Result.success(Pair(rideCode, riderId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observes the planned tripInfo node (start/dest and route geometry) in real-time.
     * All riders (both creator and joiners) use this to display the route polyline.
     */
    fun observeTripInfo(rideCode: String): Flow<TripInfo?> = callbackFlow {
        val cleanCode = rideCode.trim().uppercase()
        val tripRef = ridesRef.child(cleanCode).child("tripInfo")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val trip = if (snapshot.exists()) {
                    snapshot.getValue(TripInfo::class.java)
                } else {
                    null
                }
                Log.d("RideSafeDebug", "[FirebaseObserve] onDataChange tripInfo: $trip for $cleanCode")
                trySend(trip)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        tripRef.addValueEventListener(listener)
        awaitClose {
            tripRef.removeEventListener(listener)
        }
    }

    /**
     * Updates or sets tripInfo on an existing ride session.
     */
    fun saveTripInfo(rideCode: String, tripInfo: TripInfo) {
        val cleanCode = rideCode.trim().uppercase()
        ridesRef.child(cleanCode).child("tripInfo").setValue(tripInfo)
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

            // Enable local cache sync for this specific ride session
            ridesRef.child(cleanCode).keepSynced(true)
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
            // setValue writes to local cache and synchronizes to Firebase Realtime Database
            ridesRef.child(cleanCode).child("riders").child(riderId).setValue(rider)
                .addOnSuccessListener {
                    Log.d("RideSafeDebug", "[FirebaseWrite] joinRide rider write SUCCESS: code=$cleanCode, riderId=$riderId, name=${rider.name}")
                }
                .addOnFailureListener { e ->
                    Log.e("RideSafeDebug", "[FirebaseWrite] joinRide rider write FAILED: ${e.message}", e)
                }

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
        val cleanCode = rideCode.trim().uppercase()
        val updates = mapOf<String, Any>(
            "lat" to lat,
            "lng" to lng,
            "speed" to speed,
            "lastUpdated" to System.currentTimeMillis()
        )
        ridesRef.child(cleanCode).child("riders").child(riderId).updateChildren(updates) { error, _ ->
            if (error != null) {
                Log.e("RideSafeDebug", "[FirebaseWrite] updateLocation FAILED: ${error.message} (code: ${error.code}) for riderId=$riderId, ride=$cleanCode")
            } else {
                Log.d("RideSafeDebug", "[FirebaseWrite] updateLocation SUCCESS: riderId=$riderId, lat=$lat, lng=$lng, speed=$speed")
            }
        }
    }

    /**
     * Updates the current rider's stop status (e.g. Refueling, Emergency, Rest Stop).
     */
    fun updateStatus(rideCode: String, riderId: String, status: RiderStatus) {
        val cleanCode = rideCode.trim().uppercase()
        val updates = mapOf<String, Any>(
            "status" to status.name,
            "lastUpdated" to System.currentTimeMillis()
        )
        ridesRef.child(cleanCode).child("riders").child(riderId).updateChildren(updates)
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
                Log.d(
                    "RideSafeDebug",
                    "[FirebaseObserve] onDataChange: ${ridersList.size} riders received for $cleanCode -> ${ridersList.map { "${it.name}(id=${it.id}, lat=${it.lat}, lng=${it.lng})" }}"
                )
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
            val cleanCode = rideCode.trim().uppercase()
            ridesRef.child(cleanCode).child("riders").child(riderId).removeValue()
        } catch (e: Exception) {
            // Log or ignore network errors on exit
        }
    }

    /**
     * Checks whether a ride session is still active in Firebase Realtime Database.
     * A ride is active if its node exists, session.active is true, and it has at least one rider.
     */
    suspend fun isRideActive(rideCode: String): Boolean {
        return try {
            val cleanCode = rideCode.trim().uppercase()
            val rideNodeRef = ridesRef.child(cleanCode)

            val snapshot = withTimeoutOrNull(8000L) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (continuation.isActive) {
                                continuation.resume(dataSnapshot)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                    rideNodeRef.addListenerForSingleValueEvent(listener)
                    continuation.invokeOnCancellation {
                        rideNodeRef.removeEventListener(listener)
                    }
                }
            }

            if (snapshot == null || !snapshot.exists()) {
                return false
            }

            val sessionNode = snapshot.child("session")
            if (!sessionNode.exists()) {
                return false
            }

            val sessionActive = sessionNode.child("active").getValue(Boolean::class.java) ?: true
            val ridersNode = snapshot.child("riders")
            val hasRiders = ridersNode.exists() && ridersNode.childrenCount > 0

            hasRiders && sessionActive
        } catch (e: Exception) {
            Log.e("RideRepository", "Error checking isRideActive for $rideCode", e)
            false
        }
    }

    /**
     * Re-registers an existing rider into an active ride session using their saved riderId.
     */
    suspend fun rejoinRide(rideCode: String, riderId: String, riderName: String): Result<Unit> {
        return try {
            val cleanCode = rideCode.trim().uppercase()
            ridesRef.child(cleanCode).keepSynced(true)
            val sessionRef = ridesRef.child(cleanCode).child("session")

            val snapshot = withTimeoutOrNull(10000L) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (continuation.isActive) {
                                continuation.resume(dataSnapshot)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(error.toException())
                            }
                        }
                    }
                    sessionRef.addListenerForSingleValueEvent(listener)
                    continuation.invokeOnCancellation {
                        sessionRef.removeEventListener(listener)
                    }
                }
            }

            if (snapshot == null) {
                return Result.failure(Exception("Could not connect to Firebase. Check internet connection."))
            }

            if (!snapshot.exists()) {
                return Result.failure(IllegalArgumentException("Ride '$cleanCode' not found. It may have been ended."))
            }

            val rider = Rider(
                id = riderId,
                name = riderName.trim().ifEmpty { "Rider" },
                status = RiderStatus.RIDING.name,
                lastUpdated = System.currentTimeMillis()
            )

            ridesRef.child(cleanCode).child("riders").child(riderId).setValue(rider)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("RideRepository", "rejoinRide failed for $rideCode", e)
            Result.failure(e)
        }
    }
}
