package com.stillshelf.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.Library
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
                when (
                    val result = runCatching {
                        sessionRepository.refreshLibrariesForActiveServer()
                    }.getOrElse {
                        AppResult.Error("Unable to load libraries for this server.", it)
                    }
                ) {
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

    val uiState: StateFlow<LibraryPickerUiState> = combine(
        sessionRepository.observeLibrariesForActiveServer(),
        sessionRepository.observeSessionState(),
        errorState,
        loadingState
    ) { libraries, session, errorMessage, isLoading ->
        LibraryPickerUiState(
            libraries = libraries,
            activeLibraryId = session.activeLibraryId,
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
        return normalized.contains("active server not found") ||
            normalized.contains("no active server selected") ||
            normalized.contains("no saved session")
    }
}

data class LibraryPickerUiState(
    val libraries: List<Library> = emptyList(),
    val activeLibraryId: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

sealed interface LibraryPickerEvent {
    data object NavigateToMain : LibraryPickerEvent
    data object NavigateToManageServers : LibraryPickerEvent
}
