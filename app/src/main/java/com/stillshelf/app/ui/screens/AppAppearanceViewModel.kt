package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppAppearanceUiState(
    val themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    val materialDesignEnabled: Boolean = false,
    val immersivePlayerEnabled: Boolean = false,
    val playerBottomToolsStyle: String = "dock"
)

@HiltViewModel
class AppAppearanceViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppAppearanceUiState())
    val uiState: StateFlow<AppAppearanceUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { state ->
                mutableUiState.update {
                    it.copy(
                        themeMode = parseThemeMode(state.appThemeMode),
                        materialDesignEnabled = state.materialDesignEnabled,
                        immersivePlayerEnabled = state.immersivePlayerEnabled,
                        playerBottomToolsStyle = state.playerBottomToolsStyle
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            sessionPreferences.setAppThemeMode(mode.toPreferenceValue())
        }
    }

    fun setMaterialDesignEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setMaterialDesignEnabled(enabled)
        }
    }

    fun setImmersivePlayerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setImmersivePlayerEnabled(enabled)
        }
    }

    fun setPlayerBottomToolsStyle(style: String) {
        viewModelScope.launch {
            sessionPreferences.setPlayerBottomToolsStyle(style)
        }
    }

    private fun parseThemeMode(raw: String?): AppThemeMode {
        return when (raw?.lowercase()) {
            "light" -> AppThemeMode.Light
            "dark" -> AppThemeMode.Dark
            else -> AppThemeMode.FollowSystem
        }
    }
}

private fun AppThemeMode.toPreferenceValue(): String {
    return when (this) {
        AppThemeMode.FollowSystem -> "follow_system"
        AppThemeMode.Light -> "light"
        AppThemeMode.Dark -> "dark"
    }
}
