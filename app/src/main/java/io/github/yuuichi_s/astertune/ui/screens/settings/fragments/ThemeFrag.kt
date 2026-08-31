/*
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings.fragments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.CustomThemeColorKey
import io.github.yuuichi_s.astertune.constants.CustomThemeKey
import io.github.yuuichi_s.astertune.constants.DarkMode
import io.github.yuuichi_s.astertune.constants.DarkModeKey
import io.github.yuuichi_s.astertune.constants.DynamicThemeKey
import io.github.yuuichi_s.astertune.constants.PureBlackKey
import io.github.yuuichi_s.astertune.ui.component.EnumListPreference
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.dialog.ActionPromptDialog
import io.github.yuuichi_s.astertune.ui.theme.DefaultThemeColor
import io.github.yuuichi_s.astertune.ui.theme.PresetThemeColors
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ColumnScope.ThemeAppFrag() {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = true)
    val (customTheme, onCustomThemeChange) = rememberPreference(CustomThemeKey, defaultValue = false)
    val (customThemeColor, onCustomThemeColorChange) = rememberPreference(
        CustomThemeColorKey,
        defaultValue = DefaultThemeColor.toArgb()
    )

    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)

    var showColorPicker by rememberSaveable { mutableStateOf(false) }

    // Dynamic theme and a custom accent color are mutually exclusive: enabling one disables the
    // other so both toggles stay visible without items appearing or disappearing unexpectedly.
    SwitchPreference(
        title = { Text(stringResource(R.string.enable_dynamic_theme)) },
        icon = { Icon(Icons.Rounded.Palette, null) },
        checked = dynamicTheme,
        onCheckedChange = { checked ->
            onDynamicThemeChange(checked)
            if (checked) onCustomThemeChange(false)
        }
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.custom_accent_color)) },
        description = stringResource(R.string.custom_accent_color_description),
        icon = { Icon(Icons.Rounded.ColorLens, null) },
        checked = customTheme,
        onCheckedChange = { checked ->
            onCustomThemeChange(checked)
            if (checked) onDynamicThemeChange(false)
        }
    )
    AnimatedVisibility(customTheme) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.accent_color)) },
            icon = { Icon(Icons.Rounded.Palette, null) },
            trailingContent = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(customThemeColor))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            },
            onClick = { showColorPicker = true }
        )
    }
    EnumListPreference(
        title = { Text(stringResource(R.string.dark_theme)) },
        icon = { Icon(Icons.Rounded.DarkMode, null) },
        selectedValue = darkMode,
        onValueSelected = onDarkModeChange,
        valueText = {
            when (it) {
                DarkMode.ON -> stringResource(R.string.dark_theme_on)
                DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
            }
        }
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.pure_black)) },
        icon = { Icon(Icons.Rounded.Contrast, null) },
        checked = pureBlack,
        onCheckedChange = onPureBlackChange
    )

    if (showColorPicker) {
        AccentColorPickerDialog(
            selectedColor = Color(customThemeColor),
            onColorSelected = {
                onCustomThemeColorChange(it.toArgb())
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun AccentColorPickerDialog(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    // Store the in-progress selection as an argb Int so it survives configuration changes
    // (Color has no default Saver).
    var tempColorArgb by rememberSaveable { mutableStateOf(selectedColor.toArgb()) }
    val tempColor = Color(tempColorArgb)

    ActionPromptDialog(
        title = stringResource(R.string.accent_color),
        onDismiss = onDismiss,
        onConfirm = { onColorSelected(tempColor) },
        onCancel = onDismiss,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            PresetThemeColors.forEach { color ->
                val isSelected = color == tempColor
                val colorHex = String.format("#%06X", 0xFFFFFF and color.toArgb())
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { tempColorArgb = color.toArgb() }
                        )
                        .semantics { contentDescription = colorHex }
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun ThemeAppFragPreview() {
    Column {
        ThemeAppFrag()
    }
}

