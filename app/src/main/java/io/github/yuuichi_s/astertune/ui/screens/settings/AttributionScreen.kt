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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.TopBarInsets
import io.github.yuuichi_s.astertune.ui.component.ColumnWithContentPadding
import io.github.yuuichi_s.astertune.ui.component.ContributorCard
import io.github.yuuichi_s.astertune.ui.component.ContributorInfo
import io.github.yuuichi_s.astertune.ui.component.ContributorType.CUSTOM
import io.github.yuuichi_s.astertune.ui.component.ContributorType.LEAD_DEVELOPER
import io.github.yuuichi_s.astertune.ui.component.ContributorType.MAINTAINER
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.ui.utils.backToMain
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributionScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            maintainers.map {
                ContributorCard(it)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

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
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            ContributorCard(
                contributor = ContributorInfo (
                    name = stringResource(R.string.all_contributors),
                    type = listOf(CUSTOM),
                    url = "https://github.com/OuterTune/OuterTune/graphs/contributors"
                )
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.attribution_title)) },
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

val maintainers = mutableListOf(
    ContributorInfo(
        name = "Davide Garberi",
        alias = "DD3Boh",
        type = listOf(LEAD_DEVELOPER),
        url = "https://github.com/DD3Boh"
    ),
    ContributorInfo(
        name = "Michael Zh",
        alias = "mikooomich",
        type = listOf(LEAD_DEVELOPER, MAINTAINER),
        url = "https://github.com/mikooomich"
    ),
)

val contributors = mutableListOf(
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
    CompositionLocalProvider(
        LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
    ) {
        AttributionScreen(
            navController = rememberNavController(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        )
    }
}
