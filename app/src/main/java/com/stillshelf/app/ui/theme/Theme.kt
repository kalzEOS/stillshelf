package com.stillshelf.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
    val baseColorScheme = when {
        materialDesignEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        materialDesignEnabled -> if (darkTheme) DarkColors else LightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = if (materialDesignEnabled) {
        baseColorScheme.withPronouncedSectionSurfaces(darkTheme = darkTheme)
    } else {
        baseColorScheme
    }
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(activity.window, view)
            val statusBarColor = colorScheme.surface
            val navigationBarColor = if (materialDesignEnabled) {
                colorScheme.surfaceContainer
            } else {
                colorScheme.surface
            }
            insetsController.isAppearanceLightStatusBars = statusBarColor.luminance() > 0.5f
            insetsController.isAppearanceLightNavigationBars = navigationBarColor.luminance() > 0.5f
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = statusBarColor.toArgb()
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = navigationBarColor.toArgb()
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (materialDesignEnabled) Typography() else StillShelfTypography,
        content = content
    )
}

private fun ColorScheme.withPronouncedSectionSurfaces(darkTheme: Boolean): ColorScheme {
    val sectionLift = if (darkTheme) 0.035f else 0.025f
    val cardLift = if (darkTheme) 0.02f else 0.015f
    val outlineLift = if (darkTheme) 0.1f else 0.06f

    // Keep Material mode panels close to the base dark surface, with gentle separation.
    val elevatedSurface = lerp(surface, surfaceVariant, cardLift)
    val groupedSurface = lerp(surfaceVariant, surface, if (darkTheme) 0.35f else 0.2f)

    return copy(
        background = lerp(background, surface, sectionLift),
        surface = elevatedSurface,
        surfaceVariant = groupedSurface,
        outline = lerp(outline, onSurface, outlineLift),
        surfaceContainerLow = lerp(surfaceContainerLow, surface, if (darkTheme) 0.3f else 0.18f),
        surfaceContainer = lerp(surfaceContainer, surface, if (darkTheme) 0.35f else 0.22f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, surface, if (darkTheme) 0.4f else 0.26f),
        surfaceContainerHighest = lerp(surfaceContainerHighest, surface, if (darkTheme) 0.45f else 0.3f)
    )
}
