package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeMenuUiState(
    val libraries: List<Library> = emptyList(),
    val servers: List<Server> = emptyList(),
    val activeServerId: String? = null,
    val activeLibraryId: String? = null,
    val isSwitchingServer: Boolean = false,
    val isSwitchingLibrary: Boolean = false,
    val errorMessage: String? = null
)

sealed interface HomeMenuEvent {
    data object NavigateToLibraryPicker : HomeMenuEvent
}

@HiltViewModel
class HomeMenuViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeMenuUiState())
    val uiState: StateFlow<HomeMenuUiState> = mutableUiState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<HomeMenuEvent>()
    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionRepository.observeLibrariesForActiveServer(),
                sessionRepository.observeSessionState(),
                sessionRepository.observeServers()
            ) { libraries, sessionState, servers ->
                Triple(libraries, sessionState, servers)
            }.collect { (libraries, sessionState, servers) ->
                mutableUiState.update {
                    it.copy(
                        libraries = libraries,
                        servers = servers,
                        activeServerId = sessionState.activeServerId,
                        activeLibraryId = sessionState.activeLibraryId
                    )
                }
            }
        }
    }

    fun onServerSelected(serverId: String) {
        if (uiState.value.isSwitchingServer) return
        if (serverId == uiState.value.activeServerId) {
            viewModelScope.launch {
                mutableEvents.emit(HomeMenuEvent.NavigateToLibraryPicker)
            }
            return
        }
        mutableUiState.update { it.copy(isSwitchingServer = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.setActiveServer(serverId)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(isSwitchingServer = false) }
                    mutableEvents.emit(HomeMenuEvent.NavigateToLibraryPicker)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isSwitchingServer = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun switchLibrary(libraryId: String) {
        if (
            libraryId == uiState.value.activeLibraryId ||
            uiState.value.isSwitchingLibrary ||
            uiState.value.isSwitchingServer
        ) {
            return
        }
        mutableUiState.update { it.copy(isSwitchingLibrary = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.setActiveLibrary(libraryId)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(isSwitchingLibrary = false) }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isSwitchingLibrary = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }
}
