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
    private val mutableEvents = MutableSharedFlow<LibraryPickerEvent>()

    val events = mutableEvents.asSharedFlow()

    val uiState: StateFlow<LibraryPickerUiState> = combine(
        sessionRepository.observeLibrariesForActiveServer(),
        sessionRepository.observeSessionState(),
        errorState
    ) { libraries, session, errorMessage ->
        LibraryPickerUiState(
            libraries = libraries,
            activeLibraryId = session.activeLibraryId,
            errorMessage = errorMessage
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
}

data class LibraryPickerUiState(
    val libraries: List<Library> = emptyList(),
    val activeLibraryId: String? = null,
    val errorMessage: String? = null
)

sealed interface LibraryPickerEvent {
    data object NavigateToMain : LibraryPickerEvent
}
