/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_READY
import coil3.compose.AsyncImage
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.DEFAULT_SWIPE_TO_SKIP
import io.github.yuuichi_s.astertune.constants.ListThumbnailSize
import io.github.yuuichi_s.astertune.constants.MiniPlayerHeight
import io.github.yuuichi_s.astertune.constants.SwipeToSkipKey
import io.github.yuuichi_s.astertune.constants.ThumbnailCornerRadius
import io.github.yuuichi_s.astertune.extensions.togglePlayPause
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview

/** Horizontal distance that always completes a mini player swipe. */
private val MiniPlayerSwipeDistanceThreshold: Dp = 64.dp

/** Shortest horizontal distance that a fast fling may complete a mini player swipe with. */
private val MiniPlayerFlingDistanceThreshold: Dp = 24.dp

/** Release speed, per second, that a fling must reach to complete a mini player swipe. */
private val MiniPlayerFlingVelocityThreshold: Dp = 1000.dp

internal enum class MiniPlayerSwipeDirection { NEXT, PREVIOUS }

/**
 * Decides which song a finished horizontal mini player gesture moves to, or null when the gesture
 * is too short or too slow. All arguments are pixels; velocity is pixels per second.
 *
 * A fling also requires the accumulated distance and the release velocity to share a direction, so
 * that a gesture reversed just before release does not skip.
 */
internal fun resolveMiniPlayerSwipeDirection(
    distancePx: Float,
    velocityPxPerSecond: Float,
    normalDistanceThresholdPx: Float,
    minimumFlingDistancePx: Float,
    velocityThresholdPxPerSecond: Float,
): MiniPlayerSwipeDirection? {
    if (distancePx == 0f) return null

    val isFling = abs(distancePx) >= minimumFlingDistancePx &&
            abs(velocityPxPerSecond) >= velocityThresholdPxPerSecond &&
            sign(velocityPxPerSecond) == sign(distancePx)
    if (abs(distancePx) < normalDistanceThresholdPx && !isFling) return null

    return if (distancePx < 0f) MiniPlayerSwipeDirection.NEXT else MiniPlayerSwipeDirection.PREVIOUS
}

/** Whether [targetIndex] refers to a song other than the one at [currentIndex]. */
internal fun canSeekToMiniPlayerSwipeTarget(targetIndex: Int, currentIndex: Int): Boolean =
    targetIndex != C.INDEX_UNSET && targetIndex != currentIndex

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val queueBoard by playerConnection.queueBoard.collectAsState()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val error by playerConnection.error.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val swipeToSkip by rememberPreference(SwipeToSkipKey, defaultValue = DEFAULT_SWIPE_TO_SKIP)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val normalDistanceThresholdPx = with(density) { MiniPlayerSwipeDistanceThreshold.toPx() }
    val minimumFlingDistancePx = with(density) { MiniPlayerFlingDistanceThreshold.toPx() }
    val velocityThresholdPxPerSecond = with(density) { MiniPlayerFlingVelocityThreshold.toPx() }

    var swipeDistancePx by remember { mutableFloatStateOf(0f) }
    val swipeState = rememberDraggableState { delta -> swipeDistancePx += delta }

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }


    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(500)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }


    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
//            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
    ) {
        LinearProgressIndicator(
            progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
            drawStopIndicator = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier

                .fillMaxSize(),
        ) {
            val iconButtonColor = MaterialTheme.colorScheme.onSecondaryContainer
            Box(
                Modifier
                    .weight(1f)
                    .draggable(
                        state = swipeState,
                        orientation = Orientation.Horizontal,
                        enabled = swipeToSkip && mediaMetadata != null,
                        onDragStarted = { swipeDistancePx = 0f },
                        onDragStopped = { velocity ->
                            val direction = resolveMiniPlayerSwipeDirection(
                                distancePx = swipeDistancePx,
                                velocityPxPerSecond = velocity,
                                normalDistanceThresholdPx = normalDistanceThresholdPx,
                                minimumFlingDistancePx = minimumFlingDistancePx,
                                velocityThresholdPxPerSecond = velocityThresholdPxPerSecond,
                            )
                            swipeDistancePx = 0f

                            val player = playerConnection.player
                            when (direction) {
                                MiniPlayerSwipeDirection.NEXT ->
                                    if (canSeekToMiniPlayerSwipeTarget(
                                            player.nextMediaItemIndex,
                                            player.currentMediaItemIndex
                                        )
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        player.seekToNext()
                                    }

                                MiniPlayerSwipeDirection.PREVIOUS ->
                                    if (canSeekToMiniPlayerSwipeTarget(
                                            player.previousMediaItemIndex,
                                            player.currentMediaItemIndex
                                        )
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        player.seekToPreviousMediaItem()
                                    }

                                null -> {}
                            }
                        },
                    )
            ) {
                mediaMetadata?.let {
                    MiniMediaInfo(
                        mediaMetadata = it,
                        error = error,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    if (playerConnection.player.currentMediaItem == null) {
                        queueBoard.setCurrQueue()
                        playerConnection.player.togglePlayPause()
                    } else if (playbackState == Player.STATE_ENDED) {
                        playerConnection.player.seekTo(0, 0)
                        playerConnection.player.playWhenReady = true
                    } else {
                        playerConnection.player.togglePlayPause()
                    }
                }
            ) {
                Icon(
                    imageVector = if (playbackState == Player.STATE_ENDED) Icons.Rounded.Replay else if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    tint = iconButtonColor,
                    contentDescription = null
                )
            }

            IconButton(
                enabled = canSkipNext,
                onClick = {
                    if (playerConnection.player.currentMediaItem == null) {
                        queueBoard.setCurrQueue()
                        playerConnection.player.playWhenReady = true
                    }
                    playerConnection.player.seekToNext()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    tint = iconButtonColor.copy(alpha = (if (canSkipNext) 1f else 0.5f)),
                    contentDescription = null
                )
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MiniMediaInfo(
    mediaMetadata: MediaMetadata,
    error: PlaybackException?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val playerConnection = LocalPlayerConnection.current
    val isWaitingForNetwork by playerConnection?.waitingForNetworkConnection?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }

    val px = (ListThumbnailSize.value * density.density).roundToInt()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(6.dp)
                .size(48.dp)
        ) {
            AsyncImage(
                model = mediaMetadata.getThumbnailModel(px, px),
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = error != null || isWaitingForNetwork,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius)
                        )
                ) {
                    if (isWaitingForNetwork) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = mediaMetadata.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MiniMediaInfoPreview() {
    CompositionLocalProvider(
        LocalPlayerConnection provides null,
    ) {
        MiniMediaInfo(
            mediaMetadata = MediaMetadata(
                id = "preview",
                title = "Preview Song Title",
                artists = listOf(MediaMetadata.Artist(id = null, name = "Preview Artist")),
                duration = 240,
                genre = null,
            ),
            error = null,
        )
    }
}
