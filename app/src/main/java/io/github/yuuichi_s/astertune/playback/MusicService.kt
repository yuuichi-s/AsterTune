/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import dagger.hilt.android.AndroidEntryPoint
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.yuuichi_s.astertune.MainActivity
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.AudioDecoderKey
import io.github.yuuichi_s.astertune.constants.AudioGaplessOffloadKey
import io.github.yuuichi_s.astertune.constants.AudioNormalizationKey
import io.github.yuuichi_s.astertune.constants.AudioOffloadKey
import io.github.yuuichi_s.astertune.constants.AudioQuality
import io.github.yuuichi_s.astertune.constants.AudioQualityKey
import io.github.yuuichi_s.astertune.constants.AutoLoadMoreKey
import io.github.yuuichi_s.astertune.constants.DEFAULT_AUDIO_DECODER
import io.github.yuuichi_s.astertune.constants.ENABLE_FFMETADATAEX
import io.github.yuuichi_s.astertune.constants.EnableLyricsPrefetchKey
import io.github.yuuichi_s.astertune.constants.IgnoreAudioFocusKey
import io.github.yuuichi_s.astertune.constants.KeepAliveKey
import io.github.yuuichi_s.astertune.constants.LocalAudioNormalizationKey
import io.github.yuuichi_s.astertune.constants.LyricsPrefetchCountKey
import io.github.yuuichi_s.astertune.constants.MAX_PLAYER_CONSECUTIVE_ERR
import io.github.yuuichi_s.astertune.constants.MaxQueuesKey
import io.github.yuuichi_s.astertune.constants.MediaSessionConstants.CommandToggleLike
import io.github.yuuichi_s.astertune.constants.MediaSessionConstants.CommandToggleRepeatMode
import io.github.yuuichi_s.astertune.constants.MediaSessionConstants.CommandToggleShuffle
import io.github.yuuichi_s.astertune.constants.MediaSessionConstants.CommandToggleStartRadio
import io.github.yuuichi_s.astertune.constants.PauseListenHistoryKey
import io.github.yuuichi_s.astertune.constants.PauseRemoteListenHistoryKey
import io.github.yuuichi_s.astertune.constants.PersistentQueueKey
import io.github.yuuichi_s.astertune.constants.PlayerDismissedKey
import io.github.yuuichi_s.astertune.constants.PlayerVolumeKey
import io.github.yuuichi_s.astertune.constants.RepeatModeKey
import io.github.yuuichi_s.astertune.constants.SERVICE_DEBUG
import io.github.yuuichi_s.astertune.constants.ShowLyricsKey
import io.github.yuuichi_s.astertune.constants.SkipOnErrorKey
import io.github.yuuichi_s.astertune.constants.SkipSilenceKey
import io.github.yuuichi_s.astertune.constants.StopMusicOnTaskClearKey
import io.github.yuuichi_s.astertune.constants.minPlaybackDurKey
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.Event
import io.github.yuuichi_s.astertune.db.entities.FormatEntity
import io.github.yuuichi_s.astertune.di.AppModule.PlayerCache
import io.github.yuuichi_s.astertune.di.DownloadCache
import io.github.yuuichi_s.astertune.extensions.SilentHandler
import io.github.yuuichi_s.astertune.extensions.collect
import io.github.yuuichi_s.astertune.extensions.collectLatest
import io.github.yuuichi_s.astertune.extensions.currentMetadata
import io.github.yuuichi_s.astertune.extensions.findNextMediaItemById
import io.github.yuuichi_s.astertune.extensions.metadata
import io.github.yuuichi_s.astertune.extensions.setOffloadEnabled
import io.github.yuuichi_s.astertune.lyrics.LyricsFetchRole
import io.github.yuuichi_s.astertune.lyrics.LyricsHelper
import io.github.yuuichi_s.astertune.models.HybridCacheDataSinkFactory
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.playback.audio.LoudnessMeasurement
import io.github.yuuichi_s.astertune.playback.audio.LoudnessTracker
import io.github.yuuichi_s.astertune.playback.queues.ListQueue
import io.github.yuuichi_s.astertune.playback.queues.Queue
import io.github.yuuichi_s.astertune.playback.queues.YouTubeQueue
import io.github.yuuichi_s.astertune.utils.CoilBitmapLoader
import io.github.yuuichi_s.astertune.utils.NetworkConnectivityObserver
import io.github.yuuichi_s.astertune.utils.SyncUtils
import io.github.yuuichi_s.astertune.utils.YTPlayerUtils
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.enumPreference
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.astertune.utils.playerCoroutine
import io.github.yuuichi_s.astertune.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.nift4.alacdecoder.AlacRenderer
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    val TAG = MusicService::class.simpleName.toString()

    @Inject
    lateinit var database: MusicDatabase
    private val relatedSongsSaver by lazy { RelatedSongsSaver(database) }

    // Parent of every service coroutine scope. Cancelled once in onDestroy() so no scope outlives the
    // service. Each scope owns a SupervisorJob child of this so a failure in one coroutine neither
    // cancels its siblings nor the whole service.
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(SupervisorJob(serviceJob) + Dispatchers.Main)
    private val offloadScope = CoroutineScope(SupervisorJob(serviceJob) + playerCoroutine)

    // Scope for the per-request playQueue() coroutine. Uses the Main dispatcher because it mutates the
    // player, which must run on the application thread; the SupervisorJob binds it to the service lifecycle.
    private val queueLoadScope = CoroutineScope(SupervisorJob(serviceJob) + Dispatchers.Main)

    // Durations the player reported for recently played songs, keyed by media ID, for counting plays of
    // songs whose metadata has no duration. Accessed only on the application thread.
    private val playerDurationsMs = object : LinkedHashMap<String, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 8
    }

    // Critical player components
    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private val binder = MusicBinder()
    private lateinit var connectivityManager: ConnectivityManager

    val qbInit = MutableStateFlow(false)
    var queueBoard = MutableStateFlow(QueueBoard(this, maxQueues = 1))
    var queuePlaylistId: String? = null

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @PlayerCache
    @JvmField
    var playerCacheSizeMb: Int = 0

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    // Player components
    @Inject
    lateinit var syncUtils: SyncUtils

    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(true)

    lateinit var sleepTimer: SleepTimer

    // Player vars
    val currentMediaMetadata = MutableStateFlow<MediaMetadata?>(null)

    private val currentSong = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.song(mediaMetadata?.id)
    }.stateIn(offloadScope, SharingStarted.Lazily, null)

    private val currentFormat = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }

    private val normalizeFactor = MutableStateFlow(1f)

    // Both callbacks are posted to the main thread in the order the tracker calls them.
    private val loudnessTracker = LoudnessTracker(object : LoudnessTracker.Listener {
        override fun onSectionStarted(windowSequenceNumber: Long) {
            scope.launch { measuringWindowSequenceNumber.value = windowSequenceNumber }
        }

        override fun onMeasured(measurement: LoudnessMeasurement) {
            scope.launch { onLoudnessMeasured(measurement) }
        }
    })
    private val measuringWindowSequenceNumber = MutableStateFlow(C.INDEX_UNSET.toLong())

    private val localLoudness = LocalLoudnessStore()

    // Read loudness once per playback so later measurements do not change the volume midway.
    private data class LocalPlayback(
        val songId: String,
        // Makes each playback distinct so StateFlow emits even when the song is unchanged.
        val sequence: Int,
        // Set when the same song plays again, whose previous measurement may still be on its way.
        val awaitedWindowSequenceNumber: Long?,
    )

    private var localPlaybackSequence = 0
    private var lastPlaybackState = Player.STATE_IDLE
    private val localPlayback = MutableStateFlow<LocalPlayback?>(null)
    private val localPlaybackLoudness = localPlayback.flatMapLatest { playback ->
        if (playback == null) {
            flowOf<Pair<String, Double?>?>(null)
        } else {
            flow {
                playback.awaitedWindowSequenceNumber?.let { windowSequenceNumber ->
                    // Bound the wait because offload playback may never start a PCM section.
                    withTimeoutOrNull(LOCAL_LOUDNESS_SECTION_WAIT_TIMEOUT_MS) {
                        measuringWindowSequenceNumber.first { it >= windowSequenceNumber }
                    }
                }
                val songId = playback.songId
                val loudnessDb = if (songId in localLoudness) {
                    localLoudness[songId]
                } else {
                    database.format(songId).first()?.loudnessDb.also { localLoudness.remember(songId, it) }
                }
                emit(playback.songId to loudnessDb)
            }
        }
    }

    private val audioDecoder = dataStore.get(AudioDecoderKey, DEFAULT_AUDIO_DECODER)
    private val isGaplessOffloadAllowed = dataStore.get(AudioGaplessOffloadKey, false)
    val playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

    private var isAudioEffectSessionOpened = false

    var consecutivePlaybackErr = 0

    // Current song plus upcoming songs to fetch lyrics for, read synchronously from the player on each
    // track transition. A data class so the StateFlow deduplicates identical updates.
    private data class LyricsFetchTargets(
        val current: MediaMetadata?,
        val upcoming: List<MediaMetadata>,
    )

    private val lyricsFetchTargets = MutableStateFlow(LyricsFetchTargets(null, emptyList()))
    // Shared by data source callbacks and playback error recovery.
    private val songUrlCache = StreamUrlCache()

    /** Refresh attempts per media id, so a song that cannot play at all stops retrying. */
    private val streamRefreshes = HashMap<String, Int>()

    override fun onCreate() {
        if (SERVICE_DEBUG) Log.i(TAG, "Starting MusicService")
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .setRenderersFactory(createRenderersFactory(isGaplessOffloadAllowed))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), !dataStore.get(IgnoreAudioFocusKey, false)
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                // listeners
                addListener(this@MusicService)
                sleepTimer = SleepTimer(scope, this)
                sleepTimer.onFinish = { this@MusicService.pauseAllPlayersAndStopSelf() }
                addListener(sleepTimer)
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
                addAnalyticsListener(object : AnalyticsListener {
                    override fun onMediaItemTransition(
                        eventTime: AnalyticsListener.EventTime,
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        updateLocalPlayback(mediaItem, reason, eventTime.currentMediaPeriodId?.windowSequenceNumber)
                    }
                })

                // misc
                setOffloadEnabled(dataStore.get(AudioOffloadKey, false))
            }

        mediaLibrarySessionCallback.apply {
            service = this@MusicService
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }

        mediaSession = MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            // TODO: do i even want to have smaller art for media notification
            .setBitmapLoader(CoilBitmapLoader(this))
            .build()

        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!

        currentSong.collect(scope) {
            updateNotification()
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this@MusicService,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.small_icon)
                }
        )

        queueDismissed = dataStore.get(PlayerDismissedKey, false)

        // lateinit tasks
        offloadScope.launch {
            if (SERVICE_DEBUG) Log.i(TAG, "Launching MusicService offloadScope tasks")
            if (!qbInit.value) {
                initQueue()
            }

            combine(playerVolume, normalizeFactor, sleepTimer.fadeFactor) { playerVolume, normalizeFactor, fadeFactor ->
                playerVolume * normalizeFactor * fadeFactor
            }.collectLatest(scope) {
                withContext(Dispatchers.Main) {
                    player.volume = it
                }
            }

            playerVolume.debounce(1000).collect(scope) { volume ->
                dataStore.edit { settings ->
                    settings[PlayerVolumeKey] = volume
                }
            }

            dataStore.data
                .map { it[SkipSilenceKey] ?: false }
                .distinctUntilChanged()
                .collectLatest(scope) {
                    withContext(Dispatchers.Main) {
                        player.skipSilenceEnabled = it
                    }
                }

            dataStore.data
                .map { it[IgnoreAudioFocusKey] ?: false }
                .distinctUntilChanged()
                .collectLatest(scope) { ignoreAudioFocus ->
                    withContext(Dispatchers.Main) {
                        player.setAudioAttributes(player.audioAttributes, !ignoreAudioFocus)
                    }
                }

            combine(
                currentMediaMetadata,
                currentFormat,
                localPlaybackLoudness,
                dataStore.data
                    .map { (it[AudioNormalizationKey] ?: true) to (it[LocalAudioNormalizationKey] ?: false) }
                    .distinctUntilChanged()
            ) { mediaMetadata, format, localLoudness, (normalizeAudio, normalizeLocalAudio) ->
                val loudnessDb = if (mediaMetadata?.isLocal == true) {
                    if (localLoudness == null || localLoudness.first != mediaMetadata.id) return@combine null
                    localLoudness.second.takeIf { normalizeLocalAudio }
                } else {
                    format?.loudnessDb.takeIf { normalizeAudio }
                }
                if (loudnessDb != null) min(10f.pow(-loudnessDb.toFloat() / 20), 1f) else 1f
            }.filterNotNull().collectLatest(scope) {
                normalizeFactor.value = it
            }

            // Fetch current-song lyrics before prefetching, keeping both in the same collector.
            combine(
                dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
                lyricsFetchTargets
            ) { showLyrics, targets -> showLyrics to targets }
                .collectLatest(offloadScope) { (showLyrics, targets) ->
                    val current = targets.current
                    if (showLyrics && current != null && lyricsHelper.shouldFetch(current.id)) {
                        lyricsHelper.fetchAndStoreRemote(current, LyricsFetchRole.CURRENT)
                    }
                    // Read prefetch settings and connectivity without observing their changes,
                    // so they do not restart or cancel this collector.
                    if (dataStore.get(EnableLyricsPrefetchKey, true) && isNetworkConnected.value) {
                        val count = dataStore.get(LyricsPrefetchCountKey, 3)
                        targets.upcoming.take(count).forEach { mediaMetadata ->
                            if (!currentCoroutineContext().isActive) return@forEach
                            if (lyricsHelper.shouldFetch(mediaMetadata.id)) {
                                lyricsHelper.fetchAndStoreRemote(mediaMetadata, LyricsFetchRole.PREFETCH)
                            }
                        }
                    }
                }


            // network connectivity
            try {
                connectivityObserver.unregister()
            } catch (e: UninitializedPropertyAccessException) {
                // lol
            }
            connectivityObserver = NetworkConnectivityObserver(this@MusicService)

            offloadScope.launch {
                connectivityObserver.networkStatus.collect { isConnected ->
                    isNetworkConnected.value = isConnected

                    if (isConnected && waitingForNetworkConnection.value) {
                        waitingForNetworkConnection.value = false
                        withContext(Dispatchers.Main) {
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }
        }
    }


// Library functions

    private suspend fun recoverSong(mediaId: String, playbackData: YTPlayerUtils.PlaybackData? = null) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        relatedSongsSaver.saveIfMissing(mediaId)
    }

    fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        database.query {
            currentSong.value?.let {
                val song = it.song.toggleLike()
                update(song)

                if (!song.isLocal) {
                    syncUtils.likeSong(song)
                }
            }
        }
    }

    fun toggleStartRadio() {
        val mediaMetadata = player.currentMetadata ?: return
        playQueue(YouTubeQueue.radio(mediaMetadata), isRadio = true)
    }


