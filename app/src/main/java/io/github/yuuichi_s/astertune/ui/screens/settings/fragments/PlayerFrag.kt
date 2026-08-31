package io.github.yuuichi_s.astertune.ui.screens.settings.fragments

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.AudioNormalizationKey
import io.github.yuuichi_s.astertune.constants.AudioQuality
import io.github.yuuichi_s.astertune.constants.AudioQualityKey
import io.github.yuuichi_s.astertune.constants.AutoLoadMoreKey
import io.github.yuuichi_s.astertune.constants.DEFAULT_PLAYER_BACKGROUND
import io.github.yuuichi_s.astertune.constants.DEFAULT_SHOW_LYRICS_ON_CLICK
import io.github.yuuichi_s.astertune.constants.DEFAULT_SLIDER_STYLE
import io.github.yuuichi_s.astertune.constants.DEFAULT_SWIPE_TO_SKIP
import io.github.yuuichi_s.astertune.constants.IgnoreAudioFocusKey
import io.github.yuuichi_s.astertune.constants.KeepAliveKey
import io.github.yuuichi_s.astertune.constants.PersistentQueueKey
import io.github.yuuichi_s.astertune.constants.PlayerBackgroundStyle
import io.github.yuuichi_s.astertune.constants.PlayerBackgroundStyleKey
import io.github.yuuichi_s.astertune.constants.SeekIncrement
import io.github.yuuichi_s.astertune.constants.SeekIncrementKey
import io.github.yuuichi_s.astertune.constants.ShowLyricsOnClickKey
import io.github.yuuichi_s.astertune.constants.ShowQueueTitleKey
import io.github.yuuichi_s.astertune.constants.SkipOnErrorKey
import io.github.yuuichi_s.astertune.constants.SkipSilenceKey
import io.github.yuuichi_s.astertune.constants.SleepTimerDefaultMinutesKey
import io.github.yuuichi_s.astertune.constants.SleepTimerDefaults
import io.github.yuuichi_s.astertune.constants.SleepTimerFadeDurationKey
import io.github.yuuichi_s.astertune.constants.SleepTimerFadeKey
import io.github.yuuichi_s.astertune.constants.SleepTimerShowOnPlayerKey
import io.github.yuuichi_s.astertune.constants.SliderStyle
import io.github.yuuichi_s.astertune.constants.SliderStyleKey
import io.github.yuuichi_s.astertune.constants.StopMusicOnTaskClearKey
import io.github.yuuichi_s.astertune.constants.SwipeToSkipKey
import io.github.yuuichi_s.astertune.constants.minPlaybackDurKey
import io.github.yuuichi_s.astertune.ui.component.EnumListPreference
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.dialog.CounterDialog
import io.github.yuuichi_s.astertune.ui.menu.SleepTimerDefaultTimeDialog
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PlayerGeneralFrag() {
    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(AutoLoadMoreKey, defaultValue = true)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(key = SkipSilenceKey, defaultValue = false)

    val context = LocalContext.current
    val (seekIncrement, onSeekIncrementChange) = rememberEnumPreference(
        key = SeekIncrementKey,
        defaultValue = SeekIncrement.OFF
    )

    SwitchPreference(
        title = { Text(stringResource(R.string.auto_load_more)) },
        description = stringResource(R.string.auto_load_more_desc),
        icon = { Icon(Icons.Rounded.Autorenew, null) },
        checked = autoLoadMore,
        onCheckedChange = onAutoLoadMoreChange
    )
    EnumListPreference(
        title = { Text(stringResource(R.string.seek_increment))},
        icon = { Icon(Icons.Rounded.FastForward, null) },
        selectedValue = seekIncrement,
        onValueSelected = onSeekIncrementChange,
        valueText = {
            seekIncrement -> SeekIncrement.getString(context, seekIncrement)
        }
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.skip_silence)) },
        icon = { Icon(painterResource(R.drawable.skip_next), null) },
        checked = skipSilence,
        onCheckedChange = onSkipSilenceChange
    )
}

@Composable
fun PlayerServiceFrag() {

}

@Composable
fun AudioQualityFrag() {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        key = AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        key = AudioNormalizationKey,
        defaultValue = true
    )

    EnumListPreference(
        title = { Text(stringResource(R.string.audio_quality)) },
        icon = { Icon(Icons.Rounded.GraphicEq, null) },
        selectedValue = audioQuality,
        onValueSelected = onAudioQualityChange,
        valueText = {
            when (it) {
                AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
            }
        }
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.audio_normalization)) },
        icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
        checked = audioNormalization,
        onCheckedChange = onAudioNormalizationChange
    )
}

