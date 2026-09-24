package com.ridesafe.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import com.ridesafe.app.BuildConfig
import com.ridesafe.app.data.model.AppUpdateInfo
import com.ridesafe.app.data.repository.RideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * AppUpdateManager handles seamless in-app checking, downloading, and installing
 * of APK updates directly within BhaijiRide without relying on Firebase App Tester.
 */
class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "BhaijiRideUpdate"
        private const val GITHUB_REPO = "Safiur6296/BhajiRide"
        private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

        /**
         * Parses numeric build number from release tag like "v1.0.17" -> 27L (accounting for buildNumber + 10).
         */
        fun extractVersionCodeFromTag(tag: String): Long {
            val cleaned = tag.removePrefix("v").trim()
            val parts = cleaned.split(".")
            val buildNum = parts.lastOrNull()?.toLongOrNull() ?: 0L
            return if (buildNum > 0) buildNum + 10L else 0L
        }

        /**
         * Compares semantic versions (e.g. "v1.0.17" vs "1.0.15").
         * Returns true if remoteVersion is strictly newer than currentVersion.
         */
        fun isVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
            val remoteParts = remoteVersion.removePrefix("v").trim().split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = currentVersion.removePrefix("v").trim().split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }

    private val database by lazy {
        try {
            FirebaseDatabase.getInstance(RideRepository.DATABASE_URL)
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
    }

    /**
     * Checks whether an update is available by inspecting:
     * 1. Firebase Realtime Database at /app_update
     * 2. (Fallback) GitHub Releases latest asset
     */
    suspend fun checkForUpdates(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        Log.d(TAG, "Checking for updates... Current local versionCode: $currentVersionCode (${BuildConfig.VERSION_NAME})")

        // 1. Check Firebase Realtime Database
        try {
            val snapshot = database.getReference("app_update").get().await()
            if (snapshot.exists()) {
                val remoteVersionCode = snapshot.child("versionCode").getValue(Long::class.java) ?: 0L
                val downloadUrl = snapshot.child("downloadUrl").getValue(String::class.java).orEmpty()
                val versionName = snapshot.child("versionName").getValue(String::class.java).orEmpty()
                val releaseNotes = snapshot.child("releaseNotes").getValue(String::class.java)
                    ?: "Performance improvements and bug fixes."
                val forceUpdate = snapshot.child("forceUpdate").getValue(Boolean::class.java) ?: false

                if (remoteVersionCode > currentVersionCode && downloadUrl.isNotBlank()) {
                    Log.i(TAG, "Update found via Firebase: v$versionName (code $remoteVersionCode)")
                    return@withContext AppUpdateInfo(
                        versionCode = remoteVersionCode,
                        versionName = versionName.ifEmpty { "1.0.$remoteVersionCode" },
                        downloadUrl = downloadUrl,
                        releaseNotes = releaseNotes,
                        forceUpdate = forceUpdate
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase update check note: ${e.message}")
        }

        // 2. Fallback: Query GitHub Releases API
        try {
            val connection = (URL(GITHUB_API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "BhaijiRide-Android")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val tagName = json.optString("tag_name", "") // e.g. "v1.0.12" or "1.0.12"
                val body = json.optString("body", "What's new in this release.")
                val assets = json.optJSONArray("assets")

                // Extract numeric version code and check semantic version
                val parsedCode = extractVersionCodeFromTag(tagName)
                val isNewer = isVersionNewer(tagName, BuildConfig.VERSION_NAME) || (parsedCode > currentVersionCode)

                if (assets != null && assets.length() > 0 && isNewer) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (downloadUrl.isNotBlank()) {
                                Log.i(TAG, "Update found via GitHub Releases: $tagName (code $parsedCode vs current $currentVersionCode, current ver: ${BuildConfig.VERSION_NAME})")
                                return@withContext AppUpdateInfo(
                                    versionCode = parsedCode,
                                    versionName = tagName.removePrefix("v"),
                                    downloadUrl = downloadUrl,
                                    releaseNotes = body,
                                    forceUpdate = false
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub Releases check note: ${e.message}")
        }

        Log.d(TAG, "App is up to date.")
        null
    }

    /**
     * Downloads the APK file to the app's cache directory while reporting progress.
     */
    suspend fun downloadApk(
        info: AppUpdateInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "BhaijiRide-${info.versionName}.apk")

            // If previously downloaded and size matches, reuse it
            val connection = openConnectionWithRedirects(info.downloadUrl)
            val totalBytes = connection.contentLengthLong

            if (apkFile.exists() && totalBytes > 0 && apkFile.length() == totalBytes) {
                Log.d(TAG, "Using existing cached APK file: ${apkFile.absolutePath}")
                onProgress(1f, totalBytes, totalBytes)
                return@withContext Result.success(apkFile)
            }

            apkFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesDownloaded = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        val progress = if (totalBytes > 0) {
                            (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                        } else {
                            -1f // indeterminate
                        }
                        onProgress(progress, bytesDownloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            Log.i(TAG, "APK download completed: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading update APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Triggers the native Android package installer for the downloaded APK file.
     * Handles Unknown App Sources permission check on Android 8.0+.
     */
    fun installApk(apkFile: File): Boolean {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "Cannot install: APK file does not exist at ${apkFile.absolutePath}")
                return false
            }

            // Android 8.0+ (API 26) permission to install unknown apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer: ${e.message}", e)
            return false
        }
    }

    /**
     * Follows HTTP/HTTPS redirects safely (e.g. GitHub Releases redirecting to AWS S3).
     */
    @Throws(IOException::class)
    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var url = initialUrl
        var connection: HttpURLConnection
        var redirectCount = 0
        val maxRedirects = 6

        while (redirectCount < maxRedirects) {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BhaijiRide-App")
            }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == 307 || status == 308
            ) {
                val newUrl = connection.getHeaderField("Location")
                if (newUrl != null) {
                    connection.disconnect()
                    url = newUrl
                    redirectCount++
                    continue
                }
            }
            return connection
        }
        throw IOException("Too many redirects while accessing $initialUrl")
    }
}
