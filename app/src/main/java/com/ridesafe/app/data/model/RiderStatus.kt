package com.ridesafe.app.data.model

import androidx.compose.ui.graphics.Color

/**
 * RiderStatus represents the current state of a motorbike rider during a group ride.
 *
 * Kotlin enum classes can contain constructor parameters, methods, and companion helpers.
 * In Java, you would write this with private final fields, getters, and switch statements.
 */
enum class RiderStatus(
    val displayName: String,
    val emoji: String,
    val color: Color
) {
    RIDING("Riding", "🏍️", Color(0xFF00E676)),         // Vibrant Green: Moving normally
    REFUELING("Refueling", "⛽", Color(0xFFFFB300)),     // Amber/Yellow: Fuel stop
    PUNCTURE("Tire Puncture", "🔧", Color(0xFFFF7043)), // Orange: Flat tire or mechanical issue
    REST("Rest Stop", "☕", Color(0xFF40C4FF)),         // Light Blue: Coffee or breather
    EMERGENCY("Emergency", "🚨", Color(0xFFFF1744)),     // Bright Red: Accident or urgent help needed
    OTHER("Other Stop", "⚠️", Color(0xFFB0BEC5));       // Slate Grey: Miscellaneous pause

    companion object {
        /**
         * Safely parses a string into a RiderStatus, defaulting to RIDING if invalid or empty.
         */
        fun fromString(value: String?): RiderStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RIDING
        }
    }
}
