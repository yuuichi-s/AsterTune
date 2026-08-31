/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.FlatSubfoldersKey
import io.github.yuuichi_s.astertune.constants.ShowLikedAndDownloadedPlaylist
import io.github.yuuichi_s.astertune.constants.TopBarInsets
import io.github.yuuichi_s.astertune.ui.component.ColumnWithContentPadding
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.LocalizationFrag
import io.github.yuuichi_s.astertune.ui.utils.backToMain
import io.github.yuuichi_s.astertune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (showLikedAndDownloadedPlaylist, onShowLikedAndDownloadedPlaylistChange) = rememberPreference(
        key = ShowLikedAndDownloadedPlaylist,
        defaultValue = true
    )
    val (flatSubfolders, onFlatSubfoldersChange) = rememberPreference(FlatSubfoldersKey, defaultValue = true)

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.grp_localization)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            LocalizationFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_display)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
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
        Spacer(Modifier.height(96.dp))
    }


    TopAppBar(
        title = { Text(stringResource(R.string.grp_library_and_content)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        windowInsets = TopBarInsets,
        scrollBehavior = scrollBehavior
    )
}
