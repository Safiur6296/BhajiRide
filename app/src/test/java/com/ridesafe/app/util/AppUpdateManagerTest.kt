package com.ridesafe.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testExtractVersionCodeFromTag() {
        // Tag format v1.0.17 -> buildNumber 17 + 10 = 27
        assertEquals(27L, AppUpdateManager.extractVersionCodeFromTag("v1.0.17"))
        assertEquals(28L, AppUpdateManager.extractVersionCodeFromTag("v1.0.18"))
        assertEquals(25L, AppUpdateManager.extractVersionCodeFromTag("1.0.15"))
        assertEquals(0L, AppUpdateManager.extractVersionCodeFromTag("invalid"))
        assertEquals(0L, AppUpdateManager.extractVersionCodeFromTag(""))
    }

    @Test
    fun testIsVersionNewer() {
        // Newer patch version
        assertTrue(AppUpdateManager.isVersionNewer("v1.0.18", "1.0.15"))
        assertTrue(AppUpdateManager.isVersionNewer("1.0.18", "1.0.17"))
        assertTrue(AppUpdateManager.isVersionNewer("1.0.10", "1.0.9"))
        assertTrue(AppUpdateManager.isVersionNewer("v1.1.0", "1.0.17"))
        assertTrue(AppUpdateManager.isVersionNewer("v2.0.0", "1.9.99"))

        // Same version
        assertFalse(AppUpdateManager.isVersionNewer("v1.0.18", "1.0.18"))
        assertFalse(AppUpdateManager.isVersionNewer("1.0.18", "1.0.18"))
        assertFalse(AppUpdateManager.isVersionNewer("1.0.0", "1.0"))

        // Older version
        assertFalse(AppUpdateManager.isVersionNewer("v1.0.15", "1.0.18"))
        assertFalse(AppUpdateManager.isVersionNewer("v1.0.1", "1.0.17"))
        assertFalse(AppUpdateManager.isVersionNewer("1.0.9", "1.0.10"))
    }
}
