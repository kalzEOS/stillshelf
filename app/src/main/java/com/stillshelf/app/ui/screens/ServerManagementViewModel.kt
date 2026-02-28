package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerManagementUiState(
    val servers: List<Server> = emptyList(),
    val activeServerId: String? = null,
    val isSwitching: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ServerManagementUiState())
    val uiState: StateFlow<ServerManagementUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionRepository.observeServers(),
                sessionRepository.observeSessionState()
            ) { servers, session ->
                servers to session.activeServerId
            }.collect { (servers, activeServerId) ->
                mutableUiState.update {
                    it.copy(
                        servers = servers,
                        activeServerId = activeServerId
                    )
                }
            }
        }
    }

    fun setActiveServer(serverId: String) {
        if (serverId == uiState.value.activeServerId || uiState.value.isSwitching) return
        mutableUiState.update { it.copy(isSwitching = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.setActiveServer(serverId)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(isSwitching = false) }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isSwitching = false,
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

