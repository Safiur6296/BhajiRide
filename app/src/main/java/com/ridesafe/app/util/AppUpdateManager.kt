package com.ridesafe.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
        private const val GITHUB_LATEST_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=1"

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
     * Directory used for storing downloaded APKs before invoking PackageInstaller.
     * Uses external downloads directory so system package installer service has access.
     */
    fun getUpdateApkFile(versionName: String): File {
        val updatesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.externalCacheDir ?: context.cacheDir, "apk_updates")
        updatesDir.mkdirs()
        return File(updatesDir, "BhaijiRide-$versionName.apk")
    }

    /**
     * Checks if a valid cached APK already exists for the given update.
     */
    fun getCachedApk(info: AppUpdateInfo): File? {
        val file = getUpdateApkFile(info.versionName)
        return if (file.exists() && file.length() > 5 * 1024 * 1024) file else null
    }

    /**
     * Checks whether an update is available by inspecting:
     * 1. Firebase Realtime Database at /app_update (with 2s timeout)
     * 2. GitHub Releases API (latest release asset)
     *
     * @param forceCheck If true, returns the latest remote build info even if current is up-to-date (useful for reinstall/test)
     */
    suspend fun checkForUpdates(forceCheck: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        val currentVersionName = BuildConfig.VERSION_NAME
        Log.d(TAG, "Checking for updates... Current local versionCode: $currentVersionCode ($currentVersionName)")

        // 1. Check Firebase Realtime Database (with fast timeout)
        try {
            val fbUpdate = withTimeoutOrNull(2000L) {
                val snapshot = database.getReference("app_update").get().await()
                if (snapshot.exists()) {
                    val remoteVersionCode = snapshot.child("versionCode").getValue(Long::class.java) ?: 0L
                    val downloadUrl = snapshot.child("downloadUrl").getValue(String::class.java).orEmpty()
                    val versionName = snapshot.child("versionName").getValue(String::class.java).orEmpty()
                    val releaseNotes = snapshot.child("releaseNotes").getValue(String::class.java)
                        ?: "Performance improvements and bug fixes."
                    val forceUpdate = snapshot.child("forceUpdate").getValue(Boolean::class.java) ?: false

                    val isNewer = (remoteVersionCode > currentVersionCode) || isVersionNewer(versionName, currentVersionName)
                    if ((isNewer || forceCheck) && downloadUrl.isNotBlank()) {
                        Log.i(TAG, "Update found via Firebase: v$versionName (code $remoteVersionCode)")
                        return@withTimeoutOrNull AppUpdateInfo(
                            versionCode = remoteVersionCode,
                            versionName = versionName.ifEmpty { "1.0.$remoteVersionCode" },
                            downloadUrl = downloadUrl,
                            releaseNotes = releaseNotes,
                            forceUpdate = forceUpdate
                        )
                    }
                }
                null
            }
            if (fbUpdate != null) {
                return@withContext fbUpdate
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firebase update check note: ${e.message}")
        }

        // 2. Query GitHub Releases API
        val endpoints = listOf(GITHUB_LATEST_API_URL, GITHUB_RELEASES_API_URL)
        for (apiUrl in endpoints) {
            try {
                val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "BhaijiRide-Android")
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                    val releaseObj: JSONObject? = if (responseText.trim().startsWith("[")) {
                        val arr = JSONArray(responseText)
                        if (arr.length() > 0) arr.getJSONObject(0) else null
                    } else {
                        JSONObject(responseText)
                    }

                    if (releaseObj != null) {
                        val tagName = releaseObj.optString("tag_name", "")
                        val body = releaseObj.optString("body", "What's new in this release.")
                        val assets = releaseObj.optJSONArray("assets")

                        val parsedCode = extractVersionCodeFromTag(tagName)
                        val isNewer = isVersionNewer(tagName, currentVersionName) || (parsedCode > currentVersionCode)

                        if (assets != null && assets.length() > 0 && (isNewer || forceCheck)) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    val downloadUrl = asset.optString("browser_download_url", "")
                                    if (downloadUrl.isNotBlank()) {
                                        Log.i(TAG, "Update found via GitHub Releases: $tagName (code $parsedCode vs current $currentVersionCode, current ver: $currentVersionName)")
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
                        // If we inspected the latest release object and it wasn't newer, break unless forceCheck
                        if (!isNewer && !forceCheck) {
                            Log.d(TAG, "GitHub latest release $tagName is not newer than current $currentVersionName (code $currentVersionCode)")
                            return@withContext null
                        }
                    }
                } else {
                    Log.w(TAG, "GitHub API returned HTTP $responseCode for $apiUrl")
                }
            } catch (e: Exception) {
                Log.w(TAG, "GitHub Releases check note for $apiUrl: ${e.message}")
            }
        }

        Log.d(TAG, "App is up to date.")
        null
    }

    /**
     * Downloads the APK file to external app storage while reporting progress.
     */
    suspend fun downloadApk(
        info: AppUpdateInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val apkFile = getUpdateApkFile(info.versionName)

            // Connect following redirects
            val connection = openConnectionWithRedirects(info.downloadUrl)
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IOException("Server returned HTTP $statusCode: ${connection.responseMessage}")
            }

            val totalBytes = connection.contentLengthLong

            // If previously downloaded and size matches, reuse it
            if (apkFile.exists() && totalBytes > 0 && apkFile.length() == totalBytes) {
                Log.d(TAG, "Using existing cached APK file: ${apkFile.absolutePath}")
                apkFile.setReadable(true, false)
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

            // Ensure readable by Android package installer system service
            apkFile.setReadable(true, false)

            Log.i(TAG, "APK download completed: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading update APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Returns true if the app currently has permission to install unknown APKs.
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Launches the system settings screen where the user can enable unknown app sources for BhaijiRide.
     */
    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
        }
    }

    /**
     * Triggers the native Android package installer for the downloaded APK file.
     * Grants URI permissions explicitly to all resolved package installer activities.
     */
    fun installApk(apkFile: File): Boolean {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Log.e(TAG, "Cannot install: APK file does not exist or is empty at ${apkFile.absolutePath}")
                return false
            }

            apkFile.setReadable(true, false)

            // Android 8.0+ permission check
            if (!canInstallPackages()) {
                requestInstallPermission()
                return false
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Explicitly grant URI read & write permissions to all installer activities
            val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    installIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            for (resolveInfo in resolveList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer: ${e.message}", e)
            return false
        }
    }

    /**
     * Follows HTTP/HTTPS redirects safely (e.g. GitHub Releases redirecting to AWS S3 / Azure Blob).
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
                status == HttpURLConnection.HTTP_SEE_OTHER ||
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
