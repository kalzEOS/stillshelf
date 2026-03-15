package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.BuildConfig
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.EndpointReachabilityStatus
import com.stillshelf.app.core.model.ServerConnectionRoute
import com.stillshelf.app.core.model.ServerEndpointSwitchingConfig
import com.stillshelf.app.core.network.ActiveEndpointHealthMonitor
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.theme.AppThemeMode
import com.stillshelf.app.update.AppUpdateManager
import com.stillshelf.app.update.AppUpdateRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsServerOption(
    val id: String,
    val name: String,
    val baseUrl: String,
    val host: String
)

internal fun resolveCurrentConnectionLabel(
    serverPresent: Boolean,
    switchingIsActive: Boolean,
    currentRoute: ServerConnectionRoute?,
    effectiveBaseUrl: String?,
    localBaseUrl: String?,
    remoteBaseUrl: String?
): String {
    if (!serverPresent) return ""

    val normalizedEffectiveBaseUrl = effectiveBaseUrl?.trim()?.removeSuffix("/").orEmpty()
    val normalizedLocalBaseUrl = localBaseUrl?.trim()?.removeSuffix("/").orEmpty()
    val normalizedRemoteBaseUrl = remoteBaseUrl?.trim()?.removeSuffix("/").orEmpty()

    if (switchingIsActive) {
        return when (currentRoute) {
            ServerConnectionRoute.Local -> "Local"
            ServerConnectionRoute.Remote -> "Remote"
            else -> if (normalizedEffectiveBaseUrl.isNotBlank()) "Current server" else ""
        }
    }

    return when {
        normalizedLocalBaseUrl.isNotBlank() &&
            normalizedEffectiveBaseUrl.equals(normalizedLocalBaseUrl, ignoreCase = true) -> "Local"
        normalizedRemoteBaseUrl.isNotBlank() &&
            normalizedEffectiveBaseUrl.equals(normalizedRemoteBaseUrl, ignoreCase = true) -> "Remote"
        normalizedEffectiveBaseUrl.isNotBlank() -> "Current server"
        else -> ""
    }
}

