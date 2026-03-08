package com.stillshelf.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.BuildConfig
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.update.AppUpdateManager
import com.stillshelf.app.update.AppUpdateRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {
    private val mutableIsReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = mutableIsReady.asStateFlow()
    private val mutableStartupUpdatePrompt = MutableStateFlow<AppUpdateRelease?>(null)
    val startupUpdatePrompt: StateFlow<AppUpdateRelease?> = mutableStartupUpdatePrompt.asStateFlow()
    private var pendingStartupUpdatePrompt: AppUpdateRelease? = null
    private var hasReachedHomeScreen = false

    init {
        viewModelScope.launch {
            val pref = runCatching { sessionPreferences.state.first() }.getOrNull()
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
}
