package com.stillshelf.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryPickerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val errorState = MutableStateFlow<String?>(null)
    private val loadingState = MutableStateFlow(true)
    private val mutableEvents = MutableSharedFlow<LibraryPickerEvent>()

    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                loadingState.value = true
                when (val result = refreshLibrariesForActiveServerWithRetry()) {
                    is AppResult.Success -> errorState.value = null
                    is AppResult.Error -> {
                        val message = result.message
                        errorState.value = message
                        if (isUnrecoverableLibraryPickerState(message)) {
                            mutableEvents.emit(LibraryPickerEvent.NavigateToManageServers)
                        }
                    }
                }
            } catch (t: Throwable) {
                val message = t.message ?: "Unable to load libraries for this server."
                errorState.value = message
                if (isUnrecoverableLibraryPickerState(message)) {
                    mutableEvents.emit(LibraryPickerEvent.NavigateToManageServers)
                }
            } finally {
                loadingState.value = false
            }
        }
    }

    private suspend fun refreshLibrariesForActiveServerWithRetry(): AppResult<Unit> {
        repeat(3) { attempt ->
            val result = runCatching {
                sessionRepository.refreshLibrariesForActiveServer()
            }.getOrElse {
                AppResult.Error("Unable to load libraries for this server.", it)
            }

            if (result is AppResult.Success) {
                return result
            }

            val isLastAttempt = attempt == 2
            if (isLastAttempt || !isTransientPostLoginState((result as AppResult.Error).message)) {
                return result
            }

            delay(250)
        }
        return AppResult.Error("Unable to load libraries for this server.")
    }

    val uiState: StateFlow<LibraryPickerUiState> = combine(
        sessionRepository.observeLibrariesForActiveServer(),
        sessionRepository.observeSessionState(),
        sessionRepository.observeServers(),
        errorState,
        loadingState
    ) { libraries, session, servers, errorMessage, isLoading ->
        val activeServerName = when {
            session.activeServerId.isNullOrBlank() -> null
            else -> servers.firstOrNull { it.id == session.activeServerId }?.name ?: "Loading server..."
        }
        LibraryPickerUiState(
            libraries = libraries,
            activeLibraryId = session.activeLibraryId,
            activeServerName = activeServerName,
            errorMessage = errorMessage,
            isLoading = isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryPickerUiState()
    )

    fun onLibrarySelected(libraryId: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.setActiveLibrary(libraryId)) {
                is AppResult.Success -> {
                    errorState.value = null
                    mutableEvents.emit(LibraryPickerEvent.NavigateToMain)
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

    private fun isUnrecoverableLibraryPickerState(message: String?): Boolean {
        val normalized = message?.lowercase().orEmpty()
        return normalized.contains("no saved session")
    }

    private fun isTransientPostLoginState(message: String?): Boolean {
        val normalized = message?.lowercase().orEmpty()
        return normalized.contains("active server not found") ||
            normalized.contains("no active server selected")
    }
}

data class LibraryPickerUiState(
    val libraries: List<Library> = emptyList(),
    val activeLibraryId: String? = null,
    val activeServerName: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

sealed interface LibraryPickerEvent {
    data object NavigateToMain : LibraryPickerEvent
    data object NavigateToManageServers : LibraryPickerEvent
}
