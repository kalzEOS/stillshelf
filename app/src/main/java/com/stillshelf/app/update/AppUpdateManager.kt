package com.stillshelf.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.util.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class AppUpdateRelease(
    val tagName: String,
    val versionName: String,
    val htmlUrl: String,
    val body: String?,
    val apkDownloadUrl: String?
)

@Singleton
class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val sessionPreferences: SessionPreferences
) {
    companion object {
        private const val RELEASES_API_URL = "https://api.github.com/repos/kalzEOS/stillshelf/releases"
        private const val RELEASES_PAGE_URL = "https://github.com/kalzEOS/stillshelf/releases"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }

    suspend fun checkForUpdate(includePrereleases: Boolean): AppResult<AppUpdateRelease?> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(RELEASES_API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "StillShelf/${installedVersionName()}")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Unable to check updates (${response.code}).")
                    }
                    val responseBody = response.body?.string().orEmpty()
                    val releases = parseReleases(responseBody)
                    val currentVersion = SemanticVersion.parse(installedVersionName())
                    val latest = releases
                        .asSequence()
                        .filter { includePrereleases || !it.isPrerelease }
                        .sortedWith(compareByDescending<ParsedRelease> { it.version })
                        .firstOrNull { parsed ->
                            if (currentVersion == null) {
                                true
                            } else {
                                parsed.version > currentVersion
                            }
                        }
                    latest?.toRelease()
                }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Error(it.message ?: "Unable to check updates.", it) }
            )
        }
    }

    suspend fun downloadAndInstallUpdate(release: AppUpdateRelease): AppResult<Unit> {
        return withContext(Dispatchers.IO) {
            val downloadUrl = release.apkDownloadUrl
            if (downloadUrl.isNullOrBlank()) {
                openReleasePage(release.htmlUrl)
                return@withContext AppResult.Success(Unit)
            }

            runCatching {
                val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                val safeVersion = release.versionName
                    .ifBlank { release.tagName }
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val apkFile = File(updatesDir, "stillshelf-$safeVersion.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "StillShelf/${installedVersionName()}")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed (${response.code}).")
                    }
                    val body = response.body ?: throw IllegalStateException("Download returned empty data.")
                    apkFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                if (!apkFile.exists() || apkFile.length() <= 0L) {
                    throw IllegalStateException("Downloaded APK is invalid.")
                }
                sessionPreferences.setPendingUpdateInstall(
                    apkPath = apkFile.absolutePath,
                    versionName = release.versionName
                )
                if (!launchInstaller(apkFile)) {
                    throw IllegalStateException("Unable to open package installer on this device.")
                }
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { throwable ->
                    AppResult.Error(throwable.message ?: "Unable to update app.", throwable)
                }
            )
        }
    }

    suspend fun cleanupInstalledUpdateApkIfNeeded() {
        val pref = sessionPreferences.state.first()
        val apkPath = pref.pendingUpdateApkPath.orEmpty().trim()
        if (apkPath.isBlank()) return
        val apkFile = File(apkPath)
        val targetVersion = SemanticVersion.parse(pref.pendingUpdateVersionName)
        val currentVersion = SemanticVersion.parse(installedVersionName())
        val didUpgrade = when {
            targetVersion == null || currentVersion == null -> false
            else -> currentVersion >= targetVersion
        }
        if (!apkFile.exists() || didUpgrade) {
            runCatching { apkFile.delete() }
            sessionPreferences.setPendingUpdateInstall(apkPath = null, versionName = null)
        }
    }

    fun releasesPageUrl(): String = RELEASES_PAGE_URL

    fun openReleasePage(url: String = RELEASES_PAGE_URL) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    private fun launchInstaller(apkFile: File): Boolean {
        val authority = "${appContext.packageName}.fileprovider"
        val apkUri = runCatching {
            FileProvider.getUriForFile(appContext, authority, apkFile)
        }.getOrNull() ?: return false

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (runCatching { appContext.startActivity(installIntent) }.isSuccess) {
            return true
        }
        val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            appContext.startActivity(fallbackIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun installedVersionName(): String {
        return runCatching {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            packageInfo.versionName.orEmpty()
        }.getOrDefault("")
    }

    private fun parseReleases(raw: String): List<ParsedRelease> {
        if (raw.isBlank()) return emptyList()
        val source = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val releases = mutableListOf<ParsedRelease>()
        for (index in 0 until source.length()) {
            val node = source.optJSONObject(index) ?: continue
            if (node.optBoolean("draft", false)) continue
            val tagName = node.optString("tag_name").trim()
            val htmlUrl = node.optString("html_url").trim().ifBlank { RELEASES_PAGE_URL }
            val version = SemanticVersion.parse(tagName) ?: continue
            val body = node.optString("body").takeIf { it.isNotBlank() }
            val isPrerelease = node.optBoolean("prerelease", false)
            val assets = node.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex) ?: continue
                    val name = asset.optString("name").trim()
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val browserDownloadUrl = asset.optString("browser_download_url").trim()
                    if (browserDownloadUrl.isNotBlank()) {
                        apkUrl = browserDownloadUrl
                        break
                    }
                }
            }
            releases += ParsedRelease(
                tagName = tagName,
                version = version,
                isPrerelease = isPrerelease,
                body = body,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl
            )
        }
        return releases
    }

    private data class ParsedRelease(
        val tagName: String,
        val version: SemanticVersion,
        val isPrerelease: Boolean,
        val body: String?,
        val htmlUrl: String,
        val apkDownloadUrl: String?
    ) {
        fun toRelease(): AppUpdateRelease {
            return AppUpdateRelease(
                tagName = tagName,
                versionName = version.raw,
                htmlUrl = htmlUrl,
                body = body,
                apkDownloadUrl = apkDownloadUrl
            )
        }
    }
}
