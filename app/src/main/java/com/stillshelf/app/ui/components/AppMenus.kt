package com.stillshelf.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu as M3DropdownMenu
import androidx.compose.material3.DropdownMenuItem as M3DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.stillshelf.app.ui.theme.LocalMaterialDesignEnabled

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    properties: PopupProperties = PopupProperties(focusable = true),
    containerColorOverride: Color? = null,
    borderOverride: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val materialDesignEnabled = LocalMaterialDesignEnabled.current
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = colorScheme.background.luminance() < 0.5f
    val containerColor = containerColorOverride ?: if (materialDesignEnabled) {
        MenuDefaults.containerColor
    } else {
        colorScheme.surface.copy(alpha = if (darkTheme) 0.96f else 0.98f)
    }
    val border = borderOverride ?: if (materialDesignEnabled) {
        null
    } else {
        BorderStroke(
            width = 1.dp,
            color = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.1f)
        )
    }

    M3DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        properties = properties,
        shape = RoundedCornerShape(10.dp),
        containerColor = containerColor,
        tonalElevation = if (materialDesignEnabled) 8.dp else 0.dp,
        shadowElevation = if (materialDesignEnabled) 8.dp else 6.dp,
        border = border,
        content = content
    )
}

@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors? = null,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null
) {
    val resolvedColors = colors ?: appMenuItemColors()
    M3DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = resolvedColors,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    )
}

@Composable
private fun appMenuItemColors(): MenuItemColors {
    val materialDesignEnabled = LocalMaterialDesignEnabled.current
    if (materialDesignEnabled) return MenuDefaults.itemColors()

    val colorScheme = MaterialTheme.colorScheme
    val text = colorScheme.onSurface
    val icon = colorScheme.onSurfaceVariant
    val disabledText = colorScheme.onSurface.copy(alpha = 0.42f)
    val disabledIcon = colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    return MenuDefaults.itemColors(
        textColor = text,
        leadingIconColor = icon,
        trailingIconColor = icon,
        disabledTextColor = disabledText,
        disabledLeadingIconColor = disabledIcon,
        disabledTrailingIconColor = disabledIcon
    )
}
