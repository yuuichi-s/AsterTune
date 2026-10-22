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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.SettingsGroupSpacing
import io.github.yuuichi_s.astertune.ui.component.ContributorCard
import io.github.yuuichi_s.astertune.ui.component.ContributorInfo
import io.github.yuuichi_s.astertune.ui.component.ContributorType.CUSTOM
import io.github.yuuichi_s.astertune.ui.component.ContributorType.LEAD_DEVELOPER
import io.github.yuuichi_s.astertune.ui.component.ContributorType.MAINTAINER
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributionScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    SettingsScaffold(
        title = stringResource(R.string.attribution_title),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            maintainers.map {
                ContributorCard(it)
            }
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            ContributorCard(
                contributor = ContributorInfo (
                    name = stringResource(R.string.all_contributors),
                    type = listOf(CUSTOM),
                    url = "https://github.com/yuuichi-s/AsterTune/graphs/contributors"
                )
            )
        }

        PreferenceGroupTitle(title = stringResource(R.string.att_based_on))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            contributors.map {
                ContributorCard(
                    contributor = it,
                    descriptionColour = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

val maintainers = mutableListOf(
    ContributorInfo(
        name = "yuuichi",
        alias = "yuuichi-s",
        type = listOf(LEAD_DEVELOPER, MAINTAINER),
        url = "https://github.com/yuuichi-s"
    ),
)

val contributors = mutableListOf(
    ContributorInfo(
        name = "Davide Garberi",
        alias = "DD3Boh",
        type = listOf(CUSTOM),
        description = "OuterTune developer",
        url = "https://github.com/DD3Boh"
    ),
    ContributorInfo(
        name = "Michael Zh",
        alias = "mikooomich",
        type = listOf(CUSTOM),
        description = "OuterTune developer",
        url = "https://github.com/mikooomich"
    ),
    ContributorInfo(
        name = "Zion Huang",
        alias = "z-huang",
        type = listOf(CUSTOM),
        description = "InnerTune creator",
        url = "https://github.com/z-huang"
    ),
)

//val translators = mutableListOf(
//)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AttributionScreenPreview() {
    SettingsPreview {
        AttributionScreen(
            navController = rememberNavController(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        )
    }
}
