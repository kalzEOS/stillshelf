package com.stillshelf.app.ui

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.BuildConfig
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.update.AppUpdateManager
import com.stillshelf.app.update.AppUpdateRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionPreferences: SessionPreferences,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {
    private val mutableIsReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = mutableIsReady.asStateFlow()
    private val mutableStartupUpdatePrompt = MutableStateFlow<AppUpdateRelease?>(null)
    val startupUpdatePrompt: StateFlow<AppUpdateRelease?> = mutableStartupUpdatePrompt.asStateFlow()
    private val mutableUpgradeMessagePrompt = MutableStateFlow<UpgradeMessagePrompt?>(null)
    val upgradeMessagePrompt: StateFlow<UpgradeMessagePrompt?> = mutableUpgradeMessagePrompt.asStateFlow()
    private var pendingStartupUpdatePrompt: AppUpdateRelease? = null
    private var hasReachedHomeScreen = false

    init {
        viewModelScope.launch {
            val pref = runCatching { sessionPreferences.state.first() }.getOrNull()
            val acknowledgedUpgradeVersion = runCatching {
                sessionPreferences.getAcknowledgedUpgradeNoticeVersion()
            }.getOrNull()
            maybePublishUpgradePrompt(
                preferences = pref,
                acknowledgedVersion = acknowledgedUpgradeVersion
            )
            runCatching { appUpdateManager.cleanupInstalledUpdateApkIfNeeded() }
            mutableIsReady.value = true
            if (BuildConfig.IN_APP_UPDATES_ENABLED && pref != null && pref.updateCheckOnStartup) {
                when (
                    val updateResult = appUpdateManager.checkForUpdate(
                        includePrereleases = pref.updateIncludePrereleases
                    )
                ) {
                    is AppResult.Success -> {
                        pendingStartupUpdatePrompt = updateResult.value
                        publishStartupUpdatePromptIfEligible()
                    }

                    is AppResult.Error -> Unit
                }
            }
        }
    }

    fun onHomeScreenReached() {
        if (hasReachedHomeScreen) return
        hasReachedHomeScreen = true
        publishStartupUpdatePromptIfEligible()
    }

    fun dismissStartupUpdatePrompt() {
        pendingStartupUpdatePrompt = null
        mutableStartupUpdatePrompt.value = null
    }

    fun dismissUpgradeMessagePrompt() {
        val versionName = mutableUpgradeMessagePrompt.value?.versionName ?: BuildConfig.VERSION_NAME
        mutableUpgradeMessagePrompt.value = null
        viewModelScope.launch {
            sessionPreferences.setAcknowledgedUpgradeNoticeVersion(versionName)
        }
    }

    fun installStartupUpdate() {
        if (!BuildConfig.IN_APP_UPDATES_ENABLED) {
            dismissStartupUpdatePrompt()
            return
        }
        val release = mutableStartupUpdatePrompt.value ?: return
        mutableStartupUpdatePrompt.value = null
        viewModelScope.launch {
            appUpdateManager.downloadAndInstallUpdate(release)
        }
    }

    private fun publishStartupUpdatePromptIfEligible() {
        if (!hasReachedHomeScreen) return
        mutableStartupUpdatePrompt.value = pendingStartupUpdatePrompt
    }

    private fun maybePublishUpgradePrompt(
        preferences: SessionPreferenceState?,
        acknowledgedVersion: String?
    ) {
        val pref = preferences ?: return
        val packageInfo = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull() ?: return
        val currentVersionName = packageInfo.versionName.orEmpty().ifBlank { BuildConfig.VERSION_NAME }
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
        if (
            shouldShowUpgradeMessagePrompt(
                currentVersionName = currentVersionName,
                currentVersionCode = versionCode,
                firstInstallTimeMs = packageInfo.firstInstallTime,
                lastUpdateTimeMs = packageInfo.lastUpdateTime,
                acknowledgedVersion = acknowledgedVersion,
                preferences = pref
            )
        ) {
            mutableUpgradeMessagePrompt.value = UpgradeMessagePrompt(versionName = currentVersionName)
        }
    }
}

data class UpgradeMessagePrompt(
    val versionName: String
)

internal fun shouldShowUpgradeMessagePrompt(
    currentVersionName: String,
    currentVersionCode: Int,
    firstInstallTimeMs: Long,
    lastUpdateTimeMs: Long,
    acknowledgedVersion: String?,
    preferences: SessionPreferenceState
): Boolean {
    if (!currentVersionName.startsWith("0.2.6")) return false
    if (currentVersionCode <= 0) return false
    if (acknowledgedVersion == currentVersionName) return false
    if (lastUpdateTimeMs <= firstInstallTimeMs) return false

    val hasExistingAudiobookshelfState = !preferences.activeServerId.isNullOrBlank() ||
        !preferences.activeLibraryId.isNullOrBlank() ||
        !preferences.lastPlayedBookId.isNullOrBlank() ||
        preferences.selectedBackend == BackendProvider.AUDIOBOOKSHELF
    val hasNoSavedNavidromeServers = preferences.navidromeServers.isEmpty()

    return hasExistingAudiobookshelfState && hasNoSavedNavidromeServers
}
