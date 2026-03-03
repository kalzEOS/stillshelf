package com.stillshelf.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RootViewModel @Inject constructor(
    sessionRepository: SessionRepository
) : ViewModel() {

    val uiState: StateFlow<RootUiState> = combine(
        sessionRepository.observeSessionState(),
        sessionRepository.observeServers(),
        sessionRepository.observeLibrariesForActiveServer()
    ) { session, servers, libraries ->
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
}

data class RootUiState(
    val isLoading: Boolean = true,
    val serverCount: Int = 0,
    val hasAnyServer: Boolean = false,
    val hasActiveServer: Boolean = false,
    val hasActiveLibrary: Boolean = false
)
