/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import io.github.yuuichi_s.astertune.BuildConfig
import io.github.yuuichi_s.astertune.constants.LASTFM_DEBUG
import io.github.yuuichi_s.astertune.constants.LastFmNeedsReconnectKey
import io.github.yuuichi_s.astertune.constants.LastFmScrobbleEnabledKey
import io.github.yuuichi_s.astertune.constants.LastFmSessionKey
import io.github.yuuichi_s.astertune.constants.LastFmUsernameKey
import io.github.yuuichi_s.astertune.constants.PauseListenHistoryKey
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.utils.scanners.TagLibScanner
import io.github.yuuichi_s.lastfm.ApiResult
import io.github.yuuichi_s.lastfm.LastFm
import io.github.yuuichi_s.lastfm.Scrobble
import io.github.yuuichi_s.lastfm.ScrobbleQueue
import io.github.yuuichi_s.lastfm.ScrobbleRules
import io.github.yuuichi_s.lastfm.SendOutcome
import io.github.yuuichi_s.lastfm.toSendOutcome
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Records eligible listens on Last.fm, keeping pending listens on disk for later retries.
 *
 * Queuing, sending, and disconnection use a separate scope so pending work can finish after
 * the calling service or screen is destroyed.
 */
object LastFmScrobbler {
    private const val TAG = "LastFmScrobbler"
    private const val QUEUE_FILE = "lastfm_scrobbles.json"
    private const val CLEAR_MARKER_FILE = "lastfm_scrobbles.clear"

    /** Last.fm authorization callback intercepted by the login screen. */
    const val CALLBACK_URL = "astertune://lastfm-auth"

    /**
     * Whether both the Last.fm API key and shared secret are configured.
     * The Last.fm settings are hidden when false.
     */
    val isAvailable = BuildConfig.LASTFM_API_KEY.isNotBlank() && BuildConfig.LASTFM_SECRET.isNotBlank()

