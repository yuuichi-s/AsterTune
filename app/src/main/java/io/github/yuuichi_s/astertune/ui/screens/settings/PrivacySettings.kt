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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.ProxyEnabledKey
import io.github.yuuichi_s.astertune.constants.ProxyTypeKey
import io.github.yuuichi_s.astertune.constants.ProxyUrlKey
import io.github.yuuichi_s.astertune.constants.TopBarInsets
import io.github.yuuichi_s.astertune.ui.component.ColumnWithContentPadding
import io.github.yuuichi_s.astertune.ui.component.EditTextPreference
import io.github.yuuichi_s.astertune.ui.component.ListPreference
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.ListenHistoryFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SearchHistoryFrag
import io.github.yuuichi_s.astertune.ui.utils.backToMain
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

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListenHistoryFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SearchHistoryFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

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
        Spacer(Modifier.height(96.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.privacy)) },
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
