package com.stillshelf.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AddServerUiState())
    val uiState: StateFlow<AddServerUiState> = mutableUiState.asStateFlow()

    fun onServerNameChange(value: String) {
        val serverNameError = validateServerName(value)
        mutableUiState.update { state ->
            state.copy(
                serverName = value,
                serverNameError = serverNameError,
                canContinue = canContinue(
                    name = value,
                    baseUrl = state.baseUrl,
                    serverNameError = serverNameError
                ),
                connectionMessage = null,
                connectionSuccess = null
            )
        }
    }

    fun onBaseUrlChange(value: String) {
        val normalizedValue = value.replace(" ", "")
        val currentServerName = uiState.value.serverName
        val serverNameError = validateServerName(currentServerName)
        mutableUiState.update { state ->
            state.copy(
                baseUrl = normalizedValue,
                serverNameError = serverNameError,
                canContinue = canContinue(
                    name = currentServerName,
                    baseUrl = normalizedValue,
                    serverNameError = serverNameError
                ),
                connectionMessage = null,
                connectionSuccess = null
            )
        }
    }

    fun onTestConnectionClick() {
        val baseUrl = uiState.value.baseUrl.trim()
        if (baseUrl.isBlank() || uiState.value.isTestingConnection) return

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isTestingConnection = true,
                    connectionMessage = null,
                    connectionSuccess = null
                )
            }

            when (val result = sessionRepository.testServerConnection(baseUrl)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionMessage = result.value,
                            connectionSuccess = true
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionMessage = result.message,
                            connectionSuccess = false
                        )
                    }
                }
            }
        }
    }

    private fun canContinue(name: String, baseUrl: String, serverNameError: String?): Boolean {
        return name.isNotBlank() && baseUrl.isNotBlank() && serverNameError == null
    }

    private fun validateServerName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Server name is required."
        if (trimmed.length < 2) return "Server name must be at least 2 characters."
        val hasInvalidChar = trimmed.any { char ->
            !char.isLetterOrDigit() && char !in setOf(' ', '.', '-', '_', '\'')
        }
        if (hasInvalidChar) {
            return "Use letters, numbers, spaces, or . - _ ' only."
        }
        return null
    }
}

data class AddServerUiState(
    val serverName: String = "",
    val serverNameError: String? = null,
    val baseUrl: String = "",
    val canContinue: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val connectionSuccess: Boolean? = null
)
