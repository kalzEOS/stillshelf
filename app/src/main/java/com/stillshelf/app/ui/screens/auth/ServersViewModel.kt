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
    private val busyState = MutableStateFlow(false)
    private val mutableEvents = MutableSharedFlow<ServersEvent>()

    val events = mutableEvents.asSharedFlow()

    val uiState: StateFlow<ServersUiState> = combine(
        sessionRepository.observeServers(),
        sessionRepository.observeSessionState(),
        busyState,
        errorState
    ) { servers, session, isBusy, errorMessage ->
        ServersUiState(
            servers = servers,
            activeServerId = session.activeServerId,
            isBusy = isBusy,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServersUiState()
    )

    fun onServerSelected(serverId: String) {
        viewModelScope.launch {
            val selectedServer = uiState.value.servers.firstOrNull { it.id == serverId }
            busyState.value = true
            when (val result = sessionRepository.setActiveServer(serverId)) {
                is AppResult.Success -> {
                    busyState.value = false
                    errorState.value = null
                    mutableEvents.emit(ServersEvent.NavigateToLibraryPicker)
                }

                is AppResult.Error -> {
                    busyState.value = false
                    val requiresReauth = result.message.contains("no saved session", ignoreCase = true)
                    if (requiresReauth && selectedServer != null) {
                        errorState.value = null
                        mutableEvents.emit(
                            ServersEvent.NavigateToLogin(
                                serverName = selectedServer.name,
                                baseUrl = selectedServer.baseUrl
                            )
                        )
                    } else {
                        errorState.value = result.message
                    }
                }
            }
        }
    }

    fun clearError() {
        errorState.value = null
    }

    fun updateServer(serverId: String, name: String, baseUrl: String) {
        viewModelScope.launch {
            busyState.value = true
            when (val result = sessionRepository.updateServer(serverId, name, baseUrl)) {
                is AppResult.Success -> {
                    busyState.value = false
                    errorState.value = null
                }

                is AppResult.Error -> {
                    busyState.value = false
                    errorState.value = result.message
                }
            }
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            busyState.value = true
            when (val result = sessionRepository.deleteServer(serverId)) {
                is AppResult.Success -> {
                    busyState.value = false
                    errorState.value = null
                }

                is AppResult.Error -> {
                    busyState.value = false
                    errorState.value = result.message
                }
            }
        }
    }
}

data class ServersUiState(
    val servers: List<Server> = emptyList(),
    val activeServerId: String? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

sealed interface ServersEvent {
    data object NavigateToLibraryPicker : ServersEvent
    data class NavigateToLogin(
        val serverName: String,
        val baseUrl: String
    ) : ServersEvent
}