// Queue

    /**
     * Resolves a queue and loads it into the player.
     *
     * @param queue Queue to load
     * @param playWhenReady Whether to start playback when ready
     * @param shouldResume Whether to resume at the saved position when reloading; false uses
     *     the default starting position. Preloading or retaining the currently playing song
     *     can preserve its position regardless of this flag.
     * @param replace Whether to replace an existing queue's contents; resolving a preload queue
     *     replaces its contents regardless of this flag
     * @param isRadio Whether to configure radio continuation
     * @param title Queue title override; otherwise uses the initial response title or the
     *     localized default queue title
     * @param appendLater Optional loader invoked on [Dispatchers.IO] after the initial queue loads.
     *     Songs with IDs from the initial queue or already present at append time are skipped.
     *     A newer [playQueue] call or dismissal invalidates the result. The target queue must
     *     still be current when the result is applied. If the loader throws, no songs are appended.
    */
    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
        shouldResume: Boolean = false,
        replace: Boolean = false,
        isRadio: Boolean = false,
        title: String? = null,
        appendLater: (suspend () -> List<MediaMetadata>)? = null
    ) {
        val requestId = playQueueRequestId.incrementAndGet()
        if (!qbInit.value) {
            // Runs on the calling thread: when that is the main thread, initQueue() replaces the
            // board in place instead of dispatching to the blocked main looper.
            runBlocking {
                initQueue()
            }
        }

        var queueTitle = title
        queuePlaylistId = queue.playlistId
        var q: MultiQueueObject? = null
        val preloadItem = queue.preloadItem
        val dismissCountAtRequest = dismissCount
        queueLoadJob = queueLoadScope.launch {
            // A stale request must leave the newer request's loader running.
            if (playQueueRequestId.get() == requestId) {
                appendLaterJob?.cancel()
                appendLaterJob = null
            }
            if (SERVICE_DEBUG) Log.d(TAG, "playQueue: Resolving additional queue data...")
            fun dismissedSinceRequest() = dismissCount != dismissCountAtRequest
            try {
                if (preloadItem != null) {
                    if (dismissedSinceRequest()) {
                        return@launch
                    }
                    q = queueBoard.value.addQueue(
                        queueTitle ?: "Radio\u2060temp",
                        listOf(preloadItem),
                        shuffled = queue.startShuffled,
                        replace = replace,
                        continuationEndpoint = null // fulfilled later on after initial status
                    )
                    queueBoard.value.setCurrQueue(q, true)
                }

                val initialStatus = withContext(Dispatchers.IO) { queue.getInitialStatus() }
                // Rebind after a possible board replacement
                q = q?.let { added -> queueBoard.value.masterQueues.firstOrNull { it.id == added.id } }
                // do not find a title if an override is provided
                if ((title == null) && initialStatus.title != null) {
                    queueTitle = initialStatus.title

                    if (preloadItem != null && q != null) {
                        queueBoard.value.renameQueue(q!!, queueTitle)
                    }
                }

                if (dismissedSinceRequest() && q == null) return@launch

                val items = ArrayList<MediaMetadata>()
                if (SERVICE_DEBUG) Log.d(TAG, "playQueue: Queue initial status item count: ${initialStatus.items.size}")
                if (!initialStatus.items.isEmpty()) {
                    if (preloadItem != null) {
                        items.add(preloadItem)
                        items.addAll(initialStatus.items.subList(1, initialStatus.items.size))
                    } else {
                        items.addAll(initialStatus.items)
                    }
                    val q = queueBoard.value.addQueue(
                        queueTitle ?: getString(R.string.queue),
                        items,
                        shuffled = queue.startShuffled,
                        startIndex = if (initialStatus.mediaItemIndex > 0) initialStatus.mediaItemIndex else 0,
                        replace = replace || preloadItem != null,
                        continuationEndpoint = if (isRadio) items.takeLast(4).shuffled().first().id else null // yq?.getContinuationEndpoint()
                    )
                    // After dismissal, reload only if this queue was selected again
                    if (!dismissedSinceRequest() ||
                        (!queueDismissed && queueBoard.value.getCurrentQueue()?.id == q?.id)
                    ) {
                        queueBoard.value.setCurrQueue(q, shouldResume)
                    }
                    if (appendLater != null &&
                        q != null &&
                        !dismissedSinceRequest() &&
                        playQueueRequestId.get() == requestId
                    ) {
                        appendLaterJob = launchAppendLater(
                            requestId,
                            dismissCountAtRequest,
                            q.id,
                            q.queue.mapTo(HashSet()) { it.id },
                            appendLater
                        )
                    }
                }

                if (!dismissedSinceRequest()) {
                    player.prepare()
                    player.playWhenReady = playWhenReady
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                Toast.makeText(this@MusicService, "plr: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }

            if (SERVICE_DEBUG) Log.d(TAG, "playQueue: Queue additional data resolution complete")
        }
    }

    /**
     * Fetches and appends songs as described in [playQueue].
     *
     * Checks whether the result still applies and appends it on the main thread without
     * suspending between those steps. [initialIds] prevents songs from the initial queue
     * from being added back if removed during the fetch.
     */
    private fun launchAppendLater(
        requestId: Int,
        dismissCountAtRequest: Int,
        queueId: Long,
        initialIds: Set<String>,
        fetch: suspend () -> List<MediaMetadata>,
    ): Job = queueLoadScope.launch {
        val songs = try {
            withContext(Dispatchers.IO) { fetch() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            return@launch
        }
        val current = queueBoard.value.getCurrentQueue()

        if (playQueueRequestId.get() != requestId) return@launch
        if (dismissCount != dismissCountAtRequest || queueDismissed) return@launch
        if (current == null || current.id != queueId) return@launch

        val knownIds = HashSet(initialIds)
        current.queue.forEach { knownIds.add(it.id) }
        val newSongs = songs.filter { knownIds.add(it.id) }
        if (newSongs.isNotEmpty()) {
            val endedWithPlayRequested =
                player.playbackState == Player.STATE_ENDED && player.playWhenReady
            val countBefore = player.mediaItemCount
            queueBoard.value.addSongsToQueue(current, Int.MAX_VALUE, newSongs)
            // Songs added after the end do not restart playback by themselves.
            if (endedWithPlayRequested && player.mediaItemCount > countBefore &&
                player.playbackState == Player.STATE_ENDED
            ) {
                player.seekTo(countBefore, 0L)
            }
        }
    }

    /**
     * Adds items after the current song, or requests a new queue if none is open.
     *
     * @return a deferred result indicating whether playback in a new queue was requested
     */
    fun enqueueNext(items: List<MediaItem>) = enqueue(items) { queue, songs ->
        queueBoard.value.addSongsToQueue(queue, player.currentMediaItemIndex + 1, songs)
    }

    /**
     * Adds items to the end of the queue, or requests a new queue if none is open.
     *
     * @return a deferred result indicating whether playback in a new queue was requested
     */
    fun enqueueEnd(items: List<MediaItem>) = enqueue(items) { queue, songs ->
        queueBoard.value.addSongsToQueue(queue, Int.MAX_VALUE, songs)
    }

    /**
     * Adds [items] with [add] to the current queue, or requests a new queue if none is open.
     *
     * @return a deferred result indicating whether playback in a new queue was requested
     */
    private fun enqueue(
        items: List<MediaItem>,
        add: (MultiQueueObject, List<MediaMetadata>) -> Unit,
    ): Deferred<Boolean> = scope.async {
        if (!qbInit.value) {
            queueLoadJob?.join()
        }
        // Use the queue loaded while waiting, but not a dismissed queue retained in memory.
        val queue = if (qbInit.value) queueBoard.value.getCurrentQueue() else null
        val songs = items.mapNotNull { it.metadata }
        if (queue != null) {
            add(queue, songs)
            false
        } else if (items.isNotEmpty()) {
            playQueue(
                ListQueue(
                    title = items.first().mediaMetadata.title.toString(),
                    items = songs
                )
            )
            true
        } else {
            false
        }
    }.also { result ->
        // Callers that do not await the result would not see a failure otherwise.
        result.invokeOnCompletion { cause ->
            if (cause != null && cause !is CancellationException) reportException(cause)
        }
    }

    fun triggerShuffle() {
        val oldIndex = player.currentMediaItemIndex
        queueBoard.value.setCurrQueuePosIndex(oldIndex)
        val currentQueue = queueBoard.value.getCurrentQueue() ?: return

        // shuffle and update player playlist
        if (!currentQueue.shuffled) {
            queueBoard.value.shuffleCurrent()
        } else {
            queueBoard.value.unShuffleCurrent()
        }
        queueBoard.value.setCurrQueue()

        updateNotification()
    }

    // Main thread only. Advances when initQueue() starts or deInitQueue() runs, so that an
    // earlier initQueue() does not apply its result.
    private var queueInitGeneration = 0

    // Main thread only.
    private var queueDismissed = false

    // Latest playQueue job
    @Volatile
    private var queueLoadJob: Job? = null
    private val playQueueRequestId = java.util.concurrent.atomic.AtomicInteger()

    // Main thread only.
    private var appendLaterJob: Job? = null

    // deInitQueue() call count.
    // Written on the main thread. Read by playQueue() from any thread.
    @Volatile
    private var dismissCount = 0

    /**
     * Reloads the queue list up to the configured limit without loading it into the player.
     * Uses stored queues when persistence is enabled, otherwise in-memory queues.
     * A dismissed player stays hidden unless its playlist matches an open queue.
     */
    suspend fun initQueue() {
        if (SERVICE_DEBUG) Log.i(TAG, "+initQueue()")
        val generation = withContext(Dispatchers.Main.immediate) { ++queueInitGeneration }
        val maxQueues = dataStore.get(MaxQueuesKey, 19)
        if (!dataStore.get(PersistentQueueKey, true)) {
            withContext(Dispatchers.Main.immediate) {
                if (generation == queueInitGeneration) carryOverQueues(maxQueues)
            }
            if (SERVICE_DEBUG) Log.i(TAG, "-initQueue()")
            return
        }

        repeat(3) { attempt ->
            val writeCount = withContext(Dispatchers.Main.immediate) {
                if (generation != queueInitGeneration) return@withContext null
                if (qbInit.value) {
                    // The position of a paused song is kept only in memory until it is saved here.
                    recordCurrentSongPosition()
                    queueBoard.value.saveCurrentQueue()
                }
                QueueBoard.writeCount
            } ?: return

            // Read the queues only after pending edits have reached the database.
            queueBoard.value.flushDatabaseWrites()
            val storedQueues = database.readQueue(maxQueues).toMutableList()

            // Checking and replacing run together on the main thread, where the queues are edited.
            val done = withContext(Dispatchers.Main.immediate) {
                when {
                    generation != queueInitGeneration -> true
                    // Edits made after persistence was turned off are not in the database.
                    !dataStore.get(PersistentQueueKey, true) -> {
                        carryOverQueues(maxQueues)
                        true
                    }
                    // Queues edited during the read are read again.
                    QueueBoard.writeCount != writeCount -> false
                    else -> {
                        // Keep open queues after stored queues outside the limit.
                        replaceQueueBoard(
                            storedQueues,
                            maxQueues,
                            storedQueuesLoaded = true,
                            minFirstQueueIndex = storedQueues.firstOrNull()?.index ?: 0,
                        )
                        true
                    }
                }
            }
            if (done) {
                if (SERVICE_DEBUG) Log.i(TAG, "-initQueue() attempts = ${attempt + 1}")
                return
            }
        }
        // Queues kept changing during every read; keep the queues in memory.
        if (SERVICE_DEBUG) Log.w(TAG, "-initQueue(): queues changed during every read, keeping current queues")
    }

    // Main thread only.
    private fun carryOverQueues(maxQueues: Int) = replaceQueueBoard(
        ArrayList(queueBoard.value.masterQueues),
        maxQueues,
        queueBoard.value.storedQueuesLoaded,
        queueBoard.value.firstQueueIndex,
    )

    // Main thread only.
    private fun replaceQueueBoard(
        queues: MutableList<MultiQueueObject>,
        maxQueues: Int,
        storedQueuesLoaded: Boolean,
        minFirstQueueIndex: Int = 0,
    ) {
        queueBoard.value = QueueBoard(
            this, queueBoard.value.masterQueues, queues, maxQueues, minFirstQueueIndex, storedQueuesLoaded
        )
        val playerHoldsOpenQueue = queueDismissed && queueBoard.value.selectQueueInPlayer()
        recordCurrentSongPosition()
        if (SERVICE_DEBUG) Log.d(TAG, "Queue with $maxQueues queue limit. Queues loaded = ${queueBoard.value.masterQueues.size}")
        if (queueDismissed) {
            if (!playerHoldsOpenQueue) return
            setQueueDismissed(false)
        }
        qbInit.value = true
    }

    /**
     * Clears the player's dismissed state.
     * Main thread only.
     */
    fun onQueueLoaded() {
        if (!queueDismissed) return
        setQueueDismissed(false)
        qbInit.value = true
    }

    /**
     * Marks queues uninitialized and saves the player's dismissed state.
     * The caller clears the player.
     * Main thread only.
     */
    fun dismissQueue() {
        deInitQueue()
        setQueueDismissed(true)
    }

    // Main thread only.
    private fun setQueueDismissed(dismissed: Boolean) {
        queueDismissed = dismissed
        runBlocking(Dispatchers.IO) {
            dataStore.edit { it[PlayerDismissedKey] = dismissed }
        }
    }

    /**
     * Saves open queue metadata when initialized and persistence is enabled,
     * then marks the queues uninitialized.
     * Retains in-memory queues and the player's playlist. Must be called on the main thread.
     *
     * Use [dismissQueue] to persist a user dismissal.
     */
    fun deInitQueue() {
        if (SERVICE_DEBUG) Log.i(TAG, "+deInitQueue()")
        queueInitGeneration++
        dismissCount++
        queueLoadJob = null
        appendLaterJob?.cancel()
        appendLaterJob = null
        recordCurrentSongPosition()
        // An uninitialized board may not contain the stored queues, so do not save it.
        if (qbInit.value && dataStore.get(PersistentQueueKey, true)) {
            queueBoard.value.saveAllQueues()
            runBlocking {
                queueBoard.value.flushDatabaseWrites()
            }
        }
        qbInit.value = false
        queueDismissed = true
        if (SERVICE_DEBUG) Log.i(TAG, "-deInitQueue()")
    }

    /**
     * Stores the player position as the resume position of the current queue, if the player
     * holds that queue's selected song.
     */
    private fun recordCurrentSongPosition() {
        val q = queueBoard.value.getCurrentQueue() ?: return
        val songId = player.currentMediaItem?.mediaId ?: return
        if (q.queue.getOrNull(q.queuePos)?.id == songId) {
            q.lastSongPos = player.currentPosition
        }
    }


// Audio playback

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    private fun createCacheDataSource(): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient.Builder()
                                    .proxy(YouTube.proxy)
                                    .build()
                            )
                        )
                    )
                    .setCacheWriteDataSinkFactory(
                        if (playerCacheSizeMb == 0) {
                            null
                        } else {
                            HybridCacheDataSinkFactory(playerCache) { dataSpec ->
                                val isLocal = queueBoard.value.getCurrentQueue()?.findSong(dataSpec.key ?: "")?.isLocal == true
                                if (SERVICE_DEBUG) Log.d(TAG, "SONG CACHE: ${!isLocal}")
                                !isLocal
                            }
                        }
                    )
                    .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            if (SERVICE_DEBUG) Log.d(TAG, "PLAYING: song id = $mediaId")

            var song = queueBoard.value.getCurrentQueue()?.findSong(dataSpec.key ?: "")
            if (song == null) { // in the case of resumption, queueBoard may not be ready yet
                song = runBlocking { database.song(dataSpec.key).first()?.toMediaMetadata() }
            }
            // local song
            if (song?.localPath != null) {
                if (song.isLocal) {
                    if (SERVICE_DEBUG) Log.d(TAG, "PLAYING: local song")
                    val file = File(song.localPath)
                    if (!file.exists()) {
                        throw PlaybackException(
                            "File not found",
                            Throwable(),
                            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                        )
                    }

                    return@Factory dataSpec.withUri(file.toUri())
                } else {
                    val isDownloadNew = downloadUtil.localMgr.getFilePathIfExists(mediaId)
                    isDownloadNew?.let {
                        if (SERVICE_DEBUG) Log.d(TAG, "PLAYING: Custom downloaded song")
                        return@Factory dataSpec.withUri(it)
                    }
                }
            }

            val isDownload =
                downloadCache.isCached(mediaId, dataSpec.position, if (dataSpec.length >= 0) dataSpec.length else 1)
            val isCache = playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
            if (isDownload || isCache) {
                if (SERVICE_DEBUG) Log.d(TAG, "PLAYING: remote song (cache = ${isCache}, download = ${isDownload})")
                offloadScope.launch { recoverSong(mediaId) }
                return@Factory dataSpec
            }

            songUrlCache[mediaId]?.let { cachedStream ->
                if (SERVICE_DEBUG) Log.d(TAG, "PLAYING: remote song (temp cache)")
                offloadScope.launch { recoverSong(mediaId) }
                // Bounded like the fresh-resolve path below; letting this read run open-ended was
                // measurably worse (playback died at 31 s rather than 62 s). The headers matter as
                // much as the url: googlevideo expects the fetch to look like the client it issued
                // the url for, and every read past the first chunk comes through here.
                return@Factory dataSpec.withResolvedStream(cachedStream)
                    .subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
            }

            if (SERVICE_DEBUG) Log.d(TAG, "PLAYING: remote song (online fetch)")

            val playbackData = runBlocking(Dispatchers.IO) {
                val audioQuality by enumPreference(this@MusicService, AudioQualityKey, AudioQuality.AUTO)
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable

                    is ConnectException, is UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }
            val format = playbackData.format

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength!!,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        playbackTrackingUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    )
                )
            }
            offloadScope.launch { recoverSong(mediaId, playbackData) }

            val stream = songUrlCache.put(
                mediaId = mediaId,
                url = playbackData.streamUrl,
                requestHeaders = playbackData.streamHeaders,
                clientName = playbackData.streamClient,
                expiresInSeconds = playbackData.streamExpiresInSeconds,
            )
            dataSpec.withResolvedStream(stream)
                .subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
        }
    }

    private fun createRenderersFactory(gaplessOffloadAllowed: Boolean): DefaultRenderersFactory {
        if (ENABLE_FFMETADATAEX) {
            return object : NextRenderersFactory(this@MusicService) {
                override fun buildAudioSink(
                    context: Context,
                    pcmEncodingRestrictionLifted: Boolean,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink? {
                    return DefaultAudioSink.Builder(this@MusicService)
                        .setPcmEncodingRestrictionLifted(pcmEncodingRestrictionLifted)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessorChain(
                            DefaultAudioSink.DefaultAudioProcessorChain(
                                arrayOf(loudnessTracker.audioProcessor),
                                SilenceSkippingAudioProcessor(),
                                SonicAudioProcessor()
                            )
                        )
                        .setAudioOffloadSupportProvider(
                            MyAudioOffloadSupportProvider(
                                DefaultAudioOffloadSupportProvider(context),
                                !gaplessOffloadAllowed
                            )
                        )
                        .build()
                        .let(loudnessTracker::wrapSink)
                }

                override fun buildAudioRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    audioSink: AudioSink,
                    eventHandler: Handler,
                    eventListener: AudioRendererEventListener,
                    out: ArrayList<Renderer>
                ) {
                    val start = out.size
                    super.buildAudioRenderers(
                        context,
                        extensionRendererMode,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        audioSink,
                        eventHandler,
                        eventListener,
                        out
                    )
                    for (i in start until out.size) out[i] = loudnessTracker.wrapRenderer(out[i])
                }
            }
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(audioDecoder)
        } else {
            return object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: Context,
                    pcmEncodingRestrictionLifted: Boolean,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink? {
                    return DefaultAudioSink.Builder(this@MusicService)
                        .setPcmEncodingRestrictionLifted(pcmEncodingRestrictionLifted)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessorChain(
                            DefaultAudioSink.DefaultAudioProcessorChain(
                                arrayOf(loudnessTracker.audioProcessor),
                                SilenceSkippingAudioProcessor(),
                                SonicAudioProcessor()
                            )
                        )
                        .setAudioOffloadSupportProvider(
                            MyAudioOffloadSupportProvider(
                                DefaultAudioOffloadSupportProvider(context),
                                !gaplessOffloadAllowed
                            )
                        )
                        .build()
                        .let(loudnessTracker::wrapSink)
                }

                override fun buildAudioRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    audioSink: AudioSink,
                    eventHandler: Handler,
                    eventListener: AudioRendererEventListener,
                    out: ArrayList<Renderer>
                ) {
                    val start = out.size
                    out.add(AlacRenderer(eventHandler, eventListener, audioSink))
                    super.buildAudioRenderers(
                        context,
                        extensionRendererMode,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        audioSink,
                        eventHandler,
                        eventListener,
                        out
                    )
                    for (i in start until out.size) out[i] = loudnessTracker.wrapRenderer(out[i])
                }
            }
        }
    }


