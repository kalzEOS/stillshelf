package com.stillshelf.app.ui.screens

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class AboutReleaseEntry(
    val tagName: String,
    val title: String,
    val notes: String?,
    val publishedDate: String?,
    val prerelease: Boolean
)

data class AboutUiState(
    val appName: String = "StillShelf",
    val tagline: String = "A focused Audiobookshelf client for listening anywhere.",
    val originStory: String = "StillShelf started as a personal need, then was shared so others could benefit too.",
    val acknowledgements: String = """
StillShelf is open source under GPL-3.0-only.

Core technologies:
- Kotlin + Jetpack Compose
- Hilt, Room, DataStore
- OkHttp, Coil, AndroidX Media
- Material 3
""".trimIndent(),
    val versionName: String = "",
    val versionCode: Int = 0,
    val websiteUrl: String = "https://github.com/kalzEOS/stillshelf",
    val supportUrl: String = "https://github.com/kalzEOS/stillshelf/issues",
    val privacyUrl: String = "https://github.com/kalzEOS/stillshelf/blob/main/docs/PRIVACY_POLICY.md",
    val sourceUrl: String = "https://github.com/kalzEOS/stillshelf",
    val licenseUrl: String = "https://github.com/kalzEOS/stillshelf/blob/main/LICENSE",
    val releasePageUrl: String = "https://github.com/kalzEOS/stillshelf/releases",
    val currentRelease: AboutReleaseEntry? = null,
    val releaseHistory: List<AboutReleaseEntry> = emptyList(),
    val isLoadingReleaseNotes: Boolean = false,
    val releaseNotesError: String? = null
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {
    companion object {
        private const val RELEASES_API_URL = "https://api.github.com/repos/kalzEOS/stillshelf/releases?per_page=12"
    }

    private val installedVersionName: String
    private val installedVersionCode: Int
    private val mutableUiState: MutableStateFlow<AboutUiState>

    init {
        val packageInfo = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull()
        installedVersionName = packageInfo?.versionName.orEmpty()
        installedVersionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it).toInt() } ?: 0
        mutableUiState = MutableStateFlow(
            AboutUiState(
                versionName = installedVersionName,
                versionCode = installedVersionCode
            )
        )
        refreshReleaseNotes()
    }
    val uiState: StateFlow<AboutUiState> = mutableUiState.asStateFlow()

    fun refreshReleaseNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            mutableUiState.update {
                it.copy(
                    isLoadingReleaseNotes = true,
                    releaseNotesError = null
                )
            }
            runCatching {
                val request = Request.Builder()
                    .url(RELEASES_API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "StillShelf/${installedVersionName.ifBlank { "unknown" }}")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Unable to load release notes (${response.code}).")
                    }
                    parseReleaseEntries(response.body?.string().orEmpty())
                }
            }.fold(
                onSuccess = { entries ->
                    val currentTag = "v${installedVersionName}"
                    val current = entries.firstOrNull {
                        it.tagName.equals(currentTag, ignoreCase = true) ||
                            it.tagName.equals(installedVersionName, ignoreCase = true)
                    } ?: AboutReleaseEntry(
                        tagName = currentTag,
                        title = "Current build",
                        notes = "Release notes are not published yet for this build.",
                        publishedDate = null,
                        prerelease = installedVersionName.contains("rc", ignoreCase = true)
                    )
                    val history = entries.filterNot { it.tagName.equals(current.tagName, ignoreCase = true) }
                    mutableUiState.update {
                        it.copy(
                            currentRelease = current,
                            releaseHistory = history,
                            isLoadingReleaseNotes = false,
                            releaseNotesError = null
                        )
                    }
                },
                onFailure = { error ->
                    mutableUiState.update {
                        it.copy(
                            currentRelease = AboutReleaseEntry(
                                tagName = "v${installedVersionName}",
                                title = "Current build",
                                notes = "Release notes unavailable right now.",
                                publishedDate = null,
                                prerelease = installedVersionName.contains("rc", ignoreCase = true)
                            ),
                            releaseHistory = emptyList(),
                            isLoadingReleaseNotes = false,
                            releaseNotesError = error.message ?: "Unable to load release notes."
                        )
                    }
                }
            )
        }
    }

    private fun parseReleaseEntries(raw: String): List<AboutReleaseEntry> {
        if (raw.isBlank()) return emptyList()
        val source = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val releases = mutableListOf<AboutReleaseEntry>()
        for (index in 0 until source.length()) {
            val node = source.optJSONObject(index) ?: continue
            if (node.optBoolean("draft", false)) continue
            val tag = node.optString("tag_name").trim()
            if (tag.isBlank()) continue
            val title = node.optString("name").trim().ifBlank { tag }
            val notes = node.optString("body").trim().takeIf { it.isNotBlank() }
            val publishedDate = node.optString("published_at")
                .trim()
                .takeIf { it.isNotBlank() }
                ?.take(10)
            releases += AboutReleaseEntry(
                tagName = tag,
                title = title,
                notes = notes,
                publishedDate = publishedDate,
                prerelease = node.optBoolean("prerelease", false)
            )
        }
        return releases
    }
}
