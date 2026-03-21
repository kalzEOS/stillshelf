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
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val secureTokenStorage: SecureTokenStorage,
    private val playbackController: PlaybackController,
    private val navidromePlayerController: NavidromePlayerController
) : ViewModel() {

    private val hasNavidromeAuthFlow = sessionPreferences.state
        .mapLatest { preferences ->
            val hasSessionIdentity = !preferences.navidromeBaseUrl.isNullOrBlank() &&
                !preferences.navidromeUsername.isNullOrBlank()
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
        sessionPreferences.state,
        hasNavidromeAuthFlow
    ) { session, servers, libraries, preferences, hasNavidromeAuth ->
        val activeServerId = session.activeServerId
        val activeLibraryId = session.activeLibraryId
        val requiresLibrarySelection = session.requiresLibrarySelection
        val hasAnyServer = servers.isNotEmpty()
        val hasActiveServer = !activeServerId.isNullOrBlank() &&
            servers.any { it.id == activeServerId }
        val hasActiveLibrary = hasActiveServer &&
            !requiresLibrarySelection &&
            !activeLibraryId.isNullOrBlank() &&
            libraries.any { it.id == activeLibraryId }

        RootUiState(
            isLoading = false,
            selectedBackend = preferences.selectedBackend,
            hasNavidromeSession = hasNavidromeAuth,
            serverCount = servers.size,
            hasAnyServer = hasAnyServer,
            hasActiveServer = hasActiveServer,
            hasActiveLibrary = hasActiveLibrary
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
                navidromePlayerController.stop()
            }
            if (provider != BackendProvider.AUDIOBOOKSHELF) {
                playbackController.stop()
            }
            sessionPreferences.setSelectedBackend(provider)
        }
    }

    fun clearSelectedBackend() {
        viewModelScope.launch {
            playbackController.stop()
            navidromePlayerController.stop()
            sessionPreferences.setSelectedBackend(null)
        }
    }

    fun stopPlaybackForBackendSelection() {
        viewModelScope.launch {
            playbackController.stop()
            navidromePlayerController.stop()
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
    val hasActiveLibrary: Boolean = false
)