// Misc

    fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton.Builder(ICON_UNDEFINED)
                    .setDisplayName(getString(if (queueBoard.value.getCurrentQueue()?.shuffled == true) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setSessionCommand(CommandToggleShuffle)
                    .setCustomIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle_off)
                    .build(),
                CommandButton.Builder(ICON_UNDEFINED)
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            }
                        )
                    )
                    .setCustomIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat_off
                            REPEAT_MODE_ONE -> R.drawable.repeat_one
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        }
                    )
                    .setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton.Builder(if (currentSong.value?.song?.liked == true) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                    .setDisplayName(getString(if (currentSong.value?.song?.liked == true) R.string.action_remove_like else R.string.action_like))
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_RADIO)
                    .setDisplayName(getString(R.string.start_radio))
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build()
            )
        )
    }

    fun waitOnNetworkError() {
        waitingForNetworkConnection.value = true
        Toast.makeText(this@MusicService, getString(R.string.wait_to_reconnect), Toast.LENGTH_LONG).show()
    }

    fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_PLAYER_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()

            Toast.makeText(this@MusicService, getString(R.string.err_play_next_on_error), Toast.LENGTH_SHORT).show()
            return
        }

        player.pause()
        Toast.makeText(this@MusicService, getString(R.string.err_stop_on_too_many_errors), Toast.LENGTH_LONG).show()
        consecutivePlaybackErr = 0
    }

    /**
     * Drops the refused stream URL for [mediaId] and re-prepares so it resolves again. If the most
     * recent URL came from WEB_REMIX, that client is temporarily excluded from the candidates.
     *
     * @return true if a retry was started, false once the song has used up its attempts
     */
    private fun refreshStreamAndRetry(mediaId: String): Boolean {
        val attempts = streamRefreshes.getOrDefault(mediaId, 0)
        if (attempts >= MAX_STREAM_REFRESHES) {
            Log.w(TAG, "stream for $mediaId still refused after $attempts refreshes, giving up")
            return false
        }
        streamRefreshes[mediaId] = attempts + 1

        val failedStream = songUrlCache.invalidate(mediaId)
        if (failedStream?.clientName == "WEB_REMIX") {
            YTPlayerUtils.markWebRemixFailed(mediaId)
        }
        Log.w(TAG, "stream for $mediaId refused on ${failedStream?.clientName}, resolving again")

        player.prepare()
        return true
    }

    fun stopOnError() {
        player.pause()
        Toast.makeText(this@MusicService, getString(R.string.err_stop_on_error), Toast.LENGTH_LONG).show()
    }

    /**
     * Read the current song and the upcoming queue entries from the player and publish them for the
     * lyrics fetch coroutine. Called on each track transition; the player is read synchronously here
     * because onMediaItemTransition runs before currentMediaMetadata is updated in onEvents.
     */
    private fun updateLyricsFetchTargets() {
        val mediaItemCount = player.mediaItemCount
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in 0 until mediaItemCount) {
            lyricsFetchTargets.value = LyricsFetchTargets(null, emptyList())
            return
        }
        val current = player.getMediaItemAt(currentIndex).metadata
        val upcoming = ((currentIndex + 1) until mediaItemCount)
            .mapNotNull { player.getMediaItemAt(it).metadata }
        lyricsFetchTargets.value = LyricsFetchTargets(current, upcoming)
    }


