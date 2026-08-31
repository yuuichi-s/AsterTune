package io.github.yuuichi_s.astertune.ui.menu

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.LocalDatabase
import io.github.yuuichi_s.astertune.LocalDownloadUtil
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.ShowLyricsKey
import io.github.yuuichi_s.astertune.constants.SleepTimerDefaultMinutesKey
import io.github.yuuichi_s.astertune.constants.SleepTimerDefaults
import io.github.yuuichi_s.astertune.constants.SleepTimerFadeDurationKey
import io.github.yuuichi_s.astertune.constants.SleepTimerFadeKey
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.playback.ExoDownloadService
import io.github.yuuichi_s.astertune.playback.queues.YouTubeQueue
import io.github.yuuichi_s.astertune.ui.component.BigSeekBar
import io.github.yuuichi_s.astertune.ui.component.BottomSheetState
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.ui.dialog.AddToPlaylistDialog
import io.github.yuuichi_s.astertune.ui.dialog.AddToQueueDialog
import io.github.yuuichi_s.astertune.ui.dialog.ArtistDialog
import io.github.yuuichi_s.astertune.ui.dialog.DetailsDialog
import io.github.yuuichi_s.astertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val clipboardManager = LocalClipboard.current

    var showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    val playerConnection = LocalPlayerConnection.current ?: return
    val playerVolume = playerConnection.service.playerVolume.collectAsState()
    val queueBoard by playerConnection.queueBoard.collectAsState()
    val currentFormatState = database.format(mediaMetadata.id).collectAsState(initial = null)
    val currentFormat = currentFormatState.value
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)

    val activityResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }


    var showPitchTempoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showPitchTempoDialog) {
        PitchTempoDialog(
            onDismiss = { showPitchTempoDialog = false }
        )
    }

    val sleepTimerEnabled = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.pauseWhenSongEnd
    ) {
        playerConnection.service.sleepTimer.isActive
    }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                val newSleepTimerTimeLeft = if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                    playerConnection.player.duration - playerConnection.player.currentPosition
                } else {
                    playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                }
                delay(1000L)

                withContext(Dispatchers.Main) {
                    sleepTimerTimeLeft = newSleepTimerTimeLeft
                }
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(onDismiss = { showSleepTimerDialog = false })
    }

    var showDetailsDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showDetailsDialog) {
        DetailsDialog(
            mediaMetadata = mediaMetadata,
            currentFormat = currentFormat,
            currentPlayCount = librarySong?.playCount?.fastSumBy { it.count } ?: 0,
            clipboardManager = clipboardManager,
            setVisibility = { showDetailsDialog = it }
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 6.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )

        BigSeekBar(
            progressProvider = playerVolume::value,
            onProgressChange = { playerConnection.service.playerVolume.value = it },
            modifier = Modifier.weight(1f)
        )
    }

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        if (!mediaMetadata.isLocal)
            GridMenuItem(
                icon = Icons.Rounded.Radio,
                title = R.string.start_radio
            ) {
                playerConnection.playQueue(YouTubeQueue.radio(mediaMetadata), isRadio = true)
                onDismiss()
            }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
        if (!mediaMetadata.isLocal)
            DownloadGridMenu(
                localDateTime = download,
                onDownload = {
                    database.transaction {
                        insert(mediaMetadata)
                    }
                    downloadUtil.download(mediaMetadata)
                },
                onRemoveDownload = {
                    DownloadService.sendRemoveDownload(
                        context,
                        ExoDownloadService::class.java,
                        mediaMetadata.id,
                        false
                    )
                }
            )
        if (librarySong?.song?.inLibrary != null && !librarySong!!.song.isLocal) {
            GridMenuItem(
                icon = Icons.Rounded.LibraryAddCheck,
                title = R.string.remove_from_library,
            ) {
                database.query {
                    toggleInLibrary(mediaMetadata.id, null)
                }
            }
        } else if (!mediaMetadata.isLocal) {
            GridMenuItem(
                icon = Icons.Rounded.LibraryAdd,
                title = R.string.add_to_library,
            ) {
                database.transaction {
                    insert(mediaMetadata)
                    toggleInLibrary(mediaMetadata.id, LocalDateTime.now())
                }
            }
        }
        GridMenuItem(
            icon = R.drawable.artist,
            title = R.string.view_artist
        ) {
            if (mediaMetadata.artists.size == 1) {
                navController.navigate("artist/${mediaMetadata.artists[0].id}")
                playerBottomSheetState.collapseSoft()
                onDismiss()
            } else {
                showSelectArtistDialog = true
            }
        }
        GridMenuItem(
            icon = R.drawable.album,
            title = R.string.view_album,
            enabled = mediaMetadata.album != null && !mediaMetadata.isLocal
        ) {
            mediaMetadata.album?.let { navController.navigate("album/${it.id}") }
            playerBottomSheetState.collapseSoft()
            onDismiss()
        }

        if (!mediaMetadata.isLocal)
            GridMenuItem(
                icon = Icons.Rounded.Share,
                title = R.string.share
            ) {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${mediaMetadata.id}")
                }
                context.startActivity(Intent.createChooser(intent, null))
                onDismiss()
            }
        GridMenuItem(
            icon = Icons.Rounded.Lyrics,
            title = R.string.toggle_lyrics
        ) {
            onDismiss()
            showLyrics = !showLyrics
        }
        GridMenuItem(
            icon = Icons.Rounded.Info,
            title = R.string.details
        ) {
            showDetailsDialog = true
        }
        SleepTimerGridMenu(
            sleepTimerTimeLeft = sleepTimerTimeLeft,
            enabled = sleepTimerEnabled
        ) {
            showSleepTimerDialog = true
        }
        GridMenuItem(
            icon = Icons.Rounded.Equalizer,
            title = R.string.equalizer
        ) {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playerConnection.player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                activityResultLauncher.launch(intent)
            }
            onDismiss()
        }
        GridMenuItem(
            icon = Icons.Rounded.Tune,
            title = R.string.advanced
        ) {
            showPitchTempoDialog = true
        }
    }

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */

    if (showChooseQueueDialog) {
        AddToQueueDialog(
            onAdd = { queueName ->
                val q = queueBoard.addQueue(
                    queueName,
                    listOf(mediaMetadata),
                    forceInsert = true,
                    delta = false
                )
                q?.let {
                    queueBoard.setCurrQueue(it)
                }
            },
            onDismiss = {
                showChooseQueueDialog = false
                onDismiss() // here we dismiss since we switch to the queue anyways
            }
        )
    }

    if (showChoosePlaylistDialog) {
        AddToPlaylistDialog(
            navController = navController,
            songIds = listOf(mediaMetadata.id),
            onPreAdd = { playlist ->
                database.transaction {
                    insert(mediaMetadata)
                }

                playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }

                listOf(mediaMetadata.id)
            },
            onDismiss = {
                showChoosePlaylistDialog = false
            }
        )
    }

    if (showSelectArtistDialog) {
        ArtistDialog(
            navController = navController,
            artists = mediaMetadata.artists,
            onDismiss = {
                playerBottomSheetState.collapseSoft()
                showSelectArtistDialog = false
            }
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val defaultSleepMinutes by rememberPreference(
        SleepTimerDefaultMinutesKey,
        defaultValue = SleepTimerDefaults.DEFAULT_MINUTES
    )
    val sleepTimerFade by rememberPreference(SleepTimerFadeKey, defaultValue = SleepTimerDefaults.FADE_ENABLED)
    val sleepTimerFadeDuration by rememberPreference(
        SleepTimerFadeDurationKey,
        defaultValue = SleepTimerDefaults.FADE_DURATION_SECONDS
    )

    val sleepTimerActive = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.pauseWhenSongEnd
    ) {
        playerConnection.service.sleepTimer.isActive
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(defaultSleepMinutes.toFloat())
    }

    AlertDialog(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Rounded.Timer, contentDescription = null) },
        title = { Text(stringResource(R.string.sleep_timer)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    playerConnection.service.startSleepTimer(
                        sleepTimerValue.roundToInt()
                            .coerceIn(SleepTimerDefaults.MINUTES_RANGE.first, SleepTimerDefaults.MINUTES_RANGE.last),
                        sleepTimerFade,
                        sleepTimerFadeDuration
                    )
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sleepTimerActive) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            playerConnection.service.sleepTimer.clear()
                        }
                    ) {
                        Text(stringResource(R.string.sleep_timer_stop))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SleepTimerDurationPicker(
                    minutes = sleepTimerValue,
                    onMinutesChange = { sleepTimerValue = it },
                    showEndTime = true,
                )

                Box(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            playerConnection.service.startSleepTimer(-1, sleepTimerFade, sleepTimerFadeDuration)
                        },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            }
        }
    )
}

