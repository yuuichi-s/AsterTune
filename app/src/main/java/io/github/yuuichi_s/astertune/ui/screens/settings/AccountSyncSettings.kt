/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.InnerTubeCookieKey
import io.github.yuuichi_s.astertune.constants.TopBarInsets
import io.github.yuuichi_s.astertune.ui.component.ColumnWithContentPadding
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.AccountExtrasFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.AccountFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SyncAutoFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SyncManualFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SyncParamsFrag
import io.github.yuuichi_s.astertune.ui.utils.backToMain
import io.github.yuuichi_s.astertune.utils.rememberPreference
import com.zionhuang.innertube.utils.parseCookieString

@SuppressLint("PrivateResource")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSyncSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.account)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            AccountFrag(navController)
        }
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(isLoggedIn) {
            Column {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AccountExtrasFrag()
                }
                Spacer(modifier = Modifier.height(16.dp))

                PreferenceGroupTitle(
                    title = stringResource(R.string.grp_sync)
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SyncAutoFrag()
                }
                Spacer(modifier = Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SyncManualFrag()
                }
                Spacer(modifier = Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SyncParamsFrag()
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.grp_account_sync)) },
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
