package com.ridesafe.app.data.model

/**
 * Metadata representing an available application update.
 *
 * Can be fetched from Firebase Realtime Database (/app_update) or GitHub Releases API.
 *
 * @param versionCode Remote build number. Compared against local BuildConfig.VERSION_CODE.
 * @param versionName Human readable version string (e.g. "1.0.12").
 * @param downloadUrl Direct link to download the updated APK file.
 * @param releaseNotes Summary of changes, new features, or bug fixes.
 * @param forceUpdate If true, prevents dismissing the dialog until updated.
 */
data class AppUpdateInfo(
    val versionCode: Long = 0L,
    val versionName: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val forceUpdate: Boolean = false
)