@Composable
fun NowPlayingFrag() {
    val (playerBackground, onPlayerBackgroundChange) = rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = DEFAULT_PLAYER_BACKGROUND
    )
    val availableBackgroundStyles = PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    val (showQueueTitle, onShowQueueTitleChange) = rememberPreference(ShowQueueTitleKey, defaultValue = true)
    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(SliderStyleKey, defaultValue = DEFAULT_SLIDER_STYLE)
    val (swipeToSkip, onSwipeToSkipChange) = rememberPreference(SwipeToSkipKey, defaultValue = DEFAULT_SWIPE_TO_SKIP)
    val (showLyricsOnClick, onShowLyricsOnClickChange) = rememberPreference(
        ShowLyricsOnClickKey,
        defaultValue = DEFAULT_SHOW_LYRICS_ON_CLICK
    )

    EnumListPreference(
        title = { Text(stringResource(R.string.player_background_style)) },
        icon = { Icon(Icons.Rounded.BlurOn, null) },
        selectedValue = playerBackground,
        onValueSelected = onPlayerBackgroundChange,
        valueText = {
            when (it) {
                PlayerBackgroundStyle.FOLLOW_THEME -> stringResource(R.string.player_background_default)
                PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.player_background_gradient)
                PlayerBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
            }
        },
        values = availableBackgroundStyles
    )
    EnumListPreference(
        title = { Text(stringResource(R.string.slider_style_title)) },
        icon = { Icon(Icons.Rounded.GraphicEq, null) },
        selectedValue = sliderStyle,
        onValueSelected = onSliderStyleChange,
        valueText = {
            when (it) {
                SliderStyle.SQUIGGLY -> stringResource(R.string.slider_style_squiggly)
                SliderStyle.DEFAULT -> stringResource(R.string.slider_style_default)
                SliderStyle.SLIM -> stringResource(R.string.slider_style_slim)
            }
        }
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.swipe_to_skip_title)) },
        description = stringResource(R.string.swipe_to_skip_description),
        icon = { Icon(Icons.Rounded.Swipe, null) },
        checked = swipeToSkip,
        onCheckedChange = onSwipeToSkipChange
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.show_queue_title)) },
        icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
        checked = showQueueTitle,
        onCheckedChange = onShowQueueTitleChange
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.tap_artwork_to_show_lyrics_title)) },
        description = stringResource(R.string.tap_artwork_to_show_lyrics_description),
        icon = { Icon(Icons.Rounded.Lyrics, null) },
        checked = showLyricsOnClick,
        onCheckedChange = onShowLyricsOnClickChange
    )
}

@Composable
fun PlaybackBehaviourFrag() {
    val keepAlive by rememberPreference(key = KeepAliveKey, defaultValue = false)
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(key = PersistentQueueKey, defaultValue = true)
    val (minPlaybackDur, onMinPlaybackDurChange) = rememberPreference(minPlaybackDurKey, defaultValue = 30)
    val (skipOnErrorKey, onSkipOnErrorChange) = rememberPreference(key = SkipOnErrorKey, defaultValue = false)
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        key = StopMusicOnTaskClearKey,
        defaultValue = false
    )
    val (ignoreAudioFocus, onIgnoreAudioFocusChange) = rememberPreference(key = IgnoreAudioFocusKey, defaultValue = false)

    var showMinPlaybackDur by remember {
        mutableStateOf(false)
    }

    PreferenceEntry(
        title = { Text(stringResource(R.string.min_playback_duration)) },
        icon = { Icon(Icons.Rounded.Sync, null) },
        onClick = { showMinPlaybackDur = true }
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
        description = stringResource(R.string.auto_skip_next_on_error_desc),
        icon = { Icon(Icons.Rounded.SkipNext, null) },
        checked = skipOnErrorKey,
        onCheckedChange = onSkipOnErrorChange
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
        icon = { Icon(Icons.Rounded.ClearAll, null) },
        isEnabled = !keepAlive,
        checked = stopMusicOnTaskClear,
        onCheckedChange = onStopMusicOnTaskClearChange,
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.ignore_audio_focus)) },
        description = stringResource(R.string.ignore_audio_focus_desc),
        icon = { Icon(Icons.Rounded.Headset, null) },
        checked = ignoreAudioFocus,
        onCheckedChange = onIgnoreAudioFocusChange
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.persistent_queue)) },
        description = stringResource(R.string.persistent_queue_desc_ot),
        icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
        checked = persistentQueue,
        onCheckedChange = onPersistentQueueChange
    )

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showMinPlaybackDur) {
        CounterDialog(
            title = stringResource(R.string.min_playback_duration),
            description = stringResource(R.string.min_playback_duration_description),
            initialValue = minPlaybackDur,
            upperBound = 100,
            lowerBound = 0,
            unitDisplay = "%",
            onDismiss = { showMinPlaybackDur = false },
            onConfirm = {
                showMinPlaybackDur = false
                onMinPlaybackDurChange(it)
            },
            onCancel = {
                showMinPlaybackDur = false
            }
        )
    }
}

