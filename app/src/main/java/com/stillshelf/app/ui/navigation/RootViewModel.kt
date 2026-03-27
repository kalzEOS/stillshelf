package com.stillshelf.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.navidrome.NavidromePlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val secureTokenStorage: SecureTokenStorage,
    private val playbackController: Lazy<PlaybackController>,
    private val navidromePlayerController: Lazy<NavidromePlayerController>
) : ViewModel() {

    private val selectedBackendFlow = sessionPreferences.state
        .map { preferences -> preferences.selectedBackend }
        .distinctUntilChanged()

    private val navidromeSessionIdentityFlow = sessionPreferences.state
        .map { preferences ->
            preferences.navidromeBaseUrl.orEmpty() to preferences.navidromeUsername.orEmpty()
        }
        .distinctUntilChanged()

    private val hasNavidromeAuthFlow = navidromeSessionIdentityFlow
        .mapLatest { preferences ->
            val hasSessionIdentity = preferences.first.isNotBlank() &&
                preferences.second.isNotBlank()
            if (!hasSessionIdentity) {
                false
            } else {
                secureTokenStorage.getNamedSecret(NAVIDROME_PASSWORD_KEY)
                    ?.isNotBlank() == true
            }
        }

    val uiState: StateFlow<RootUiState> = combine(
        sessionRepository.observeSessionState(),
        sessionRepository.observeServers(),
        sessionRepository.observeLibrariesForActiveServer(),
        selectedBackendFlow,
        hasNavidromeAuthFlow
    ) { session, servers, libraries, selectedBackend, hasNavidromeAuth ->
        val activeServerId = session.activeServerId
        val activeLibraryId = session.activeLibraryId
        val requiresLibrarySelection = session.requiresLibrarySelection
        val hasAnyServer = servers.isNotEmpty()
        val hasActiveServer = !activeServerId.isNullOrBlank() &&
            servers.any { it.id == activeServerId }
        val hasSelectedLibrary = hasResolvedActiveLibrarySelection(
            hasActiveServer = hasActiveServer,
            activeLibraryId = activeLibraryId,
            availableLibraryIds = libraries.map { it.id }.toSet()
        )
        val hasActiveLibrary = hasSelectedLibrary && !requiresLibrarySelection
        val hasPendingActiveLibrary = hasSelectedLibrary && requiresLibrarySelection

        RootUiState(
            isLoading = false,
            selectedBackend = selectedBackend,
            hasNavidromeSession = hasNavidromeAuth,
            serverCount = servers.size,
            hasAnyServer = hasAnyServer,
            hasActiveServer = hasActiveServer,
            hasActiveLibrary = hasActiveLibrary,
            hasPendingActiveLibrary = hasPendingActiveLibrary
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RootUiState()
        )

    fun selectBackend(provider: BackendProvider) {
        viewModelScope.launch {
            if (provider != BackendProvider.NAVIDROME) {
                navidromePlayerController.get().stop()
            }
            if (provider != BackendProvider.AUDIOBOOKSHELF) {
                playbackController.get().stop()
            }
            sessionPreferences.setSelectedBackend(provider)
        }
    }

    fun clearSelectedBackend() {
        viewModelScope.launch {
            playbackController.get().stop()
            navidromePlayerController.get().stop()
            sessionPreferences.setSelectedBackend(null)
        }
    }

    fun stopPlaybackForBackendSelection() {
        viewModelScope.launch {
            playbackController.get().stop()
            navidromePlayerController.get().stop()
        }
    }

    private companion object {
        const val NAVIDROME_PASSWORD_KEY = "navidrome_password"
    }
}

data class RootUiState(
    val isLoading: Boolean = true,
    val selectedBackend: BackendProvider? = null,
    val hasNavidromeSession: Boolean = false,
    val serverCount: Int = 0,
    val hasAnyServer: Boolean = false,
    val hasActiveServer: Boolean = false,
    val hasActiveLibrary: Boolean = false,
    val hasPendingActiveLibrary: Boolean = false
)

internal fun hasResolvedActiveLibrarySelection(
    hasActiveServer: Boolean,
    activeLibraryId: String?,
    availableLibraryIds: Set<String>
): Boolean {
    if (!hasActiveServer || activeLibraryId.isNullOrBlank()) return false
    return availableLibraryIds.contains(activeLibraryId)
}
