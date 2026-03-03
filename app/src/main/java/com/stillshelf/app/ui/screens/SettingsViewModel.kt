package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverDisplayName: String = "Account",
    val serverHost: String = "-",
    val immersivePlayerEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    val materialDesignEnabled: Boolean = false,
    val skipForwardSeconds: Int = 15,
    val skipBackwardSeconds: Int = 15,
    val lockScreenControlMode: String = "skip",
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionRepository.observeSessionState(),
                sessionRepository.observeServers(),
                sessionPreferences.state
            ) { session, servers, pref ->
                val server = servers.firstOrNull { it.id == session.activeServerId }
                if (server == null) {
                    SettingsUiState(
                        immersivePlayerEnabled = pref.immersivePlayerEnabled,
                        themeMode = parseThemeMode(pref.appThemeMode),
                        materialDesignEnabled = pref.materialDesignEnabled,
                        skipForwardSeconds = pref.skipForwardSeconds,
                        skipBackwardSeconds = pref.skipBackwardSeconds,
                        lockScreenControlMode = pref.lockScreenControlMode
                    )
                } else {
                    SettingsUiState(
                        serverDisplayName = server.name,
                        serverHost = parseHost(server.baseUrl),
                        immersivePlayerEnabled = pref.immersivePlayerEnabled,
                        themeMode = parseThemeMode(pref.appThemeMode),
                        materialDesignEnabled = pref.materialDesignEnabled,
                        skipForwardSeconds = pref.skipForwardSeconds,
                        skipBackwardSeconds = pref.skipBackwardSeconds,
                        lockScreenControlMode = pref.lockScreenControlMode
                    )
                }
            }.collect { state ->
                mutableUiState.update { previous ->
                    state.copy(errorMessage = previous.errorMessage)
                }
            }
        }
    }

    fun onSignOutClick() {
        viewModelScope.launch {
            when (val result = sessionRepository.signOutActiveSession()) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(errorMessage = null) }
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun clearError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    fun setImmersivePlayerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setImmersivePlayerEnabled(enabled)
        }
    }

    fun setMaterialDesignEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setMaterialDesignEnabled(enabled)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            sessionPreferences.setAppThemeMode(
                when (mode) {
                    AppThemeMode.FollowSystem -> "follow_system"
                    AppThemeMode.Light -> "light"
                    AppThemeMode.Dark -> "dark"
                }
            )
        }
    }

    fun setSkipForwardSeconds(seconds: Int) {
        viewModelScope.launch {
            sessionPreferences.setSkipForwardSeconds(seconds)
        }
    }

    fun setSkipBackwardSeconds(seconds: Int) {
        viewModelScope.launch {
            sessionPreferences.setSkipBackwardSeconds(seconds)
        }
    }

    fun setLockScreenControlMode(mode: String) {
        viewModelScope.launch {
            sessionPreferences.setLockScreenControlMode(mode)
        }
    }

    private fun parseHost(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        return if (host.isBlank()) url else host
    }

    private fun parseThemeMode(raw: String?): AppThemeMode {
        return when (raw?.lowercase()) {
            "light" -> AppThemeMode.Light
            "dark" -> AppThemeMode.Dark
            else -> AppThemeMode.FollowSystem
        }
    }
}
