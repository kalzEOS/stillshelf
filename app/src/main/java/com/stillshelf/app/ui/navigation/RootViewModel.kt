package com.stillshelf.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RootViewModel @Inject constructor(
    sessionRepository: SessionRepository
) : ViewModel() {

    val uiState: StateFlow<RootUiState> = sessionRepository.observeSessionState()
        .map { session ->
            RootUiState(
                isLoading = false,
                hasActiveServer = !session.activeServerId.isNullOrBlank(),
                hasActiveLibrary = !session.activeLibraryId.isNullOrBlank()
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RootUiState()
        )
}

data class RootUiState(
    val isLoading: Boolean = true,
    val hasActiveServer: Boolean = false,
    val hasActiveLibrary: Boolean = false
)
