package com.stillshelf.app.ui.screens.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.navigation.AuthRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val serverName: String = savedStateHandle[AuthRoute.SERVER_NAME_ARG] ?: ""
    private val baseUrl: String = savedStateHandle[AuthRoute.BASE_URL_ARG] ?: ""

    private val mutableUiState = MutableStateFlow(
        LoginUiState(
            serverName = serverName,
            baseUrl = baseUrl
        )
    )
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<LoginEvent>()
    val events = mutableEvents.asSharedFlow()

    fun onUsernameChange(value: String) {
        mutableUiState.update { state ->
            state.copy(
                username = value,
                canSubmit = canSubmit(value, state.password)
            )
        }
    }

    fun onPasswordChange(value: String) {
        mutableUiState.update { state ->
            state.copy(
                password = value,
                canSubmit = canSubmit(state.username, value)
            )
        }
    }

    fun onLoginClick() {
        val currentState = mutableUiState.value
        if (!currentState.canSubmit || currentState.isLoading) return

        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (
                val result = sessionRepository.addServerAndLogin(
                    serverName = currentState.serverName,
                    baseUrl = currentState.baseUrl,
                    username = currentState.username,
                    password = currentState.password
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(isLoading = false) }
                    mutableEvents.emit(LoginEvent.Success)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
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

    private fun canSubmit(username: String, password: String): Boolean {
        return username.isNotBlank() && password.isNotBlank()
    }
}

data class LoginUiState(
    val serverName: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val canSubmit: Boolean = false,
    val errorMessage: String? = null
)

sealed interface LoginEvent {
    data object Success : LoginEvent
}
