/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.ProxyEnabledKey
import io.github.yuuichi_s.astertune.constants.ProxyTypeKey
import io.github.yuuichi_s.astertune.constants.ProxyUrlKey
import io.github.yuuichi_s.astertune.constants.SettingsBottomSpacing
import io.github.yuuichi_s.astertune.constants.SettingsGroupSpacing
import io.github.yuuichi_s.astertune.ui.component.EditTextPreference
import io.github.yuuichi_s.astertune.ui.component.ListPreference
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.ListenHistoryFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SearchHistoryFrag
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference
import java.net.Proxy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")

    SettingsScaffold(
        title = stringResource(R.string.privacy),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListenHistoryFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SearchHistoryFrag()
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_proxy)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_proxy)) },
                checked = proxyEnabled,
                onCheckedChange = onProxyEnabledChange
            )

            AnimatedVisibility(proxyEnabled) {
                Column {
                    ListPreference(
                        title = { Text(stringResource(R.string.proxy_type)) },
                        selectedValue = proxyType,
                        values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                        valueText = { it.name },
                        onValueSelected = onProxyTypeChange
                    )
                    EditTextPreference(
                        title = { Text(stringResource(R.string.proxy_url)) },
                        value = proxyUrl,
                        onValueChange = onProxyUrlChange
                    )
                }
            }
        }
        Spacer(Modifier.height(SettingsBottomSpacing))
    }
}
