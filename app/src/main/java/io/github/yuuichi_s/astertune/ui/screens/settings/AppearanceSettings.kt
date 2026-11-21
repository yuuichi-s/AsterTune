/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.FlatSubfoldersKey
import io.github.yuuichi_s.astertune.constants.SettingsGroupSpacing
import io.github.yuuichi_s.astertune.constants.ShowLikedAndDownloadedPlaylist
import io.github.yuuichi_s.astertune.constants.ShowScrollbarKey
import io.github.yuuichi_s.astertune.constants.SlimNavBarKey
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.GestureSettingsFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.LocalizationFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.TabArrangementFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.TabExtrasFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.ThemeAppFrag
import io.github.yuuichi_s.astertune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (slimNav, onSlimNavChange) = rememberPreference(SlimNavBarKey, defaultValue = false)
    val (showScrollbar, onShowScrollbarChange) = rememberPreference(ShowScrollbarKey, defaultValue = true)
    val (showLikedAndDownloadedPlaylist, onShowLikedAndDownloadedPlaylistChange) = rememberPreference(
        key = ShowLikedAndDownloadedPlaylist,
        defaultValue = true
    )
    val (flatSubfolders, onFlatSubfoldersChange) = rememberPreference(FlatSubfoldersKey, defaultValue = true)

    SettingsScaffold(
        title = stringResource(R.string.grp_appearance_controls),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.theme)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            ThemeAppFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_layout)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            TabArrangementFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_display)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            TabExtrasFrag()
            PreferenceEntry(
                title = { Text(stringResource(R.string.home_sections_title)) },
                description = stringResource(R.string.home_sections_description),
                icon = { Icon(Icons.Rounded.Home, null) },
                onClick = { navController.navigate("settings/home_sections") }
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.slim_navbar_title)) },
                description = stringResource(R.string.slim_navbar_description),
                icon = { Icon(Icons.Rounded.MoreHoriz, null) },
                checked = slimNav,
                onCheckedChange = onSlimNavChange
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.show_scrollbar_title)) },
                description = stringResource(R.string.show_scrollbar_description),
                icon = { Icon(Icons.Rounded.UnfoldMore, null) },
                checked = showScrollbar,
                onCheckedChange = onShowScrollbarChange
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.show_liked_and_downloaded_playlist)) },
                icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                checked = showLikedAndDownloadedPlaylist,
                onCheckedChange = onShowLikedAndDownloadedPlaylistChange
            )
            SwitchPreference(
                title = { Text(stringResource(R.string.flat_subfolders_title)) },
                description = stringResource(R.string.flat_subfolders_description),
                icon = { Icon(Icons.Rounded.FolderCopy, null) },
                checked = flatSubfolders,
                onCheckedChange = onFlatSubfoldersChange
            )
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_behavior)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            GestureSettingsFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_localization)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            LocalizationFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AppearanceSettingsPreview() {
    SettingsPreview {
        AppearanceSettings(
            navController = rememberNavController(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        )
    }
}
