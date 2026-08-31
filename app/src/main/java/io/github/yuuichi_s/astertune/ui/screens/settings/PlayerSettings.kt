/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
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
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.NoCell
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.AudioDecoderKey
import io.github.yuuichi_s.astertune.constants.DEFAULT_AUDIO_DECODER
import io.github.yuuichi_s.astertune.constants.ENABLE_FFMETADATAEX
import io.github.yuuichi_s.astertune.constants.KeepAliveKey
import io.github.yuuichi_s.astertune.constants.StopMusicOnTaskClearKey
import io.github.yuuichi_s.astertune.constants.TopBarInsets
import io.github.yuuichi_s.astertune.ui.component.ColumnWithContentPadding
import io.github.yuuichi_s.astertune.ui.component.ListPreference
import io.github.yuuichi_s.astertune.ui.component.PreferenceGroupTitle
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.ui.dialog.InfoLabel
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.AudioQualityFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.NowPlayingFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.PlaybackBehaviourFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.PlayerGeneralFrag
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.SleepTimerFrag
import io.github.yuuichi_s.astertune.ui.utils.backToMain
import io.github.yuuichi_s.astertune.utils.rememberPreference
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioDecoder, onAudioDecoderChange) = rememberPreference(
        key = AudioDecoderKey,
        defaultValue = DEFAULT_AUDIO_DECODER
    )
    val (keepAlive, onKeepAliveChange) = rememberPreference(key = KeepAliveKey, defaultValue = false)
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        key = StopMusicOnTaskClearKey,
        defaultValue = true
    )

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.grp_general)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerGeneralFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_audio)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            AudioQualityFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_now_playing)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            NowPlayingFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_behavior)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PlaybackBehaviourFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.sleep_timer)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SleepTimerFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.advanced)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (ENABLE_FFMETADATAEX) {
                ListPreference(
                    title = { Text(stringResource(R.string.audio_decoder_preference)) },
                    icon = { Icon(Icons.Rounded.AudioFile, null) },
                    selectedValue = audioDecoder,
                    onValueSelected = onAudioDecoderChange,
                    values = listOf(
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
                    ),
                    valueText = {
                        when (it) {
                            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF -> stringResource(R.string.audio_decoder_system_only)
                            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON -> stringResource(R.string.audio_decoder_system_with_ffmpeg)
                            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER -> stringResource(R.string.audio_decoder_ffmpeg_only)
                            else -> {stringResource(R.string.error_unknown)}
                        }
                    }
                )
                InfoLabel(stringResource(R.string.restart_to_apply_changes))
            }
            SwitchPreference(
                title = { Text(stringResource(R.string.keep_alive_title)) },
                description = stringResource(R.string.keep_alive_description),
                icon = { Icon(Icons.Rounded.NoCell, null) },
                checked = keepAlive,
                onCheckedChange = {
                    if (it) {
                        onStopMusicOnTaskClearChange(false)
                    }
                    onKeepAliveChange(it)
                }
            )
        }
        Spacer(Modifier.height(96.dp))
    }


    TopAppBar(
        title = { Text(stringResource(R.string.player_and_audio)) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun PlayerSettingsPreview() {
    CompositionLocalProvider(
        LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
    ) {
        PlayerSettings(
            navController = rememberNavController(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        )
    }
}
