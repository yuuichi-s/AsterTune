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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.InnerTubeCookieKey
import io.github.yuuichi_s.astertune.constants.SettingsGroupSpacing
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.AccountExtrasFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.AccountFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SyncAutoFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SyncManualFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SyncParamsFrag
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

    SettingsScaffold(
        title = stringResource(R.string.grp_account_sync),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.account)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            AccountFrag(navController)
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        AnimatedVisibility(isLoggedIn) {
            Column {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AccountExtrasFrag()
                }
                Spacer(modifier = Modifier.height(SettingsGroupSpacing))

                PreferenceGroupTitle(
                    title = stringResource(R.string.grp_sync)
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SyncAutoFrag()
                }
                Spacer(modifier = Modifier.height(SettingsGroupSpacing))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SyncManualFrag()
                }
                Spacer(modifier = Modifier.height(SettingsGroupSpacing))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SyncParamsFrag()
                }
                Spacer(modifier = Modifier.height(SettingsGroupSpacing))
            }
        }
    }
}
