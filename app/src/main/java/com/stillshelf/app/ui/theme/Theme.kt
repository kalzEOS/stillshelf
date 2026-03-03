package com.stillshelf.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = StillShelfPrimary,
    onPrimary = StillShelfOnPrimary,
    secondary = StillShelfSecondary,
    onSecondary = StillShelfOnSecondary,
    background = StillShelfBackground,
    onBackground = StillShelfOnBackground,
    surface = StillShelfSurface,
    onSurface = StillShelfOnSurface,
    surfaceVariant = StillShelfSurfaceVariant,
    onSurfaceVariant = StillShelfOnSurfaceVariant,
    outline = StillShelfOutline
)

private val DarkColors = darkColorScheme(
    primary = StillShelfOnPrimary,
    onPrimary = StillShelfPrimary,
    secondary = StillShelfSecondary,
    onSecondary = StillShelfOnSecondary,
    background = StillShelfDarkBackground,
    onBackground = StillShelfDarkOnBackground,
    surface = StillShelfDarkSurface,
    onSurface = StillShelfDarkOnSurface,
    surfaceVariant = StillShelfDarkSurfaceVariant,
    onSurfaceVariant = StillShelfDarkOnSurfaceVariant,
    outline = StillShelfDarkOutline
)

enum class AppThemeMode {
    FollowSystem,
    Light,
    Dark
}

@Composable
fun StillShelfTheme(
    themeMode: AppThemeMode,
    materialDesignEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val darkTheme = when (themeMode) {
        AppThemeMode.FollowSystem -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val colorScheme = when {
        materialDesignEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        materialDesignEnabled -> if (darkTheme) darkColorScheme() else lightColorScheme()
        darkTheme -> DarkColors
        else -> LightColors
    }
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(activity.window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            activity.window.navigationBarColor = colorScheme.surface.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (materialDesignEnabled) Typography() else StillShelfTypography,
        content = content
    )
}