@Composable
fun SleepTimerFrag() {
    val (fade, onFadeChange) = rememberPreference(SleepTimerFadeKey, defaultValue = SleepTimerDefaults.FADE_ENABLED)
    val (fadeDuration, onFadeDurationChange) = rememberPreference(
        SleepTimerFadeDurationKey,
        defaultValue = SleepTimerDefaults.FADE_DURATION_SECONDS
    )
    val (defaultMinutes, onDefaultMinutesChange) = rememberPreference(
        SleepTimerDefaultMinutesKey,
        defaultValue = SleepTimerDefaults.DEFAULT_MINUTES
    )
    val (showOnPlayer, onShowOnPlayerChange) = rememberPreference(SleepTimerShowOnPlayerKey, defaultValue = true)

    var showFadeDurationDialog by remember { mutableStateOf(false) }
    var showDefaultTimeDialog by remember { mutableStateOf(false) }

    SwitchPreference(
        title = { Text(stringResource(R.string.sleep_timer_show_on_player)) },
        description = stringResource(R.string.sleep_timer_show_on_player_desc),
        icon = { Icon(Icons.Rounded.Visibility, null) },
        checked = showOnPlayer,
        onCheckedChange = onShowOnPlayerChange
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.sleep_timer_fade)) },
        icon = { Icon(Icons.AutoMirrored.Rounded.VolumeDown, null) },
        checked = fade,
        onCheckedChange = onFadeChange
    )
    PreferenceEntry(
        title = { Text(stringResource(R.string.sleep_timer_fade_duration)) },
        description = stringResource(R.string.sleep_timer_fade_duration_desc),
        icon = { Icon(Icons.Rounded.Timelapse, null) },
        isEnabled = fade,
        onClick = { showFadeDurationDialog = true }
    )
    PreferenceEntry(
        title = { Text(stringResource(R.string.sleep_timer_default_time)) },
        description = pluralStringResource(R.plurals.minute, defaultMinutes, defaultMinutes),
        icon = { Icon(Icons.Rounded.Bedtime, null) },
        onClick = { showDefaultTimeDialog = true }
    )

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */

    if (showFadeDurationDialog) {
        CounterDialog(
            title = stringResource(R.string.sleep_timer_fade_duration),
            description = stringResource(R.string.sleep_timer_fade_duration_desc),
            initialValue = fadeDuration,
            upperBound = SleepTimerDefaults.FADE_DURATION_RANGE.last,
            lowerBound = SleepTimerDefaults.FADE_DURATION_RANGE.first,
            unitDisplay = " " + stringResource(R.string.sleep_timer_second_unit),
            onDismiss = { showFadeDurationDialog = false },
            onConfirm = {
                showFadeDurationDialog = false
                onFadeDurationChange(it)
            },
            onCancel = { showFadeDurationDialog = false }
        )
    }
    if (showDefaultTimeDialog) {
        SleepTimerDefaultTimeDialog(
            initialMinutes = defaultMinutes,
            onDismiss = { showDefaultTimeDialog = false },
            onConfirm = {
                showDefaultTimeDialog = false
                onDefaultMinutesChange(it)
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerGeneralFragPreview() {
    PlayerGeneralFrag()
}

@Preview(showBackground = true)
@Composable
private fun AudioQualityFragPreview() {
    AudioQualityFrag()
}

@Preview(showBackground = true)
@Composable
private fun NowPlayingFragPreview() {
    NowPlayingFrag()
}

@Preview(showBackground = true)
@Composable
private fun PlaybackBehaviourFragPreview() {
    PlaybackBehaviourFrag()
}