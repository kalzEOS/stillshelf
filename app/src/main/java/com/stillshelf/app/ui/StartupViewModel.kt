package com.stillshelf.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        viewModelScope.launch {
            val pref = runCatching { sessionPreferences.state.first() }.getOrNull()
            runCatching { appUpdateManager.cleanupInstalledUpdateApkIfNeeded() }
            mutableIsReady.value = true
            if (pref != null && pref.updateCheckOnStartup) {
                when (
                    val updateResult = appUpdateManager.checkForUpdate(
                        includePrereleases = pref.updateIncludePrereleases
                    )
                ) {
                    is AppResult.Success -> {
                        mutableStartupUpdatePrompt.value = updateResult.value
                    }

                    is AppResult.Error -> Unit
                }
            }
        }
    }

    fun dismissStartupUpdatePrompt() {
        mutableStartupUpdatePrompt.value = null
    }

    fun installStartupUpdate() {
        val release = mutableStartupUpdatePrompt.value ?: return
        mutableStartupUpdatePrompt.value = null
        viewModelScope.launch {
            appUpdateManager.downloadAndInstallUpdate(release)
        }
    }
}
