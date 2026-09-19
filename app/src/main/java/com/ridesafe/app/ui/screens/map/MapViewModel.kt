package com.ridesafe.app.ui.screens.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.ridesafe.app.data.model.Rider
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.data.repository.RideRepository
import com.ridesafe.app.service.LocationTrackingService
import com.ridesafe.app.util.LocationUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RiderWithDistance pairs a Rider with their calculated distance and formatted string
 * relative to the current app user.
 */
data class RiderWithDistance(
    val rider: Rider,
    val distanceMeters: Float = 0f,
    val formattedDistance: String = "",
    val isCurrentUser: Boolean = false
)

/**
 * UI State for the Live Map Screen.
 * In Jetpack Compose, UI is a function of State. Whenever this state changes,
 * Compose automatically recomposes only the elements that need updating.
 */
data class MapUiState(
    val rideCode: String = "",
    val currentRiderId: String = "",
    val currentRiderName: String = "",
    val myStatus: RiderStatus = RiderStatus.RIDING,
    val riders: List<RiderWithDistance> = emptyList(),
    val selectedRider: RiderWithDistance? = null,
    val isStatusPickerOpen: Boolean = false,
    val isRiderListOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MapViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = RideRepository()
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var riderObservationJob: Job? = null

    /**
     * Initializes the map session for a given rideCode and rider.
     */
    fun initSession(rideCode: String, riderId: String, riderName: String) {
        val cleanCode = rideCode.trim().uppercase()

        // Guard against duplicate initialization resetting state on recomposition
        if (_uiState.value.rideCode == cleanCode && _uiState.value.currentRiderId == riderId && _uiState.value.riders.isNotEmpty()) {
            return
        }

        val initialUser = Rider(
            id = riderId,
            name = riderName,
            status = RiderStatus.RIDING.name,
            lastUpdated = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                rideCode = cleanCode,
                currentRiderId = riderId,
                currentRiderName = riderName,
                myStatus = RiderStatus.RIDING,
                riders = listOf(
                    RiderWithDistance(
                        rider = initialUser,
                        distanceMeters = 0f,
                        formattedDistance = "You",
                        isCurrentUser = true
                    )
                ),
                isLoading = false
            )
        }

        // Fetch initial GPS location right away for instant map marker display
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val updatedUser = initialUser.copy(
                        lat = location.latitude,
                        lng = location.longitude,
                        speed = location.speed
                    )
                    _uiState.update { state ->
                        state.copy(
                            riders = state.riders.map { item ->
                                if (item.isCurrentUser) item.copy(rider = updatedUser) else item
                            }
                        )
                    }
                    repository.updateLocation(
                        rideCode = cleanCode,
                        riderId = riderId,
                        lat = location.latitude,
                        lng = location.longitude,
                        speed = location.speed
                    )
                }
            }
        } catch (e: SecurityException) {
        }

        // Start listening to live updates from Firebase
        observeRiders()
    }

    private fun observeRiders() {
        riderObservationJob?.cancel()
        val rideCode = _uiState.value.rideCode

        riderObservationJob = viewModelScope.launch {
            repository.observeRiders(rideCode)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                }
                .collect { ridersList ->
                    processRiders(ridersList)
                }
        }
    }

    private fun processRiders(ridersList: List<Rider>) {
        val myId = _uiState.value.currentRiderId
        val currentRider = ridersList.find { it.id == myId }
        val myStatus = currentRider?.riderStatus ?: _uiState.value.myStatus

        // Calculate distance from current rider to every other rider in the group
        val ridersWithDistance = ridersList.map { rider ->
            val isMe = rider.id == myId
            val distanceMeters = if (isMe || currentRider == null || rider.lat == 0.0 || currentRider.lat == 0.0) {
                0f
            } else {
                LocationUtils.calculateDistanceMeters(
                    startLat = currentRider.lat,
                    startLng = currentRider.lng,
                    endLat = rider.lat,
                    endLng = rider.lng
                )
            }

            val formattedDist = if (isMe) {
                "You"
            } else if (rider.lat == 0.0) {
                "Locating..."
            } else {
                LocationUtils.formatDistance(distanceMeters)
            }

            RiderWithDistance(
                rider = rider,
                distanceMeters = distanceMeters,
                formattedDistance = formattedDist,
                isCurrentUser = isMe
            )
        }.sortedWith(
            // Show current user first, then sort other riders by distance
            compareByDescending<RiderWithDistance> { it.isCurrentUser }
                .thenBy { it.distanceMeters }
        )

        _uiState.update {
            it.copy(
                riders = ridersWithDistance,
                myStatus = myStatus,
                isLoading = false
            )
        }
    }

    /**
     * Updates the current rider's status (e.g. Refueling, Emergency, Riding).
     * Synchronizes to both Firebase and the Foreground Service notification.
     */
    fun setRiderStatus(status: RiderStatus) {
        val rideCode = _uiState.value.rideCode
        val riderId = _uiState.value.currentRiderId

        _uiState.update { it.copy(myStatus = status, isStatusPickerOpen = false) }

        // Update in Firebase Realtime Database
        if (rideCode.isNotEmpty() && riderId.isNotEmpty()) {
            repository.updateStatus(rideCode, riderId, status)
        }

        // Update Foreground Service notification
        LocationTrackingService.updateStatus(getApplication(), status)
    }

    fun openStatusPicker() {
        _uiState.update { it.copy(isStatusPickerOpen = true) }
    }

    fun closeStatusPicker() {
        _uiState.update { it.copy(isStatusPickerOpen = false) }
    }

    fun openRiderList() {
        _uiState.update { it.copy(isRiderListOpen = true) }
    }

    fun closeRiderList() {
        _uiState.update { it.copy(isRiderListOpen = false) }
    }

    fun selectRider(rider: RiderWithDistance?) {
        _uiState.update { it.copy(selectedRider = rider) }
    }

    /**
     * Leaves the ride session cleanly, stops the foreground service, and clears observers.
     */
    fun leaveRide(onLeaveComplete: () -> Unit) {
        val rideCode = _uiState.value.rideCode
        val riderId = _uiState.value.currentRiderId

        // 1. Stop foreground tracking service immediately
        LocationTrackingService.stopTracking(getApplication())

        // 2. Clear real-time observation job
        riderObservationJob?.cancel()
        riderObservationJob = null

        // 3. Remove rider from Firebase asynchronously
        if (rideCode.isNotEmpty() && riderId.isNotEmpty()) {
            repository.leaveRide(rideCode, riderId)
        }

        // 4. Return to Home screen immediately
        onLeaveComplete()
    }

    override fun onCleared() {
        super.onCleared()
        riderObservationJob?.cancel()
    }
}
