package com.stillshelf.app.ui.screens.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SecureStorageUnavailableException
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.LoginPersistenceMode
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.navigation.AuthRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.net.ssl.SSLException

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
        submitLogin(LoginPersistenceMode.PersistentSecureOnly)
    }

    fun onContinueSessionOnlyClick() {
        submitLogin(LoginPersistenceMode.SessionOnly)
    }

    fun onSaveLessSecurelyClick() {
        submitLogin(LoginPersistenceMode.PersistentAllowInsecureFallback)
    }

    fun dismissTokenStoragePrompt() {
        mutableUiState.update { it.copy(showTokenStorageFallbackPrompt = false) }
    }

    private fun submitLogin(persistenceMode: LoginPersistenceMode) {
        val currentState = mutableUiState.value
        if (!currentState.canSubmit || currentState.isLoading) return
        viewModelScope.launch {
            try {
                mutableUiState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        showTokenStorageFallbackPrompt = false
                    )
                }

                when (
                    val result = runCatching {
                        sessionRepository.addServerAndLogin(
                            serverName = currentState.serverName,
                            baseUrl = currentState.baseUrl,
                            username = currentState.username,
                            password = currentState.password,
                            persistenceMode = persistenceMode
                        )
                    }.getOrElse {
                        AppResult.Error("Unable to complete login.", it)
                    }
                ) {
                    is AppResult.Success -> {
                        mutableUiState.update { it.copy(isLoading = false) }
                        mutableEvents.emit(LoginEvent.Success)
                    }

                    is AppResult.Error -> {
                        val requiresStorageChoice =
                            result.cause is SecureStorageUnavailableException &&
                                persistenceMode == LoginPersistenceMode.PersistentSecureOnly
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = if (requiresStorageChoice) null else mapLoginErrorMessage(result),
                                showTokenStorageFallbackPrompt = requiresStorageChoice
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapLoginErrorMessage(AppResult.Error("Unable to complete login.", t))
                    )
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

    private fun mapLoginErrorMessage(error: AppResult.Error): String {
        val message = error.message.trim()
        val normalized = message.lowercase()
        val cause = error.cause

        return when {
            normalized.contains("http 401") ||
                normalized.contains("http 403") ||
                normalized.contains("invalid username") ||
                normalized.contains("invalid password") -> {
                "Login failed. Check your username and password."
            }

            normalized.contains("http 429") -> {
                "Too many requests right now. Wait a moment and try again."
            }

            cause is UnknownHostException ||
                cause is ConnectException ||
                normalized.contains("unable to resolve host") ||
                normalized.contains("failed to connect") ||
                normalized.contains("connection refused") -> {
                "Could not reach the server. Check the URL and make sure the server is online."
            }

            cause is SocketTimeoutException || normalized.contains("timed out") -> {
                "Connection timed out. Check your network and try again."
            }

            cause is SSLException ||
                normalized.contains("ssl") ||
                normalized.contains("certificate") ||
                normalized.contains("cleartext") -> {
                "Secure connection failed. Check your HTTPS/certificate settings and try again."
            }

            normalized.contains("http 5") -> {
                "Server error. Please try again in a moment."
            }

            normalized.contains("http 4") -> {
                "Login request was rejected. Check your credentials and server settings."
            }

            message.isBlank() -> "Login failed. Please try again."
            else -> message
        }
    }
}

data class LoginUiState(
    val serverName: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val canSubmit: Boolean = false,
    val errorMessage: String? = null,
    val showTokenStorageFallbackPrompt: Boolean = false
)

sealed interface LoginEvent {
    data object Success : LoginEvent
}
