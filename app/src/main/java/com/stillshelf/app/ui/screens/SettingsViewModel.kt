package com.stillshelf.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.theme.AppThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
    val activeServerId: String? = null,
    val serverDisplayName: String = "Account",
    val serverHost: String = "-",
    val serverAvatarUri: String? = null,
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
    private val sessionPreferences: SessionPreferences,
    @param:ApplicationContext private val appContext: Context
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
                        activeServerId = session.activeServerId,
                        immersivePlayerEnabled = pref.immersivePlayerEnabled,
                        serverAvatarUri = pref.serverAvatarUris[session.activeServerId],
                        themeMode = parseThemeMode(pref.appThemeMode),
                        materialDesignEnabled = pref.materialDesignEnabled,
                        skipForwardSeconds = pref.skipForwardSeconds,
                        skipBackwardSeconds = pref.skipBackwardSeconds,
                        lockScreenControlMode = pref.lockScreenControlMode
                    )
                } else {
                    SettingsUiState(
                        activeServerId = server.id,
                        serverDisplayName = server.name,
                        serverHost = parseHost(server.baseUrl),
                        serverAvatarUri = pref.serverAvatarUris[server.id],
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

    fun setServerAvatarFromUri(uri: Uri?) {
        if (uri == null) return
        val serverId = uiState.value.activeServerId
        if (serverId.isNullOrBlank()) return
        viewModelScope.launch {
            when (val result = copyServerAvatarToInternalStorage(serverId, uri)) {
                is AppResult.Success -> {
                    sessionPreferences.setServerAvatarUri(serverId, result.value)
                    mutableUiState.update { it.copy(serverAvatarUri = result.value, errorMessage = null) }
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    private fun copyServerAvatarToInternalStorage(serverId: String, source: Uri): AppResult<String> {
        return runCatching {
            val safeServerId = serverId.trim().ifBlank { "default" }
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val avatarDir = File(appContext.filesDir, "server_avatars")
            if (!avatarDir.exists()) {
                avatarDir.mkdirs()
            }
            val target = File(avatarDir, "server_avatar_$safeServerId.img")
            appContext.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to open selected image.")
            target.absolutePath
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                AppResult.Error(
                    it.message ?: "Unable to set profile photo for this server.",
                    it
                )
            }
        )
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
