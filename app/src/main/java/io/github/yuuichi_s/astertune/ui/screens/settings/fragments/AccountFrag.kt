/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.ui.screens.settings.fragments

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.App.Companion.forgetAccount
import io.github.yuuichi_s.astertune.LocalAccountImageFetcher
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.AccountChannelHandleKey
import io.github.yuuichi_s.astertune.constants.AccountEmailKey
import io.github.yuuichi_s.astertune.constants.AccountImageFetchedKey
import io.github.yuuichi_s.astertune.constants.AccountImageUrlKey
import io.github.yuuichi_s.astertune.constants.AccountNameKey
import io.github.yuuichi_s.astertune.constants.DataSyncIdKey
import io.github.yuuichi_s.astertune.constants.InnerTubeCookieKey
import io.github.yuuichi_s.astertune.constants.UseLoginForBrowse
import io.github.yuuichi_s.astertune.constants.VisitorDataKey
import io.github.yuuichi_s.astertune.ui.component.AccountAvatar
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.dialog.InfoLabel
import io.github.yuuichi_s.astertune.ui.dialog.TextFieldDialog
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.utils.parseCookieString
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun ColumnScope.AccountFrag(navController: NavController) {
    val context = LocalContext.current

    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val visitorData by rememberPreference(VisitorDataKey, "")
    val dataSyncId by rememberPreference(DataSyncIdKey, "")
    val accountImageFetcher = LocalAccountImageFetcher.current
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    // temp vars
    var showToken: Boolean by remember {
        mutableStateOf(false)
    }
    var showTokenEditor by remember {
        mutableStateOf(false)
    }

    PreferenceEntry(
        title = { Text(if (isLoggedIn) accountName else stringResource(R.string.login)) },
        description = if (isLoggedIn) {
            accountEmail.takeIf { it.isNotEmpty() }
                ?: accountChannelHandle.takeIf { it.isNotEmpty() }
        } else null,
        icon = { AccountAvatar { Icon(Icons.Rounded.Person, null) } },
        onClick = { navController.navigate("login") }
    )
    if (isLoggedIn) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.action_logout)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
            onClick = {
                forgetAccount(context)
            }
        )
        Spacer(Modifier.height(8.dp))
        InfoLabel(stringResource(R.string.action_logout_tooltip))
        Spacer(Modifier.height(24.dp))
    }

    PreferenceEntry(
        title = {
            if (showToken) {
                Text(stringResource(R.string.token_shown))
                Text(
                    text = if (isLoggedIn) innerTubeCookie else stringResource(R.string.not_logged_in),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1 // just give a preview so user knows it's at least there
                )
            } else {
                Text(stringResource(R.string.token_hidden))
            }
        },
        onClick = {
            if (showToken == false) {
                showToken = true
            } else {
                showTokenEditor = true
            }
        },
    )


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showTokenEditor) {
        val text =
            "***INNERTUBE COOKIE*** =${innerTubeCookie}\n\n***VISITOR DATA*** =${visitorData}\n\n***DATASYNC ID*** =${dataSyncId}\n\n***ACCOUNT NAME*** =${accountName}\n\n***ACCOUNT EMAIL*** =${accountEmail}\n\n***ACCOUNT CHANNEL HANDLE*** =${accountChannelHandle}"
        TextFieldDialog(
            modifier = Modifier,
            initialTextFieldValue = TextFieldValue(text),
            onDone = { data ->
                // Not tied to this screen, so that neither the write nor the fetch is cancelled
                // when the dialog and the settings screen go away.
                GlobalScope.launch {
                    context.dataStore.edit { settings ->
                        data.split("\n").forEach { line ->
                            TOKEN_EDITOR_FIELDS.firstOrNull { line.startsWith(it.first) }?.let { (prefix, key) ->
                                settings[key] = line.substringAfter(prefix)
                            }
                        }
                        // The stored image belongs to the credentials being replaced.
                        settings.remove(AccountImageUrlKey)
                        settings.remove(AccountImageFetchedKey)
                    }
                    accountImageFetcher.fetch()
                }
            },
            onDismiss = { showTokenEditor = false },
            singleLine = false,
            maxLines = 20,
            isInputValid = {
                it.isNotEmpty() &&
                        try {
                            "SAPISID" in parseCookieString(it)
                            true
                        } catch (e: Exception) {
                            false
                        }
            },
            extraContent = {
                InfoLabel(text = stringResource(R.string.token_adv_login_description))
            }
        )
    }
}

private val TOKEN_EDITOR_FIELDS = listOf(
    "***INNERTUBE COOKIE*** =" to InnerTubeCookieKey,
    "***VISITOR DATA*** =" to VisitorDataKey,
    "***DATASYNC ID*** =" to DataSyncIdKey,
    "***ACCOUNT NAME*** =" to AccountNameKey,
    "***ACCOUNT EMAIL*** =" to AccountEmailKey,
    "***ACCOUNT CHANNEL HANDLE*** =" to AccountChannelHandleKey,
)

@Composable
fun ColumnScope.AccountExtrasFrag() {
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)

    SwitchPreference(
        title = { Text(stringResource(R.string.use_login_for_browse)) },
        description = stringResource(R.string.use_login_for_browse_desc),
        icon = { Icon(Icons.Rounded.Person, null) },
        checked = useLoginForBrowse,
        onCheckedChange = {
            YouTube.useLoginForBrowse = it
            onUseLoginForBrowseChange(it)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AccountFragPreview() {
    Column {
        AccountFrag(navController = rememberNavController())
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountExtrasFragPreview() {
    Column {
        AccountExtrasFrag()
    }
}
