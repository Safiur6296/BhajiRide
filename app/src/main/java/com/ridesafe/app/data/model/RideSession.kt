package com.ridesafe.app.data.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * RideSession represents the metadata of a group ride session in Firebase.
 */
@IgnoreExtraProperties
data class RideSession(
    val code: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val active: Boolean = true
)
