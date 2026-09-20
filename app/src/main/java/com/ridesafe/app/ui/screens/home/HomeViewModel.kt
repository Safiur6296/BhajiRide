package com.ridesafe.app.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ridesafe.app.data.model.LocalRideSession
import com.ridesafe.app.data.model.LocalRideSessionUi
import com.ridesafe.app.data.repository.RideRepository
import com.ridesafe.app.data.repository.SessionPreferencesRepository
import com.ridesafe.app.service.LocationTrackingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val riderName: String = "",
    val joinCode: String = "",
    val isCreatingRide: Boolean = false,
    val isJoiningRide: Boolean = false,
    val rejoiningCode: String? = null,
    val errorMessage: String? = null,
    val sessions: List<LocalRideSessionUi> = emptyList(),
    val isLoadingSessions: Boolean = true
)

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val sessionPrefs = SessionPreferencesRepository(application)
    private val rideRepository = RideRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var statusCheckJob: Job? = null

    init {
        // Pre-fill last used rider name
        viewModelScope.launch {
            val savedName = sessionPrefs.lastRiderNameFlow.first()
            if (savedName.isNotBlank() && _uiState.value.riderName.isBlank()) {
                _uiState.update { it.copy(riderName = savedName) }
            }
        }

        // Observe local sessions from Jetpack DataStore
        viewModelScope.launch {
            sessionPrefs.sessionsFlow.collect { localList ->
                checkAndUpdateSessionsStatus(localList)
            }
        }
    }

    fun onRiderNameChange(name: String) {
        _uiState.update { it.copy(riderName = name, errorMessage = null) }
        viewModelScope.launch {
            sessionPrefs.saveLastRiderName(name)
        }
    }

    fun onJoinCodeChange(code: String) {
        if (code.length <= 8) {
            _uiState.update { it.copy(joinCode = code.uppercase(), errorMessage = null) }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            val localList = sessionPrefs.sessionsFlow.first()
            checkAndUpdateSessionsStatus(localList)
        }
    }

    private fun checkAndUpdateSessionsStatus(localList: List<LocalRideSession>) {
        statusCheckJob?.cancel()
        statusCheckJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true) }

            if (localList.isEmpty()) {
                _uiState.update { it.copy(sessions = emptyList(), isLoadingSessions = false) }
                return@launch
            }

            // Check Firebase active status for each session in parallel
            val statusMap = localList.map { session ->
                async {
                    val active = rideRepository.isRideActive(session.rideCode)
                    session.rideCode to active
                }
            }.awaitAll().toMap()

            // Build UI models and sort: Active first, then by timestamp descending
            val uiSessions = localList.map { session ->
                LocalRideSessionUi(
                    session = session,
                    isActive = statusMap[session.rideCode] ?: false,
                    isChecking = false
                )
            }.sortedWith(
                compareByDescending<LocalRideSessionUi> { it.isActive }
                    .thenByDescending { it.timestamp }
            )

            _uiState.update {
                it.copy(
                    sessions = uiSessions,
                    isLoadingSessions = false
                )
            }
        }
    }

    fun createRide(
        context: Context,
        onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
    ) {
        val name = _uiState.value.riderName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name first.") }
            return
        }

        _uiState.update { it.copy(isCreatingRide = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val result = rideRepository.createRide(name)
                result.onSuccess { (rideCode, riderId) ->
                    // Save to local DataStore
                    sessionPrefs.saveSession(
                        LocalRideSession(
                            rideCode = rideCode,
                            riderId = riderId,
                            riderName = name,
                            timestamp = System.currentTimeMillis(),
                            isHost = true
                        )
                    )

                    // Start background GPS tracking service
                    LocationTrackingService.startTracking(
                        context = context,
                        rideCode = rideCode,
                        riderId = riderId,
                        riderName = name
                    )

                    onRideJoined(rideCode, riderId, name)
                }.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to create ride.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "An unexpected error occurred.") }
            } finally {
                _uiState.update { it.copy(isCreatingRide = false) }
            }
        }
    }

    fun joinRide(
        context: Context,
        onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
    ) {
        val name = _uiState.value.riderName.trim()
        val code = _uiState.value.joinCode.trim().uppercase()

        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name first.") }
            return
        }
        if (code.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter the ride code.") }
            return
        }

        _uiState.update { it.copy(isJoiningRide = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val result = rideRepository.joinRide(code, name)
                result.onSuccess { riderId ->
                    // Save to local DataStore
                    sessionPrefs.saveSession(
                        LocalRideSession(
                            rideCode = code,
                            riderId = riderId,
                            riderName = name,
                            timestamp = System.currentTimeMillis(),
                            isHost = false
                        )
                    )

                    // Start background GPS tracking service
                    LocationTrackingService.startTracking(
                        context = context,
                        rideCode = code,
                        riderId = riderId,
                        riderName = name
                    )

                    onRideJoined(code, riderId, name)
                }.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to join ride.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "An unexpected error occurred.") }
            } finally {
                _uiState.update { it.copy(isJoiningRide = false) }
            }
        }
    }

    fun rejoinRide(
        context: Context,
        session: LocalRideSession,
        onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
    ) {
        _uiState.update { it.copy(rejoiningCode = session.rideCode, errorMessage = null) }

        viewModelScope.launch {
            try {
                val displayName = if (_uiState.value.riderName.isNotBlank()) {
                    _uiState.value.riderName.trim()
                } else {
                    session.riderName
                }

                val result = rideRepository.rejoinRide(
                    rideCode = session.rideCode,
                    riderId = session.riderId,
                    riderName = displayName
                )

                result.onSuccess {
                    // Update timestamp in DataStore
                    sessionPrefs.saveSession(
                        session.copy(
                            timestamp = System.currentTimeMillis(),
                            riderName = displayName
                        )
                    )

                    // Start background GPS tracking service
                    LocationTrackingService.startTracking(
                        context = context,
                        rideCode = session.rideCode,
                        riderId = session.riderId,
                        riderName = displayName
                    )

                    onRideJoined(session.rideCode, session.riderId, displayName)
                }.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to rejoin ride.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "An error occurred while rejoining.") }
            } finally {
                _uiState.update { it.copy(rejoiningCode = null) }
            }
        }
    }

    fun deleteSession(rideCode: String) {
        viewModelScope.launch {
            sessionPrefs.removeSession(rideCode)
        }
    }
}
