package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.Library
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

data class HomeMenuUiState(
    val libraries: List<Library> = emptyList(),
    val activeLibraryId: String? = null,
    val isSwitchingLibrary: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeMenuViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeMenuUiState())
    val uiState: StateFlow<HomeMenuUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionRepository.observeLibrariesForActiveServer(),
                sessionRepository.observeSessionState()
            ) { libraries, sessionState ->
                libraries to sessionState.activeLibraryId
            }.collect { (libraries, activeLibraryId) ->
                mutableUiState.update {
                    it.copy(
                        libraries = libraries,
                        activeLibraryId = activeLibraryId
                    )
                }
            }
        }
    }

    fun switchLibrary(libraryId: String) {
        if (libraryId == uiState.value.activeLibraryId || uiState.value.isSwitchingLibrary) return
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

