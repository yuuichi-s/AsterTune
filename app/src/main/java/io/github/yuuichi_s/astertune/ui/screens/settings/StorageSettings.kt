/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.SettingsGroupSpacing
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.DownloadsFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.ImageCacheFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SongCacheFrag
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import io.github.yuuichi_s.astertune.LocalPlayerConnection


@SuppressLint("PrivateResource")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {

    SettingsScaffold(
        title = stringResource(R.string.storage),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.downloaded_songs)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth().animateContentSize()
        ) {
            DownloadsFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))


        PreferenceGroupTitle(
            title = stringResource(R.string.song_cache)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SongCacheFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.image_cache)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            ImageCacheFrag()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun StorageSettingsPreview() {
    SettingsPreview {
        CompositionLocalProvider(
            LocalPlayerConnection provides null,
        ) {
            StorageSettings(
                navController = rememberNavController(),
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            )
        }
    }
}
