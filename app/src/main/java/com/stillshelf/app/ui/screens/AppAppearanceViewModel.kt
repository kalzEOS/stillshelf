package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class AppAppearanceUiState(
    val themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    val navidromeThemeMode: AppThemeMode = AppThemeMode.FollowSystem,
    val materialDesignEnabled: Boolean = false,
    val navidromeMaterialDesignEnabled: Boolean = false,
    val navidromeImmersivePlayerEnabled: Boolean = false,
    val immersivePlayerEnabled: Boolean = false,
    val playerBottomToolsStyle: String = "dock"
) {
    fun themeModeForBackend(selectedBackend: BackendProvider?): AppThemeMode {
        return if (selectedBackend == BackendProvider.NAVIDROME) {
            navidromeThemeMode
        } else {
            themeMode
        }
    }

    fun materialDesignEnabledForBackend(selectedBackend: BackendProvider?): Boolean {
        return if (selectedBackend == BackendProvider.NAVIDROME) {
            navidromeMaterialDesignEnabled
        } else {
            materialDesignEnabled
        }
    }
}

@HiltViewModel
class AppAppearanceViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppAppearanceUiState())
    val uiState: StateFlow<AppAppearanceUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state
                .map { state ->
                    AppAppearanceUiState(
                        themeMode = parseThemeMode(state.appThemeMode),
                        navidromeThemeMode = parseThemeMode(state.navidromeThemeMode),
                        materialDesignEnabled = state.materialDesignEnabled,
                        navidromeMaterialDesignEnabled = state.navidromeMaterialDesignEnabled,
                        navidromeImmersivePlayerEnabled = state.navidromeImmersivePlayerEnabled,
                        immersivePlayerEnabled = state.immersivePlayerEnabled,
                        playerBottomToolsStyle = state.playerBottomToolsStyle
                    )
                }
                .distinctUntilChanged()
                .collect { appearanceState ->
                    mutableUiState.value = appearanceState
                }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            sessionPreferences.setAppThemeMode(mode.toPreferenceValue())
        }
    }

    fun setNavidromeThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeThemeMode(mode.toPreferenceValue())
        }
    }

    fun setMaterialDesignEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setMaterialDesignEnabled(enabled)
        }
    }

    fun setNavidromeMaterialDesignEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeMaterialDesignEnabled(enabled)
        }
    }

    fun setNavidromeImmersivePlayerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeImmersivePlayerEnabled(enabled)
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