data class SettingsUiState(
    val activeServerId: String? = null,
    val serverDisplayName: String = "Account",
    val serverHost: String = "-",
    val serverBaseUrl: String? = null,
    val immersivePlayerEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    val materialDesignEnabled: Boolean = false,
    val skipForwardSeconds: Int = 15,
    val skipBackwardSeconds: Int = 15,
    val lockScreenControlMode: String = "skip",
    val isSyncingLibraries: Boolean = false,
    val lastLibrarySyncAtMs: Long? = null,
    val syncToastMessage: String? = null,
    val appUpdatesEnabled: Boolean = BuildConfig.IN_APP_UPDATES_ENABLED,
    val updateCheckOnStartupEnabled: Boolean = true,
    val includePrereleaseUpdates: Boolean = false,
    val automaticServerSwitchingEnabled: Boolean = false,
    val lanServerUrl: String = "",
    val wanServerUrl: String = "",
    val savedServers: List<SettingsServerOption> = emptyList(),
    val currentConnectionLabel: String = "",
    val currentEndpointUrl: String = "",
    val connectionStatusLabel: String = "Checking",
    val connectionLatencyMs: Long? = null,
    val isCheckingForUpdates: Boolean = false,
    val availableUpdate: AppUpdateRelease? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val activeEndpointHealthMonitor: ActiveEndpointHealthMonitor,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionRepository.observeSessionState(),
                sessionRepository.observeServers(),
                sessionPreferences.state,
                sessionRepository.observeActiveServerConnectionStatus(),
                activeEndpointHealthMonitor.observeHealth()
            ) { session, servers, pref, connectionStatus, endpointHealth ->
                val server = servers.firstOrNull { it.id == session.activeServerId }
                val switchingConfig = server?.id?.let(pref.serverEndpointSwitchingConfigs::get)
                    ?: ServerEndpointSwitchingConfig()
                val effectiveBaseUrl = connectionStatus?.effectiveBaseUrl ?: server?.baseUrl
                val currentRoute = connectionStatus?.route
                val switchingIsActive = connectionStatus?.switchingEnabled == true
                val currentConnectionLabel = resolveCurrentConnectionLabel(
                    serverPresent = server != null,
                    switchingIsActive = switchingIsActive,
                    currentRoute = currentRoute,
                    effectiveBaseUrl = effectiveBaseUrl,
                    localBaseUrl = switchingConfig.lanBaseUrl,
                    remoteBaseUrl = switchingConfig.wanBaseUrl
                )
                val connectionStatusLabel = when (endpointHealth?.reachabilityStatus) {
                    EndpointReachabilityStatus.Reachable -> "Reachable"
                    EndpointReachabilityStatus.Unavailable -> "Unavailable"
                    EndpointReachabilityStatus.Checking, null -> "Checking"
                }
                val currentEndpointUrl = endpointHealth?.endpointUrl ?: effectiveBaseUrl.orEmpty()
                if (server == null) {
                    SettingsUiState(
                        activeServerId = session.activeServerId,
                        appUpdatesEnabled = BuildConfig.IN_APP_UPDATES_ENABLED,
                        immersivePlayerEnabled = pref.immersivePlayerEnabled,
                        themeMode = parseThemeMode(pref.appThemeMode),
                        materialDesignEnabled = pref.materialDesignEnabled,
                        skipForwardSeconds = pref.skipForwardSeconds,
                        skipBackwardSeconds = pref.skipBackwardSeconds,
                        lockScreenControlMode = pref.lockScreenControlMode,
                        lastLibrarySyncAtMs = pref.lastLibrarySyncAtMs,
                        updateCheckOnStartupEnabled = pref.updateCheckOnStartup,
                        includePrereleaseUpdates = pref.updateIncludePrereleases,
                        automaticServerSwitchingEnabled = switchingConfig.enabled,
                        lanServerUrl = switchingConfig.lanBaseUrl.orEmpty(),
                        wanServerUrl = switchingConfig.wanBaseUrl.orEmpty(),
                        savedServers = servers.map { candidate ->
                            SettingsServerOption(
                                id = candidate.id,
                                name = candidate.name,
                                baseUrl = candidate.baseUrl,
                                host = parseHost(candidate.baseUrl)
                            )
                        },
                        currentConnectionLabel = currentConnectionLabel,
                        currentEndpointUrl = currentEndpointUrl,
                        connectionStatusLabel = connectionStatusLabel,
                        connectionLatencyMs = endpointHealth?.latencyMs
                    )
                } else {
                    SettingsUiState(
                        activeServerId = server.id,
                        serverDisplayName = server.name,
                        serverHost = parseHost(server.baseUrl),
                        serverBaseUrl = server.baseUrl,
                        appUpdatesEnabled = BuildConfig.IN_APP_UPDATES_ENABLED,
                        immersivePlayerEnabled = pref.immersivePlayerEnabled,
                        themeMode = parseThemeMode(pref.appThemeMode),
                        materialDesignEnabled = pref.materialDesignEnabled,
                        skipForwardSeconds = pref.skipForwardSeconds,
                        skipBackwardSeconds = pref.skipBackwardSeconds,
                        lockScreenControlMode = pref.lockScreenControlMode,
                        lastLibrarySyncAtMs = pref.lastLibrarySyncAtMs,
                        updateCheckOnStartupEnabled = pref.updateCheckOnStartup,
                        includePrereleaseUpdates = pref.updateIncludePrereleases,
                        automaticServerSwitchingEnabled = switchingConfig.enabled,
                        lanServerUrl = switchingConfig.lanBaseUrl.orEmpty(),
                        wanServerUrl = switchingConfig.wanBaseUrl.orEmpty(),
                        savedServers = servers.map { candidate ->
                            SettingsServerOption(
                                id = candidate.id,
                                name = candidate.name,
                                baseUrl = candidate.baseUrl,
                                host = parseHost(candidate.baseUrl)
                            )
                        },
                        currentConnectionLabel = currentConnectionLabel,
                        currentEndpointUrl = currentEndpointUrl,
                        connectionStatusLabel = connectionStatusLabel,
                        connectionLatencyMs = endpointHealth?.latencyMs
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
        if (!BuildConfig.IN_APP_UPDATES_ENABLED || uiState.value.isCheckingForUpdates) return
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
        if (!BuildConfig.IN_APP_UPDATES_ENABLED) {
            dismissAvailableUpdateDialog()
            return
        }
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
        if (!BuildConfig.IN_APP_UPDATES_ENABLED) return
        viewModelScope.launch {
            sessionPreferences.setUpdateCheckOnStartup(enabled)
        }
    }

    fun setIncludePrereleaseUpdates(enabled: Boolean) {
        if (!BuildConfig.IN_APP_UPDATES_ENABLED) return
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

    fun setAutomaticServerSwitchingEnabled(enabled: Boolean) {
        val serverId = uiState.value.activeServerId ?: return
        saveAdvancedServerConfig(
            serverId = serverId,
            config = ServerEndpointSwitchingConfig(
                enabled = enabled,
                lanBaseUrl = uiState.value.lanServerUrl,
                wanBaseUrl = uiState.value.wanServerUrl,
                connectionMode = ServerEndpointSwitchingConfig().connectionMode
            )
        )
    }

    fun updateLanServerUrl(url: String) {
        val serverId = uiState.value.activeServerId ?: return
        saveAdvancedServerConfig(
            serverId = serverId,
            config = ServerEndpointSwitchingConfig(
                enabled = uiState.value.automaticServerSwitchingEnabled,
                lanBaseUrl = url,
                wanBaseUrl = uiState.value.wanServerUrl,
                connectionMode = ServerEndpointSwitchingConfig().connectionMode
            )
        )
    }

    fun updateWanServerUrl(url: String) {
        val serverId = uiState.value.activeServerId ?: return
        saveAdvancedServerConfig(
            serverId = serverId,
            config = ServerEndpointSwitchingConfig(
                enabled = uiState.value.automaticServerSwitchingEnabled,
                lanBaseUrl = uiState.value.lanServerUrl,
                wanBaseUrl = url,
                connectionMode = ServerEndpointSwitchingConfig().connectionMode
            )
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

    private fun saveAdvancedServerConfig(
        serverId: String,
        config: ServerEndpointSwitchingConfig
    ) {
        viewModelScope.launch {
            when (val result = sessionRepository.updateServerEndpointSwitchingConfig(serverId, config)) {
                is AppResult.Success -> mutableUiState.update { it.copy(errorMessage = null) }
                is AppResult.Error -> mutableUiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
