/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.TopBarInsets
import io.github.yuuichi_s.astertune.utils.LastFmScrobbler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the Last.fm page where the user grants AsterTune access. Last.fm then opens
 * [LastFmScrobbler.CALLBACK_URL] with a token, which this screen intercepts and exchanges for a session.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LastFmLoginScreen(
    navController: NavController,
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val callback = LastFmScrobbler.CALLBACK_URL.toUri()

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    private var handled = false

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (url.scheme != callback.scheme || url.host != callback.host) return false
                        if (handled) return true

                        handled = true

                        val token = url.getQueryParameter("token")
                        val appContext = context.applicationContext
                        // Not tied to this screen, which closes right away.
                        GlobalScope.launch {
                            val connected = token != null && LastFmScrobbler.connect(appContext, token)
                            if (!connected) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(appContext, R.string.lastfm_connect_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        navController.navigateUp()
                        return true
                    }
                }
                settings.javaScriptEnabled = true
                webView = this
                loadUrl(LastFmScrobbler.authUrl())
            }
        }
    )

    TopAppBar(
        title = { Text(stringResource(R.string.lastfm_connect)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        windowInsets = TopBarInsets,
    )

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack())
            view.goBack()
        else
            navController.navigateUp()
    }
}
