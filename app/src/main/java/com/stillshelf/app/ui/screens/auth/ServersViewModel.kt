package com.stillshelf.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val errorState = MutableStateFlow<String?>(null)
    private val mutableEvents = MutableSharedFlow<ServersEvent>()

    val events = mutableEvents.asSharedFlow()

    val uiState: StateFlow<ServersUiState> = combine(
        sessionRepository.observeServers(),
        sessionRepository.observeSessionState(),
        errorState
    ) { servers, session, errorMessage ->
        ServersUiState(
            servers = servers,
            activeServerId = session.activeServerId,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServersUiState()
    )

    fun onServerSelected(serverId: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.setActiveServer(serverId)) {
                is AppResult.Success -> {
                    errorState.value = null
                    mutableEvents.emit(ServersEvent.NavigateToLibraryPicker)
                }

                is AppResult.Error -> {
                    errorState.value = result.message
                }
            }
        }
    }

    fun clearError() {
        errorState.value = null
    }
}

data class ServersUiState(
    val servers: List<Server> = emptyList(),
    val activeServerId: String? = null,
    val errorMessage: String? = null
)

sealed interface ServersEvent {
    data object NavigateToLibraryPicker : ServersEvent
}