    private val api by lazy { LastFm(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_SECRET) }
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> reportException(e) }
    )

    private val queueLock = Mutex()
    private var queue: ScrobbleQueue? = null

    // Blocks sends after authentication, API key, or signature errors until the app restarts.
    @Volatile
    private var apiKeyRejected = false

    fun authUrl(): String = api.authUrl(CALLBACK_URL)

    /**
     * Queues an eligible listen and attempts to send the queue asynchronously.
     *
     * Skips listens unless an account is connected, scrobbling is enabled, and listen history is
     * not paused. [startedAtMs] is the start time in milliseconds since the Unix epoch.
     */
    fun queueListen(
        context: Context,
        metadata: MediaMetadata?,
        durationMs: Long,
        playedMs: Long,
        startedAtMs: Long,
    ) {
        if (!isAvailable || metadata == null) {
            return
        }

        val appContext = context.applicationContext
        scope.launch {
            val settings = appContext.dataStore.data.first()
            val username = settings[LastFmUsernameKey]
            if (settings[LastFmSessionKey].isNullOrEmpty() || username.isNullOrEmpty() ||
                settings[LastFmScrobbleEnabledKey] == false ||
                settings[PauseListenHistoryKey] == true) {

                return@launch
            }

            if (!ScrobbleRules.isScrobblable(durationMs, playedMs)) {
                return@launch
            }

            val scrobble = metadata.toScrobble(
                timestamp = startedAtMs / 1000,
                durationMs = durationMs,
            ) ?: return@launch

            if (LASTFM_DEBUG) Log.d(TAG, "Queue: $scrobble")

            queueLock.withLock {
                // Recheck under the lock in case the account changed or was disconnected.
                val current = appContext.dataStore.data.first()
                if (current[LastFmSessionKey].isNullOrEmpty() ||
                    !username.equals(current[LastFmUsernameKey], ignoreCase = true)) {

                    return@launch
                }
                queueLocked(appContext).add(scrobble)
            }
            send(appContext)
        }
    }

    /** Starts sending the queued listens asynchronously. */
    fun flush(context: Context) {
        if (!isAvailable) return

        val appContext = context.applicationContext
        scope.launch { send(appContext) }
    }

    /**
     * Exchanges the token from the callback URL for a session and stores it. Clears pending listens
     * when the account changes, then starts sending the queue asynchronously.
     *
     * Returns false when Last.fm did not give a session.
     */
    suspend fun connect(context: Context, token: String): Boolean {
        val appContext = context.applicationContext
        val result = api.getSession(token)
        if (result !is ApiResult.Success) {
            Log.w(TAG, "auth.getSession failed: $result")
            return false
        }

        val session = result.value
        queueLock.withLock {
            val previousUser = appContext.dataStore.data.first()[LastFmUsernameKey]
            if (previousUser != null && !previousUser.equals(session.name, ignoreCase = true)) {
                queueLocked(appContext).clear()
            }

            appContext.dataStore.edit { settings ->
                settings[LastFmSessionKey] = session.key
                settings[LastFmUsernameKey] = session.name
                settings.remove(LastFmNeedsReconnectKey)
            }
        }

        flush(appContext)
        return true
    }

    /**
     * Asynchronously forgets the session and clears pending listens.
     *
     * Waits for any batch being sent to finish.
     */
    fun disconnect(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            queueLock.withLock {
                appContext.dataStore.edit { settings ->
                    settings.remove(LastFmSessionKey)
                    settings.remove(LastFmUsernameKey)
                    settings.remove(LastFmNeedsReconnectKey)
                }
                queueLocked(appContext).clear()
            }
        }
    }

    /**
     * Marks pending listens for deletion on the next app start, before sending resumes.
     *
     * Called before restoring settings, which may belong to another account. Throws if the marker
     * cannot be written.
     */
    fun clearQueueOnNextStart(context: Context) {
        File(context.filesDir, CLEAR_MARKER_FILE).createNewFile()
    }

    /** Sends the queue one batch at a time, so that a disconnect can run between batches. */
    private suspend fun send(context: Context) {
        while (!apiKeyRejected) {
            val shouldStop = queueLock.withLock { sendOldestBatchLocked(context) }

            if (shouldStop) {
                return
            }
        }
    }

    /** Sends the oldest batch, returning true to stop sending for now. */
    private suspend fun sendOldestBatchLocked(context: Context): Boolean {
        if (apiKeyRejected) return true

        val settings = context.dataStore.data.first()
        val sessionKey = settings[LastFmSessionKey]
        if (sessionKey.isNullOrEmpty() || settings[LastFmNeedsReconnectKey] == true) {
            return true
        }

        val batch = queueLocked(context).peek(LastFm.MAX_SCROBBLES_PER_REQUEST)

        if (batch.isEmpty()) return true

        val result = api.scrobble(sessionKey, batch)
        if (LASTFM_DEBUG) Log.d(TAG, "Sent ${batch.size}: $result")

        return when (result.toSendOutcome()) {
            SendOutcome.DONE -> {
                queueLocked(context).removeOldest(batch.size)
                false
            }

            SendOutcome.RETRY -> true
            SendOutcome.SESSION_INVALID -> {
                context.dataStore.edit { it[LastFmNeedsReconnectKey] = true }
                true
            }

            SendOutcome.API_KEY_REJECTED -> {
                Log.w(TAG, "API key rejected: $result")
                apiKeyRejected = true
                true
            }
        }
    }

    private fun queueLocked(context: Context): ScrobbleQueue =
        queue ?: ScrobbleQueue(File(context.filesDir, QUEUE_FILE)).also { created ->
            val marker = File(context.filesDir, CLEAR_MARKER_FILE)
            if (marker.exists()) {
                created.clear()
                marker.delete()
            }
            queue = created
        }

    private fun MediaMetadata.toScrobble(timestamp: Long, durationMs: Long): Scrobble? {
        // Avoid treating multiple artist names as a single artist.
        val artist = artists.firstOrNull { it.name.isNotBlank() }?.name?.trim() ?: return null
        val track = title.trim().takeIf { it.isNotEmpty() } ?: return null
        val path = localPath

        // The title may come from the file name. Require a readable, non-blank title tag.
        if (isLocal && path != null &&
            path.substringAfterLast('/').substringBeforeLast('.') == title &&
            TagLibScanner.hasTitleTag(File(path)) != true) {

            return null
        }

        return Scrobble(
            artist = artist,
            track = track,
            timestamp = timestamp,
            album = album?.title?.trim()?.takeIf { it.isNotEmpty() },
            durationSec = durationMs / 1000,
        )
    }
}
