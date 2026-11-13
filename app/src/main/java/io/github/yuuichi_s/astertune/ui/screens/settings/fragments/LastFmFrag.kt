/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings.fragments

import android.net.Uri
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.LastFmNeedsReconnectKey
import io.github.yuuichi_s.astertune.constants.LastFmScrobbleEnabledKey
import io.github.yuuichi_s.astertune.constants.LastFmSessionKey
import io.github.yuuichi_s.astertune.constants.LastFmUsernameKey
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.dialog.InfoLabel
import io.github.yuuichi_s.astertune.utils.LastFmScrobbler
import io.github.yuuichi_s.astertune.utils.rememberPreference

/** Last.fm connection controls, profile link, and scrobbling preference. */
@Composable
fun ColumnScope.LastFmFrag(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val sessionKey by rememberPreference(LastFmSessionKey, "")
    val username by rememberPreference(LastFmUsernameKey, "")
    val needsReconnect by rememberPreference(LastFmNeedsReconnectKey, false)
    val (scrobbleEnabled, onScrobbleEnabledChange) = rememberPreference(LastFmScrobbleEnabledKey, true)

    if (sessionKey.isEmpty()) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.lastfm_connect)) },
            icon = { Icon(Icons.Rounded.Link, null) },
            onClick = { navController.navigate("lastfm_login") }
        )
        return
    }

    PreferenceEntry(
        title = { Text(username) },
        description = stringResource(R.string.lastfm_open_profile),
        icon = { Icon(Icons.Rounded.Person, null) },
        onClick = { uriHandler.openUri("https://www.last.fm/user/" + Uri.encode(username)) }
    )
    if (needsReconnect) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.lastfm_reconnect)) },
            description = stringResource(R.string.lastfm_reconnect_desc),
            icon = { Icon(Icons.Rounded.Sync, null) },
            onClick = { navController.navigate("lastfm_login") }
        )
    }
    SwitchPreference(
        title = { Text(stringResource(R.string.lastfm_scrobble)) },
        icon = { Icon(Icons.Rounded.History, null) },
        checked = scrobbleEnabled,
        onCheckedChange = onScrobbleEnabledChange
    )
    PreferenceEntry(
        title = { Text(stringResource(R.string.lastfm_disconnect)) },
        icon = { Icon(Icons.Rounded.LinkOff, null) },
        onClick = { LastFmScrobbler.disconnect(context) }
    )
    Spacer(Modifier.height(8.dp))
    InfoLabel(stringResource(R.string.lastfm_disconnect_desc))
    Spacer(Modifier.height(24.dp))
}
