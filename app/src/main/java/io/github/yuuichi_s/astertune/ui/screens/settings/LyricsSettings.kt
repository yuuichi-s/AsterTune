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
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.LyricAdvancedFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.LyricFormatFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.LyricParserFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.LyricSourceFrag
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {

    SettingsScaffold(
        title = stringResource(R.string.lyrics_settings_title),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.grp_lyrics_source)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            LyricSourceFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_lyrics_parser)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            LyricParserFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_lyrics_format)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            LyricFormatFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.advanced)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            LyricAdvancedFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun LyricsSettingsPreview() {
    SettingsPreview {
        LyricsSettings(
            navController = rememberNavController(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        )
    }
}
