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
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
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
import io.github.yuuichi_s.astertune.db.entities.RelatedSongMap
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
import com.google.common.util.concurrent.MoreExecutors
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import dagger.hilt.android.AndroidEntryPoint
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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

    // Parent of every service coroutine scope. Cancelled once in onDestroy() so no scope outlives the
    // service. Each scope owns a SupervisorJob child of this so a failure in one coroutine neither
    // cancels its siblings nor the whole service.
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(SupervisorJob(serviceJob) + Dispatchers.Main)
    private val offloadScope = CoroutineScope(SupervisorJob(serviceJob) + playerCoroutine)

    // Scope for the per-request playQueue() coroutine. Uses the Main dispatcher because it mutates the
    // player, which must run on the application thread; the SupervisorJob binds it to the service lifecycle.
    private val queueLoadScope = CoroutineScope(SupervisorJob(serviceJob) + Dispatchers.Main)

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
    /**
     * Resolved stream urls by media id, with the time they claim to stay valid until. Hoisted out of
     * [createDataSourceFactory] so a url googlevideo refuses mid-song can be dropped and resolved
     * again, which is the only way past a url that validates and then dies a minute in.
     */
    private val songUrlCache = HashMap<String, Pair<String, Long>>()

    /** Client each cached url came from, so a refused one can be skipped on the next resolution. */
    private val songUrlClient = HashMap<String, String>()

    /** Headers each cached url has to be fetched with, kept alongside it for the cache-hit path. */
    private val songUrlHeaders = HashMap<String, Map<String, String>>()

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
                currentFormat,
                dataStore.data
                    .map { it[AudioNormalizationKey] ?: true }
                    .distinctUntilChanged()
            ) { format, normalizeAudio ->
                format to normalizeAudio
            }.collectLatest(scope) { (format, normalizeAudio) ->
                normalizeFactor.value = if (normalizeAudio && format?.loudnessDb != null) {
                    min(10f.pow(-format.loudnessDb.toFloat() / 20), 1f)
                } else {
                    1f
                }
            }

            // Fetch lyrics for the current song and prefetch the upcoming ones in a single coroutine so
            // they never run concurrently: the current fetch (only while lyrics display is on) completes
            // before prefetch starts. collectLatest restarts on a song change or a lyrics-display toggle,
            // cancelling any fetch in flight; already-fetched songs are skipped by the DB check.
            combine(
                dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
                lyricsFetchTargets
            ) { showLyrics, targets -> showLyrics to targets }
                .collectLatest(offloadScope) { (showLyrics, targets) ->
                    val current = targets.current
                    if (showLyrics && current != null && lyricsHelper.shouldFetch(current.id)) {
                        lyricsHelper.fetchAndStoreRemote(current, LyricsFetchRole.CURRENT)
                    }
                    // Read prefetch settings and connectivity after the current-song fetch so their changes
                    // do not cancel it. Changes take effect on the next lyricsFetchTargets emission.
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
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint = YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
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
     * Play a queue.
     *
     * @param queue Queue to play.
     * @param playWhenReady
     * @param shouldResume Set to true for the player should resume playing at the current song's last save position or
     * false to start from the beginning.
     * @param replace Replace media items instead of the underlying logic
     * @param title Title override for the queue. If this value us unspecified, this method takes the value from queue.
     * If both are unspecified, the title will default to "Queue".
     */
    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
        shouldResume: Boolean = false,
        replace: Boolean = false,
        isRadio: Boolean = false,
        title: String? = null
    ) {
        if (!qbInit.value) {
            runBlocking(Dispatchers.IO) {
                initQueue()
            }
        }

        var queueTitle = title
        queuePlaylistId = queue.playlistId
        var q: MultiQueueObject? = null
        val preloadItem = queue.preloadItem
        queueLoadScope.launch {
            if (SERVICE_DEBUG) Log.d(TAG, "playQueue: Resolving additional queue data...")
            try {
                if (preloadItem != null) {
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
                // do not find a title if an override is provided
                if ((title == null) && initialStatus.title != null) {
                    queueTitle = initialStatus.title

                    if (preloadItem != null && q != null) {
                        queueBoard.value.renameQueue(q!!, queueTitle)
                    }
                }

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
                    queueBoard.value.setCurrQueue(q, shouldResume)
                }

                player.prepare()
                player.playWhenReady = playWhenReady
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
     * Add items to queue, right after current playing item
     */
    fun enqueueNext(items: List<MediaItem>) {
        scope.launch {
            if (!qbInit.value) {

                // when enqueuing next when player isn't active, play as a new song
                if (items.isNotEmpty()) {
                    playQueue(
                        ListQueue(
                            title = items.first().mediaMetadata.title.toString(),
                            items = items.mapNotNull { it.metadata }
                        )
                    )
                }
            } else {
                // enqueue next
                queueBoard.value.getCurrentQueue()?.let {
                    queueBoard.value.addSongsToQueue(it, player.currentMediaItemIndex + 1, items.mapNotNull { it.metadata })
                }
            }
        }
    }

    /**
     * Add items to end of current queue
     */
    fun enqueueEnd(items: List<MediaItem>) {
        queueBoard.value.enqueueEnd(items.mapNotNull { it.metadata })
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

    suspend fun initQueue() {
        if (SERVICE_DEBUG) Log.i(TAG, "+initQueue()")
        val persistQueue = dataStore.get(PersistentQueueKey, true)
        val maxQueues = dataStore.get(MaxQueuesKey, 19)
        if (persistQueue) {
            queueBoard.value = QueueBoard(this, queueBoard.value.masterQueues, database.readQueue().toMutableList(), maxQueues)
        } else {
            queueBoard.value = QueueBoard(this, queueBoard.value.masterQueues, maxQueues = maxQueues)
        }
        if (SERVICE_DEBUG) Log.d(TAG, "Queue with $maxQueues queue limit. Persist queue = $persistQueue. Queues loaded = ${queueBoard.value.masterQueues.size}")
        qbInit.value = true
        if (SERVICE_DEBUG) Log.i(TAG, "-initQueue()")
    }

    fun deInitQueue() {
        if (SERVICE_DEBUG) Log.i(TAG, "+deInitQueue()")
        val pos = player.currentPosition
        queueBoard.value.shutdown()
        if (dataStore.get(PersistentQueueKey, true)) {
            runBlocking(Dispatchers.IO) {
                saveQueueToDisk(pos)
            }
        }
        // do not replace the object. Can lead to entire queue being deleted even though it is supposed to be saved already
        qbInit.value = false
        if (SERVICE_DEBUG) Log.i(TAG, "-deInitQueue()")
    }

    suspend fun saveQueueToDisk(currentPosition: Long) {
        val data = queueBoard.value.getAllQueues()
        // The queue can be empty when the service is torn down before initQueue() finishes loading it.
        data.lastOrNull()?.let { it.lastSongPos = currentPosition }
        database.updateAllQueues(data)
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
                        HybridCacheDataSinkFactory(playerCache) { dataSpec ->
                            val isLocal = queueBoard.value.getCurrentQueue()?.findSong(dataSpec.key ?: "")?.isLocal == true
                            if (SERVICE_DEBUG) Log.d(TAG, "SONG CACHE: ${!isLocal}")
                            !isLocal
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

            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                if (SERVICE_DEBUG) Log.d(TAG, "PLAYING: remote song (temp cache)")
                offloadScope.launch { recoverSong(mediaId) }
                // Bounded like the fresh-resolve path below; letting this read run open-ended was
                // measurably worse (playback died at 31 s rather than 62 s). The headers matter as
                // much as the url: googlevideo expects the fetch to look like the client it issued
                // the url for, and every read past the first chunk comes through here.
                return@Factory dataSpec.withUri(it.first.toUri())
                    .subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
                    .withRequestHeaders(
                        dataSpec.httpRequestHeaders + songUrlHeaders[mediaId].orEmpty()
                    )
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

            val streamUrl = playbackData.streamUrl

            songUrlCache[mediaId] =
                streamUrl to System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
            songUrlClient[mediaId] = playbackData.streamClient
            songUrlHeaders[mediaId] = playbackData.streamHeaders
            dataSpec.withUri(streamUrl.toUri())
                .subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
                .withRequestHeaders(dataSpec.httpRequestHeaders + playbackData.streamHeaders)
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
                                emptyArray(),
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
                                emptyArray(),
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
     * Drops the refused stream url for [mediaId] and re-prepares so it resolves again, skipping the
     * client that just failed.
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

        val failedClient = songUrlClient.remove(mediaId)
        songUrlHeaders.remove(mediaId)
        songUrlCache.remove(mediaId)
        if (failedClient == "WEB_REMIX") {
            YTPlayerUtils.markWebRemixFailed(mediaId)
        }
        Log.w(TAG, "stream for $mediaId refused on $failedClient, resolving again")

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

        // googlevideo hands out urls that pass validation and are then refused partway through the
        // track, so the player is the first thing that learns a client's url is no good. Report it
        // back and resolve again against a different client rather than dropping the song.
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
            val pos = player.currentPosition
            val q = queueBoard.value.getCurrentQueue()
            q?.lastSongPos = pos
        }
        super.onIsPlayingChanged(isPlaying)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        // +2 when and error happens, and -1 when transition. Thus when error, number increments by 1, else doesn't change
        if (consecutivePlaybackErr > 0) {
            consecutivePlaybackErr--
        }

        updateLyricsFetchTargets()
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
                q.playlistId = mediaItems.takeLast(4).shuffled().first().id // yq.getContinuationEndpoint()
                if (SERVICE_DEBUG) Log.d(TAG, "onMediaItemTransition: Got ${mediaItems.size} songs from radio")
                if (player.playbackState != STATE_IDLE && songCount > 1) { // initial radio loading is handled by playQueue()
                    queueBoard.value.enqueueEnd(mediaItems.drop(1))
                }
            }
        }

        queueBoard.value.setCurrQueuePosIndex(player.currentMediaItemIndex)

        // reshuffle queue when shuffle AND repeat all are enabled
        // no, when repeat mode is on, player does not "STATE_ENDED"
        if (player.currentMediaItemIndex == player.mediaItemCount - 1 &&
            (reason == MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == MEDIA_ITEM_TRANSITION_REASON_SEEK) &&
            player.shuffleModeEnabled && player.repeatMode == REPEAT_MODE_ALL
        ) {
            scope.launch(SilentHandler) {
                // or else race condition: Assertions.checkArgument(eventTime.realtimeMs >= currentPlaybackStateStartTimeMs) fails in updatePlaybackState()
                delay(200)
                queueBoard.value.shuffleCurrent(player.mediaItemCount > 2)
                queueBoard.value.setCurrQueue()
            }
        }

        updateNotification() // also updates when queue changes
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == STATE_IDLE) {
            queuePlaylistId = null
        }
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
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }
    }

    override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
        offloadScope.launch {
            val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
            var minPlaybackDur = (dataStore.get(minPlaybackDurKey, 30).toFloat() / 100)
            // ensure within bounds
            if (minPlaybackDur >= 1f) {
                minPlaybackDur = 0.99f // Ehhh 99 is good enough to avoid any rounding errors
            } else if (minPlaybackDur < 0.01f) {
                minPlaybackDur = 0.01f // Still want "spam skipping" to not count as plays
            }

            val playRatio =
                playbackStats.totalPlayTimeMs.toFloat() / ((mediaItem.metadata?.duration?.times(1000)) ?: -1)
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
