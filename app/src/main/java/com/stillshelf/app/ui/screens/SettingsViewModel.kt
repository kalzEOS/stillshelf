package com.stillshelf.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.theme.AppThemeMode
import com.stillshelf.app.update.AppUpdateManager
import com.stillshelf.app.update.AppUpdateRelease
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
    val isSyncingLibraries: Boolean = false,
    val lastLibrarySyncAtMs: Long? = null,
    val syncToastMessage: String? = null,
    val updateCheckOnStartupEnabled: Boolean = true,
    val includePrereleaseUpdates: Boolean = false,
    val isCheckingForUpdates: Boolean = false,
    val availableUpdate: AppUpdateRelease? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val appUpdateManager: AppUpdateManager,
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
                        lockScreenControlMode = pref.lockScreenControlMode,
                        lastLibrarySyncAtMs = pref.lastLibrarySyncAtMs,
                        updateCheckOnStartupEnabled = pref.updateCheckOnStartup,
                        includePrereleaseUpdates = pref.updateIncludePrereleases
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
                        lockScreenControlMode = pref.lockScreenControlMode,
                        lastLibrarySyncAtMs = pref.lastLibrarySyncAtMs,
                        updateCheckOnStartupEnabled = pref.updateCheckOnStartup,
                        includePrereleaseUpdates = pref.updateIncludePrereleases
                    )
                }
            }.collect { state ->
                mutableUiState.update { previous ->
                    state.copy(
                        isSyncingLibraries = previous.isSyncingLibraries,
                        syncToastMessage = previous.syncToastMessage,
                        isCheckingForUpdates = previous.isCheckingForUpdates,
                        availableUpdate = previous.availableUpdate,
                        errorMessage = previous.errorMessage
                    )
                }
            }
        }
    }

    fun onCheckForUpdatesClick() {
        if (uiState.value.isCheckingForUpdates) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    availableUpdate = null,
                    syncToastMessage = null,
                    errorMessage = null
                )
            }
            when (
                val result = appUpdateManager.checkForUpdate(
                    includePrereleases = uiState.value.includePrereleaseUpdates
                )
            ) {
                is AppResult.Success -> {
                    val update = result.value
                    mutableUiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            availableUpdate = update,
                            syncToastMessage = if (update == null) "No updates found." else null
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            syncToastMessage = "Update check failed"
                        )
                    }
                }
            }
        }
    }

    fun dismissAvailableUpdateDialog() {
        mutableUiState.update { it.copy(availableUpdate = null) }
    }

    fun installAvailableUpdate() {
        val release = uiState.value.availableUpdate ?: return
        mutableUiState.update { it.copy(availableUpdate = null) }
        viewModelScope.launch {
            when (val result = appUpdateManager.downloadAndInstallUpdate(release)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(syncToastMessage = "Update started")
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun onSyncLibrariesClick() {
        if (uiState.value.isSyncingLibraries) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isSyncingLibraries = true,
                    syncToastMessage = null,
                    errorMessage = null
                )
            }
            when (val result = sessionRepository.refreshLibrariesForActiveServer()) {
                is AppResult.Success -> {
                    val syncedAtMs = System.currentTimeMillis()
                    sessionPreferences.setLastLibrarySyncAtMs(syncedAtMs)
                    mutableUiState.update {
                        it.copy(
                            isSyncingLibraries = false,
                            lastLibrarySyncAtMs = syncedAtMs,
                            syncToastMessage = "Library synced",
                            errorMessage = null
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isSyncingLibraries = false,
                            syncToastMessage = "Sync failed"
                        )
                    }
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

    fun consumeSyncToastMessage() {
        mutableUiState.update { it.copy(syncToastMessage = null) }
    }

    fun setUpdateCheckOnStartupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setUpdateCheckOnStartup(enabled)
        }
    }

    fun setIncludePrereleaseUpdates(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setUpdateIncludePrereleases(enabled)
        }
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
            if (!avatarDir.exists() && !avatarDir.mkdirs()) {
                throw IllegalStateException("Unable to create server avatar directory.")
            }
            val extension = resolveAvatarExtension(source)
            val avatarBaseName = "server_avatar_${safeServerId}_${System.currentTimeMillis()}"
            val finalTarget = File(avatarDir, "$avatarBaseName.$extension")
            copyAvatarRaw(source, finalTarget)
            avatarDir.listFiles()?.forEach { existing ->
                if (existing == finalTarget) return@forEach
                if (existing.name.startsWith("server_avatar_${safeServerId}_")) {
                    runCatching { existing.delete() }
                }
            }
            if (finalTarget.length() <= 0L) {
                throw IllegalStateException("Selected image is empty.")
            }
            finalTarget.absolutePath
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

    private fun resolveAvatarExtension(source: Uri): String {
        val contentResolver = appContext.contentResolver
        val mimeType = contentResolver.getType(source).orEmpty()
        val extensionFromMime = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            .orEmpty()
        val extensionFromDisplayName = runCatching {
            contentResolver.query(
                source,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(displayNameIndex).orEmpty()
                        .substringAfterLast('.', missingDelimiterValue = "")
                } else {
                    ""
                }
            }.orEmpty()
        }.getOrDefault("")
        val extensionFromPath = source.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            .orEmpty()
        val candidate = listOf(
            extensionFromMime,
            extensionFromDisplayName,
            extensionFromPath
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        val normalized = candidate.lowercase().replace(Regex("[^a-z0-9]"), "")
        return normalized.ifBlank { "img" }
    }

    private fun copyAvatarRaw(source: Uri, target: File) {
        appContext.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open selected image.")
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