@Composable
fun PitchTempoDialog(
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var tempo by remember {
        mutableFloatStateOf(playerConnection.player.playbackParameters.speed)
    }
    var transposeValue by remember {
        mutableIntStateOf(round(12 * log2(playerConnection.player.playbackParameters.pitch)).toInt())
    }
    val updatePlaybackParameters = {
        playerConnection.player.playbackParameters = PlaybackParameters(tempo, 2f.pow(transposeValue.toFloat() / 12))
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(
                onClick = {
                    tempo = 1f
                    transposeValue = 0
                    updatePlaybackParameters()
                }
            ) {
                Text(stringResource(R.string.reset))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column {
                ValueAdjuster(
                    icon = Icons.Rounded.SlowMotionVideo,
                    currentValue = tempo,
                    values = (0..35).map { round((0.25f + it * 0.05f) * 100) / 100 },
                    onValueUpdate = {
                        tempo = it
                        updatePlaybackParameters()
                    },
                    valueText = { "x$it" },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ValueAdjuster(
                    icon = Icons.Rounded.Tune,
                    currentValue = transposeValue,
                    values = (-12..12).toList(),
                    onValueUpdate = {
                        transposeValue = it
                        updatePlaybackParameters()
                    },
                    valueText = { "${if (it > 0) "+" else ""}$it" }
                )
            }
        }
    )
}

@Composable
fun <T> ValueAdjuster(
    icon: ImageVector,
    currentValue: T,
    values: List<T>,
    onValueUpdate: (T) -> Unit,
    valueText: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )

        IconButton(
            enabled = currentValue != values.first(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) - 1])
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.RemoveCircleOutline,
                contentDescription = null
            )
        }

        Text(
            text = valueText(currentValue),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(80.dp)
        )

        IconButton(
            enabled = currentValue != values.last(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) + 1])
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.AddCircleOutline,
                contentDescription = null
            )
        }
    }
}