// Player overrides

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        // wait for reconnection
        val isConnectionError = (error.cause?.cause is PlaybackException)
                && (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        if (!isNetworkConnected.value || isConnectionError) {
            waitOnNetworkError()
            return
        }

        // A stream URL may still be rejected partway through a track after validation succeeds.
        val responseCode = (error.cause as? InvalidResponseCodeException)?.responseCode
        val mediaId = player.currentMediaItem?.mediaId
        if ((responseCode == 403 || responseCode == 410) && mediaId != null &&
            refreshStreamAndRetry(mediaId)
        ) {
            return
        }

        if (dataStore.get(SkipOnErrorKey, false)) {
            skipOnError()
        } else {
            stopOnError()
        }

        Toast.makeText(
            this@MusicService,
            "plr: ${error.message} (${error.errorCode}): ${error.cause?.message ?: ""} ",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) {
            recordCurrentSongPosition()
        }
        super.onIsPlayingChanged(isPlaying)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        // Error skips add two to this counter. Each transition, including a successful one,
        // reduces a positive count by one.
        if (consecutivePlaybackErr > 0) {
            consecutivePlaybackErr--
        }

        updateLyricsFetchTargets()
        // End the dismissed state only when the player's playlist matches an open queue.
        if (queueDismissed && mediaItem != null) {
            val playerHoldsOpenQueue = queueBoard.value.selectQueueInPlayer()
            if (playerHoldsOpenQueue) {
                onQueueLoaded()
            }
        }
        // A song that got playing again starts with a full budget next time it comes round.
        mediaItem?.mediaId?.let { streamRefreshes.remove(it) }

        if (player.isPlaying && reason == MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            player.prepare()
            player.play()
        }

        // Auto load more songs
        val q = queueBoard.value.getCurrentQueue()
        val songCount = q?.getSize() ?: -1
        val playlistId = q?.playlistId
        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            playlistId != null // aka "hasNext"
        ) {
            if (SERVICE_DEBUG) Log.d(TAG, "onMediaItemTransition: Triggering queue auto load more")
            scope.launch(SilentHandler) {
                val endpoint = playlistId // playlistId.substringBefore("\n")
                val continuation = null // playlistId.substringAfter("\n")
                val yq = YouTubeQueue(WatchEndpoint(endpoint, continuation))
                val mediaItems = yq.nextPage()
                if (SERVICE_DEBUG) Log.d(TAG, "onMediaItemTransition: Got ${mediaItems.size} songs from radio")
                // Appending songs reloads the player, so discard results
                // if the player was dismissed or a different queue is now current.
                val current = queueBoard.value.getCurrentQueue()
                if (queueDismissed || current == null || current.id != q.id) {
                    return@launch
                }
                current.playlistId = mediaItems.takeLast(4).shuffled().first().id // yq.getContinuationEndpoint()
                if (player.playbackState != STATE_IDLE && songCount > 1) { // initial radio loading is handled by playQueue()
                    queueBoard.value.enqueueEnd(mediaItems.drop(1))
                }
            }
        }

        // Keep the queue's selected song when the player is cleared or remains dismissed.
        if (mediaItem != null && !queueDismissed) {
            queueBoard.value.setCurrQueuePosIndex(player.currentMediaItemIndex)
        }

        // reshuffle queue when shuffle AND repeat all are enabled
        // no, when repeat mode is on, player does not "STATE_ENDED"
        if (player.currentMediaItemIndex == player.mediaItemCount - 1 &&
            (reason == MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == MEDIA_ITEM_TRANSITION_REASON_SEEK) &&
            player.shuffleModeEnabled && player.repeatMode == REPEAT_MODE_ALL
        ) {
            scope.launch(SilentHandler) {
                // Defer reshuffling for 200 ms after the media-item transition callback.
                delay(200)
                if (queueDismissed) return@launch
                queueBoard.value.shuffleCurrent(player.mediaItemCount > 2)
                queueBoard.value.setCurrQueue()
            }
        }

        updateNotification() // also updates when queue changes
    }

    private fun updateLocalPlayback(mediaItem: MediaItem?, reason: Int, windowSequenceNumber: Long?) {
        val song = mediaItem?.metadata?.takeIf { it.isLocal }
        val current = localPlayback.value
        localPlayback.value = when {
            song == null -> null
            // The queue was rebuilt around the song that keeps playing.
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && current?.songId == song.id -> current
            else -> LocalPlayback(
                songId = song.id,
                sequence = ++localPlaybackSequence,
                awaitedWindowSequenceNumber = windowSequenceNumber.takeIf { current?.songId == song.id },
            )
        }
    }

    private fun onLoudnessMeasured(measurement: LoudnessMeasurement) {
        val song = measurement.mediaItem.metadata ?: return
        if (!song.isLocal) return
        val durationUs = measurement.durationUs.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: song.duration.takeIf { it > 0 }?.times(1_000_000L)
            ?: return
        if (measurement.measuredUs * 2 < durationUs) return

        val loudnessDb = measurement.integratedLufs - LOCAL_LOUDNESS_REFERENCE_LUFS
        localLoudness.onMeasured(song.id, loudnessDb, measurement.complete)
        database.transaction {
            if (measurement.complete) {
                updateLoudnessDb(song.id, loudnessDb)
            } else {
                updateLoudnessDbIfAbsent(song.id, loudnessDb)
            }
        }
    }

    private fun restartLocalPlayback() {
        val current = localPlayback.value ?: return
        if (player.currentMetadata?.id != current.songId) return
        localPlayback.value = current.copy(sequence = ++localPlaybackSequence, awaitedWindowSequenceNumber = null)
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == STATE_IDLE) {
            queuePlaylistId = null
        }
        // Playing an ended song again is a new playback without a media item transition.
        if (lastPlaybackState == Player.STATE_ENDED && playbackState != Player.STATE_ENDED) {
            restartLocalPlayback()
        }
        lastPlaybackState = playbackState
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
            } else {
                closeAudioEffectSession()
                if (!player.playWhenReady) {
                    waitingForNetworkConnection.value = false
                }
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_MEDIA_ITEM_TRANSITION)) {
            val mediaId = player.currentMediaItem?.mediaId
            val duration = player.duration
            if (mediaId != null && duration > 0) {
                playerDurationsMs[mediaId] = duration
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }
    }

    override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val playerDurationMs = playerDurationsMs[mediaItem.mediaId]
        offloadScope.launch {
            var minPlaybackDur = (dataStore.get(minPlaybackDurKey, 30).toFloat() / 100)
            // ensure within bounds
            if (minPlaybackDur >= 1f) {
                minPlaybackDur = 0.99f // Ehhh 99 is good enough to avoid any rounding errors
            } else if (minPlaybackDur < 0.01f) {
                minPlaybackDur = 0.01f // Still want "spam skipping" to not count as plays
            }

            val durationMs = mediaItem.metadata?.duration?.takeIf { it > 0 }?.times(1000L) ?: playerDurationMs
            val playRatio = durationMs?.let { playbackStats.totalPlayTimeMs.toFloat() / it } ?: -1f
            if (SERVICE_DEBUG) Log.d(TAG, "Playback ratio: $playRatio Min threshold: $minPlaybackDur")
            if (playRatio >= minPlaybackDur && !dataStore.get(PauseListenHistoryKey, false)) {
                database.query {
                    incrementPlayCount(mediaItem.mediaId)
                    try {
                        insert(
                            Event(
                                songId = mediaItem.mediaId,
                                timestamp = LocalDateTime.now(),
                                playTime = playbackStats.totalPlayTimeMs
                            )
                        )
                    } catch (_: SQLException) {
                    }
                }

                // TODO: support playlist id
                val ytHist = mediaItem.metadata?.isLocal != true && !dataStore.get(PauseRemoteListenHistoryKey, false)
                if (SERVICE_DEBUG) Log.d(TAG, "Trying to register remote history: $ytHist")
                if (ytHist) {
                    val metaResult = YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null)
                    val response = metaResult.getOrNull()
                    if (SERVICE_DEBUG) Log.d(
                        TAG,
                        "History meta: success=${metaResult.isSuccess}" +
                            (metaResult.exceptionOrNull()?.let { " err=${it.javaClass.simpleName}:${it.message}" } ?: "") +
                            " playability=${response?.playabilityStatus?.status}/${response?.playabilityStatus?.reason}" +
                            " hasTracking=${response?.playbackTracking != null}" +
                            " hasVideostats=${response?.playbackTracking?.videostatsPlaybackUrl != null}" +
                            " loggedIn=${YouTube.cookie != null}"
                    )
                    val playbackUrl = response?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    if (SERVICE_DEBUG) Log.d(TAG, "Got playback url: $playbackUrl")
                    playbackUrl?.let {
                        YouTube.registerPlayback(null, playbackUrl)
                            .onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        offloadScope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        val q = queueBoard.value.getCurrentQueue()
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(player.mediaItemCount))
        if (q == null || q.shuffled == shuffleModeEnabled) return
        triggerShuffle()
    }


    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        // FG keep alive
        if (player.isPlaying || !dataStore.get(KeepAliveKey, false)) {
            super.onUpdateNotification(session, startInForegroundRequired)
        }
    }

    fun startSleepTimer(minute: Int, fadeEnabled: Boolean, fadeDurationSeconds: Int) {
        sleepTimer.fadeEnabled = fadeEnabled
        sleepTimer.fadeDurationMs = fadeDurationSeconds * 1000L
        sleepTimer.start(minute)
    }

    override fun onDestroy() {
        if (SERVICE_DEBUG) Log.i(TAG, "Terminating MusicService.")
        serviceJob.cancel()
        // Unregister before deInitQueue: cancelling the collector leaves the ConnectivityManager
        // callback registered. isInitialized guards a teardown before onCreate's async init assigned it.
        if (::connectivityObserver.isInitialized) {
            connectivityObserver.unregister()
        }
        // deInitQueue reads player.currentPosition, so it must run before the player is released.
        deInitQueue()

        mediaSession.player.stop()
        mediaSession.release()
        mediaSession.player.release()
        super.onDestroy()
        if (SERVICE_DEBUG) Log.i(TAG, "Terminated MusicService.")
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (SERVICE_DEBUG) Log.i(TAG, "onTaskRemoved called")
        if (dataStore.get(StopMusicOnTaskClearKey, true) && !dataStore.get(KeepAliveKey, false)) {
            if (SERVICE_DEBUG) Log.i(TAG, "onTaskRemoved kill")
            pauseAllPlayersAndStopSelf()
        } else {
            if (SERVICE_DEBUG) Log.i(TAG, "onTaskRemoved def")
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        private const val LOCAL_LOUDNESS_SECTION_WAIT_TIMEOUT_MS = 1_000L
        private const val LOCAL_LOUDNESS_REFERENCE_LUFS = -7.0

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val SEARCH = "search"

        const val CHANNEL_ID = "music_channel_01"
        const val CHANNEL_NAME = "fgs_workaround"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L

        /** Stream url resolutions allowed per song before a refused stream is treated as fatal. */
        const val MAX_STREAM_REFRESHES = 5

        const val COMMAND_GET_BINDER = "GET_BINDER"
    }
}

class MyAudioOffloadSupportProvider(
    private val default: DefaultAudioOffloadSupportProvider,
    private val disableGaplessOffload: Boolean
) : DefaultAudioSink.AudioOffloadSupportProvider by default {
    override fun getAudioOffloadSupport(
        format: Format,
        audioAttributes: AudioAttributes
    ): AudioOffloadSupport {
        val defaultResult = default.getAudioOffloadSupport(format, audioAttributes)
        val audioOffloadSupport = AudioOffloadSupport.Builder()
        return audioOffloadSupport
            .setIsFormatSupported(defaultResult.isFormatSupported)
            .setIsGaplessSupported(defaultResult.isGaplessSupported && !disableGaplessOffload)
            .setIsSpeedChangeSupported(defaultResult.isSpeedChangeSupported)
            .build()
    }
}
