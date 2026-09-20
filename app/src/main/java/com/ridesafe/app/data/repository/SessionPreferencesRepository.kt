package com.ridesafe.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ridesafe.app.data.model.LocalRideSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ride_session_prefs")

/**
 * SessionPreferencesRepository manages local storage of ride sessions using Jetpack DataStore Preferences.
 * It persists ride codes, rider IDs, display names, and join timestamps across app restarts.
 */
class SessionPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_SESSIONS = stringPreferencesKey("saved_sessions_json")
        private val KEY_LAST_RIDER_NAME = stringPreferencesKey("last_rider_name")
        private const val MAX_SAVED_SESSIONS = 50
    }

    /**
     * Emits the list of locally saved ride sessions, ordered by timestamp descending.
     */
    val sessionsFlow: Flow<List<LocalRideSession>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonString = preferences[KEY_SESSIONS] ?: return@map emptyList()
            parseSessions(jsonString)
        }

    /**
     * Emits the last used rider display name.
     */
    val lastRiderNameFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_LAST_RIDER_NAME] ?: ""
        }

    /**
     * Saves or updates a ride session in DataStore.
     * If the rideCode already exists, it is replaced and moved to the front (most recent).
     */
    suspend fun saveSession(session: LocalRideSession) {
        context.dataStore.edit { preferences ->
            val existingList = parseSessions(preferences[KEY_SESSIONS] ?: "").toMutableList()

            // Remove previous entry for the same ride code to avoid duplicates
            existingList.removeAll { it.rideCode.equals(session.rideCode, ignoreCase = true) }

            // Add new session to top of list
            existingList.add(0, session)

            // Cap at MAX_SAVED_SESSIONS
            val trimmedList = if (existingList.size > MAX_SAVED_SESSIONS) {
                existingList.take(MAX_SAVED_SESSIONS)
            } else {
                existingList
            }

            preferences[KEY_SESSIONS] = serializeSessions(trimmedList)

            if (session.riderName.isNotBlank()) {
                preferences[KEY_LAST_RIDER_NAME] = session.riderName.trim()
            }
        }
    }

    /**
     * Removes a session from local storage by ride code.
     * Does NOT touch any remote Firebase data.
     */
    suspend fun removeSession(rideCode: String) {
        context.dataStore.edit { preferences ->
            val existingList = parseSessions(preferences[KEY_SESSIONS] ?: "")
            val updatedList = existingList.filterNot { it.rideCode.equals(rideCode, ignoreCase = true) }
            preferences[KEY_SESSIONS] = serializeSessions(updatedList)
        }
    }

    /**
     * Saves the last entered rider name for convenient pre-filling.
     */
    suspend fun saveLastRiderName(name: String) {
        if (name.isBlank()) return
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_RIDER_NAME] = name.trim()
        }
    }

    private fun parseSessions(jsonString: String): List<LocalRideSession> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = ArrayList<LocalRideSession>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(LocalRideSession.fromJson(obj))
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeSessions(sessions: List<LocalRideSession>): String {
        val array = JSONArray()
        for (session in sessions) {
            array.put(session.toJson())
        }
        return array.toString()
    }
}
