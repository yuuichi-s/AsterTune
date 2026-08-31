/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import coil3.compose.AsyncImage
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.constants.PlayerHorizontalPadding
import io.github.yuuichi_s.astertune.constants.ShowLyricsKey
import io.github.yuuichi_s.astertune.constants.ThumbnailCornerRadius
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.ui.component.Lyrics
import io.github.yuuichi_s.astertune.utils.rememberPreference

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyricsOnClick: Boolean = false,
    customMediaMetadata: MediaMetadata? = null
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    var showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    val playerMediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val error by playerConnection.error.collectAsState()
    val mediaMetadata = customMediaMetadata ?: playerMediaMetadata

    DisposableEffect(showLyrics) {
        currentView.keepScreenOn = showLyrics
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !showLyrics && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f, false)
                ) {
                    AsyncImage(
                        model = mediaMetadata?.getThumbnailModel(),
                        contentDescription = null,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius * 2))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = showLyricsOnClick,
                            ) {
                                showLyrics = !showLyrics
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showLyrics && error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Lyrics(sliderPositionProvider = sliderPositionProvider)
        }

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            error?.let { error ->
                ThumbnailPlaybackError(
                    error = error,
                    retry = playerConnection.player::prepare
                )
            }
        }
    }
}