@Composable
fun SleepTimerDurationPicker(
    minutes: Float,
    onMinutesChange: (Float) -> Unit,
    showEndTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var showInput by remember { mutableStateOf(false) }

    LaunchedEffect(showInput) {
        if (showInput) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        val minutesInt = minutes.roundToInt()
        val pluralString = pluralStringResource(R.plurals.minute, minutesInt, minutesInt)

        val displayText = if (showEndTime) {
            val endTime = System.currentTimeMillis() + (minutesInt * 60 * 1000).toLong()
            val calendarNow = Calendar.getInstance()
            val calendarEnd = Calendar.getInstance().apply { timeInMillis = endTime }
            // show date if it will span to next day
            val endTimeString =
                if (calendarNow.get(Calendar.DAY_OF_YEAR) == calendarEnd.get(Calendar.DAY_OF_YEAR) &&
                    calendarNow.get(Calendar.YEAR) == calendarEnd.get(Calendar.YEAR)
                ) {
                    SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT, Locale.getDefault())
                        .format(Date(endTime))
                } else {
                    SimpleDateFormat.getDateTimeInstance(
                        SimpleDateFormat.SHORT,
                        SimpleDateFormat.SHORT,
                        Locale.getDefault()
                    ).format(Date(endTime))
                }
            "$pluralString\n$endTimeString"
        } else {
            pluralString
        }

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
                .clip(shape = RoundedCornerShape(8.dp))
                .clickable { showInput = true }
        )

        // manual input
        if (showInput) {
            val initialText = TextFieldValue(
                text = minutesInt.toString(),
                selection = TextRange(0, minutesInt.toString().length),
            )

            val (textFieldValue, onTextFieldValueChange) = remember {
                mutableStateOf(initialText)
            }

            TextField(
                value = textFieldValue,
                onValueChange = {
                    onTextFieldValueChange(it)
                    it.text.toFloatOrNull()?.let { value ->
                        onMinutesChange(
                            value.coerceIn(
                                SleepTimerDefaults.MINUTES_RANGE.first.toFloat(),
                                SleepTimerDefaults.MINUTES_RANGE.last.toFloat()
                            )
                        )
                    }
                },
                placeholder = { Text(pluralString) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.MoreTime, null) },
                colors = OutlinedTextFieldDefaults.colors(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.focusRequester(focusRequester)
            )
        }

        Slider(
            value = minutes,
            onValueChange = onMinutesChange,
            valueRange = SleepTimerDefaults.MINUTES_RANGE.first.toFloat()..SleepTimerDefaults.MINUTES_RANGE.last.toFloat(),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SleepTimerDefaults.PRESET_MINUTES.forEach { preset ->
                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                    OutlinedButton(
                        onClick = { onMinutesChange(preset.toFloat()) },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(stringResource(R.string.sleep_timer_minutes_short, preset))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDefaultTimeDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by remember { mutableFloatStateOf(initialMinutes.toFloat()) }

    AlertDialog(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Rounded.Bedtime, contentDescription = null) },
        title = { Text(stringResource(R.string.sleep_timer_default_time)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    minutes.roundToInt()
                        .coerceIn(SleepTimerDefaults.MINUTES_RANGE.first, SleepTimerDefaults.MINUTES_RANGE.last)
                )
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            SleepTimerDurationPicker(
                minutes = minutes,
                onMinutesChange = { minutes = it },
                showEndTime = false,
            )
        }
    )
}
