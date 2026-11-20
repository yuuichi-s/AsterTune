package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yuuichi_s.astertune.constants.LYRICS_DEBUG
import io.github.yuuichi_s.astertune.constants.LyricKaraokeEnable
import io.github.yuuichi_s.astertune.constants.LyricSourcePrefKey
import io.github.yuuichi_s.astertune.constants.LyricTrimKey
import io.github.yuuichi_s.astertune.constants.MultilineLrcKey
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.LyricsEntity
import io.github.yuuichi_s.astertune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.astertune.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Role of a remote fetch, used to correlate log lines when several fetches run for different songs.
 */
enum class LyricsFetchRole(val log: String) {
    CURRENT("current"),
    PREFETCH("prefetch"),
    MANUAL("manual"),
}

/** Resolves and stores lyrics and collects manual-search candidates. */
@Singleton
class LyricsHelper internal constructor(
    private val context: Context,
    val database: MusicDatabase,
    private val lyricsProviders: List<LyricsProvider>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
    ) : this(
        context,
        database,
        listOf(
            BetterLyricsProvider,
            LyricsPlusProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            YouTubeLyricsProvider,
            YouTubeSubtitleLyricsProvider,
        ),
    )

    private data class ManualSearchKey(
        val mediaId: String,
        val title: String,
        val artists: String,
        val duration: Int,
        val providerIds: List<String>,
    )

    private val cache = LruCache<ManualSearchKey, List<LyricsResult>>(MAX_CACHE_SIZE)

    /**
     * Mutexes that allow one remote fetch per video at a time.
     *
     * [fetchAndStoreRemote] checks the database again after acquiring the lock.
     * The map keeps one entry per video for the life of this helper.
     */
    private val fetchMutexes = HashMap<String, Mutex>()
    private val fetchMutexesGuard = Mutex()

    private suspend fun fetchMutexFor(videoId: String): Mutex =
        fetchMutexesGuard.withLock { fetchMutexes.getOrPut(videoId) { Mutex() } }

    /**
     * Resolves stored, local, or remote lyrics according to the source preference.
     * Tries local lyrics first when they are preferred; otherwise reuses stored lyrics.
     * Queries remote providers only when [shouldFetch] requires it.
     * Uses local lyrics as fallback when remote lyrics are preferred and no positive stored
     * result is available.
     */
    suspend fun getLyrics(mediaMetadata: MediaMetadata): SemanticLyrics? {
        val parserOptions = getParserOptions()
        val prefLocal = isLocalPreferred()

        val dbLyrics = database.lyrics(mediaMetadata.id).let { it.first()?.lyrics }
        val hasPositive = dbLyrics != null && dbLyrics != LYRICS_NOT_FOUND
        if (hasPositive && !prefLocal) {
            return LrcUtils.parseLyrics(dbLyrics, null, parserOptions, null)
        }

        val localLyrics: SemanticLyrics? = getLocalLyrics(mediaMetadata, parserOptions)

        // fallback to secondary provider when primary is unavailable
        if (prefLocal) {
            if (localLyrics != null) {
                return localLyrics
            }
            if (hasPositive) {
                return LrcUtils.parseLyrics(dbLyrics, null, parserOptions, null)
            }
        }

        if (shouldFetch(mediaMetadata.id)) {
            fetchAndStoreRemote(mediaMetadata, LyricsFetchRole.MANUAL)
            val fetched = database.lyrics(mediaMetadata.id).let { it.first()?.lyrics }
            if (fetched != null && fetched != LYRICS_NOT_FOUND) {
                return LrcUtils.parseLyrics(fetched, null, parserOptions, null)
            }
        }
        return if (!prefLocal) localLyrics else null
    }

    /**
     * Determines whether remote lyrics need fetching for [videoId].
     * Found lyrics are reused unless [forceRefresh] is true.
     * Missing entries and stale or invalid cached absence require a fetch.
     */
    suspend fun shouldFetch(videoId: String, forceRefresh: Boolean = false): Boolean {
        val entity = database.lyrics(videoId).first()
        val signature = ProviderSelection.snapshot(context, lyricsProviders).signature
        return shouldFetchLyrics(entity, signature, System.currentTimeMillis(), forceRefresh)
    }

    /**
     * Fetches and saves remote lyrics when needed.
     *
     * Set forceRefresh to true to fetch even if the saved result can be reused.
     * Saves lyrics or a confirmed "no lyrics found" result. Preserves changes
     * made to the saved lyrics during the lookup.
     *
     * @param role caller category used for logging
     */
    suspend fun fetchAndStoreRemote(
        mediaMetadata: MediaMetadata,
        role: LyricsFetchRole,
        forceRefresh: Boolean = false,
    ) {
        fetchMutexFor(mediaMetadata.id).withLock {
            try {
                // The enabled providers and their signature are pinned once here so the search set, the
                // all-NotFound decision and the stored signature all use the same snapshot.
                val selection = ProviderSelection.snapshot(context, lyricsProviders)
                val existing = database.lyrics(mediaMetadata.id).first()
                if (!shouldFetchLyrics(existing, selection.signature, System.currentTimeMillis(), forceRefresh)) {
                    return
                }
                val result = getRemoteLyrics(mediaMetadata, role, selection)
                val now = System.currentTimeMillis()
                val entity = when (result) {
                    is RemoteLyricsResult.Found ->
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = result.raw,
                            provider = result.provider,
                            lastCheckedAt = now,
                            providerSignature = selection.signature,
                        )

                    RemoteLyricsResult.DefinitiveNotFound ->
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = LYRICS_NOT_FOUND,
                            provider = null,
                            lastCheckedAt = now,
                            providerSignature = selection.signature,
                        )

                    RemoteLyricsResult.Indeterminate, RemoteLyricsResult.Skipped -> null
                }
                if (entity != null) {
                    // The lyrics menu saves edits and search selections outside the fetch mutex.
                    val saved = database.awaitTransaction {
                        if (lyricsById(mediaMetadata.id) == existing) {
                            upsert(entity)
                            true
                        } else {
                            false
                        }
                    }

                    if (saved) {
                        Log.d(TAG, "saved: videoId=${mediaMetadata.id} role=${role.log} provider=${(result as? RemoteLyricsResult.Found)?.provider ?: "NOT_FOUND"}")
                    } else {
                        Log.d(TAG, "not saved: videoId=${mediaMetadata.id} role=${role.log} row changed during fetch")
                    }
                } else {
                    Log.d(TAG, "not saved: videoId=${mediaMetadata.id} role=${role.log} result=${result::class.simpleName}")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "cancelled: videoId=${mediaMetadata.id} role=${role.log}")
                throw e
            }
        }
    }

    /**
     * Read the lyric parsing preferences (trim / multiline) shared by all resolution paths
     */
    suspend fun getParserOptions(): LrcUtils.LrcParserOptions {
        val trim = context.dataStore.get(LyricTrimKey, defaultValue = false)
        val multiline = context.dataStore.get(MultilineLrcKey, defaultValue = true)
        return LrcUtils.LrcParserOptions(trim, multiline, "Unable to parse lyrics")
    }

    suspend fun isLocalPreferred(): Boolean = context.dataStore.get(LyricSourcePrefKey, true)

    /**
     * Looks up lyrics from one provider.
     *
     * Rethrows cancellation and returns [LyricsFetchResult.Failed] for other exceptions.
     */
    private suspend fun LyricsProvider.fetchIsolated(
        mediaMetadata: MediaMetadata,
        artistName: String,
    ): LyricsFetchResult =
        try {
            getLyrics(mediaMetadata.id, mediaMetadata.title, artistName, mediaMetadata.duration)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LyricsFetchResult.Failed(e)
        }

    /**
     * Searches remote providers in parallel and normally takes the first synced result.
     *
     * When word-synced lyrics are preferred, keeps the first line-synced result
     * as a fallback and waits briefly for word-synced lyrics.
     */
    private suspend fun getRemoteLyrics(
        mediaMetadata: MediaMetadata,
        role: LyricsFetchRole,
        selection: ProviderSelection,
    ): RemoteLyricsResult {
        val artistName = mediaMetadata.artists
            .filter { it.id != null }
            .joinToString { it.name.removeSuffix(" - Topic") }
            .ifEmpty { mediaMetadata.artists.joinToString { it.name } }
        val start = System.currentTimeMillis()
        Log.d(TAG, "start: videoId=${mediaMetadata.id} role=${role.log} title=\"${mediaMetadata.title}\" artist=\"${artistName}\"")

        lyricsProviders.filterNot { it in selection.providers }.forEach { provider ->
            Log.d(TAG, "${provider.name} SKIPPED (disabled) videoId=${mediaMetadata.id} role=${role.log}")
        }
        val enabled = selection.providers
        if (enabled.isEmpty()) {
            Log.d(TAG, "end: skipped videoId=${mediaMetadata.id} role=${role.log} total=0ms (no enabled providers)")
            return RemoteLyricsResult.Skipped
        }

        val verifyOptions = getParserOptions().copy(errorText = null)
        val preferWordSynced = context.dataStore.get(LyricKaraokeEnable, false) &&
                enabled.any { it.id == LyricsPlusProvider.id }

        return coroutineScope {
            val channel = Channel<ProviderOutcome>(Channel.UNLIMITED)
            val fetchJobs = enabled.map { provider ->
                launch {
                    val t0 = System.currentTimeMillis()
                    val result = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        provider.fetchIsolated(mediaMetadata, artistName)
                    }
                    val elapsed = System.currentTimeMillis() - t0
                    val outcome: LyricsFetchResult = when (result) {
                        null -> {
                            Log.d(TAG, "${provider.name} FAILURE in ${elapsed}ms videoId=${mediaMetadata.id} role=${role.log}: timeout after ${PROVIDER_TIMEOUT_MS}ms")
                            LyricsFetchResult.Failed()
                        }

                        is LyricsFetchResult.Found -> {
                            Log.d(TAG, "${provider.name} SUCCESS in ${elapsed}ms videoId=${mediaMetadata.id} role=${role.log}")
                            result
                        }

                        LyricsFetchResult.NotFound -> {
                            Log.d(TAG, "${provider.name} NOT_FOUND in ${elapsed}ms videoId=${mediaMetadata.id} role=${role.log}")
                            result
                        }

                        is LyricsFetchResult.Failed -> {
                            Log.d(TAG, "${provider.name} FAILURE in ${elapsed}ms videoId=${mediaMetadata.id} role=${role.log}: ${result.cause?.message}")
                            result
                        }
                    }
                    channel.send(ProviderOutcome(provider.name, outcome))
                }
            }
            // Close the channel once every provider has reported so exhaustion is detected promptly
            // instead of waiting for the overall timeout.
            val closer = launch {
                fetchJobs.joinAll()
                channel.close()
            }

            val aggregator = RemoteLyricsAggregator(enabled.size, preferWordSynced)
            val classifyFound: (String) -> FoundKind = { raw ->
                val parsed = try {
                    LrcUtils.parseLyrics(raw, null, verifyOptions, null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                when (parsed) {
                    is SemanticLyrics.SyncedLyrics ->
                        if (parsed.text.any { it.words != null }) FoundKind.WORD_SYNCED
                        else FoundKind.LINE_SYNCED
                    is SemanticLyrics.UnsyncedLyrics -> FoundKind.UNSYNCED
                    null -> FoundKind.UNPARSEABLE
                }
            }
            val deadline = start + OVERALL_TIMEOUT_MS
            var graceDeadline: Long? = null
            val pendingWordCapable = enabled
                .filter { it.id in WORD_CAPABLE_PROVIDER_IDS }
                .mapTo(mutableSetOf()) { it.name }

            try {
                while (true) {
                    val remaining = minOf(deadline, graceDeadline ?: deadline) - System.currentTimeMillis()
                    if (remaining <= 0) break
                    val outcome = withTimeoutOrNull(remaining) {
                        channel.receiveCatching().getOrNull()
                    } ?: break // overall timeout, grace period over, or every provider has reported

                    if (aggregator.offer(outcome.providerName, outcome.result, classifyFound)) {
                        break
                    }

                    pendingWordCapable.remove(outcome.providerName)
                    if (preferWordSynced && aggregator.hasLineSynced) {
                        if (pendingWordCapable.isEmpty()) break

                        if (graceDeadline == null) {
                            graceDeadline = System.currentTimeMillis() + WORD_SYNC_GRACE_MS
                        }
                    }
                }
            } finally {
                fetchJobs.forEach { it.cancel() }
                closer.cancel()
                channel.close()
            }

            val totalMs = System.currentTimeMillis() - start
            val result = aggregator.result()
            when (result) {
                is RemoteLyricsResult.Found ->
                    Log.d(TAG, "adopted: videoId=${mediaMetadata.id} role=${role.log} provider=${result.provider} synced=${result.synced} wordSynced=${result.wordSynced} total=${totalMs}ms")

                RemoteLyricsResult.DefinitiveNotFound ->
                    Log.d(TAG, "end: not found videoId=${mediaMetadata.id} role=${role.log} total=${totalMs}ms")

                RemoteLyricsResult.Indeterminate ->
                    Log.d(TAG, "end: indeterminate videoId=${mediaMetadata.id} role=${role.log} total=${totalMs}ms")

                RemoteLyricsResult.Skipped -> {} // handled above
            }
            result
        }
    }

    /** Loads and parses a local .lrc file. */
    fun getLocalLyrics(
        mediaMetadata: MediaMetadata,
        parserOptions: LrcUtils.LrcParserOptions
    ): SemanticLyrics? {
        if (LocalLyricsProvider.isEnabled(context) && mediaMetadata.localPath != null) {
            return LocalLyricsProvider.getLyricsNew(
                mediaMetadata.localPath,
                parserOptions
            )
        }

        return null
    }

    /**
     * Searches one provider and delivers candidates through [callback].
     *
     * The caller keeps candidates already delivered if the search fails. Cancellation propagates.
     *
     * @return `true` if the search completes normally, or `false` if it fails.
     */
    private suspend fun LyricsProvider.searchIsolated(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (String) -> Unit,
    ): Boolean =
        try {
            getAllLyrics(mediaId, songTitle, songArtists, duration, callback)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is LyricsSearchFailedException || LYRICS_DEBUG) {
                reportException(e)
            }
            false
        }

    /**
     * Delivers manual search results in provider order.
     *
     * Caches the result list, even if empty, only if all searches finish
     * without errors or timeouts.
     */
    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        val karaoke = context.dataStore.get(LyricKaraokeEnable, false)
        val enabled = lyricsProviders.filter { it.isEnabled(context) }
        val searchOrder = if (karaoke) {
            enabled
        } else {
            enabled.filterNot { it.id == LyricsPlusProvider.id } + enabled.filter { it.id == LyricsPlusProvider.id }
        }
        val cacheKey = ManualSearchKey(mediaId, songTitle, songArtists, duration, searchOrder.map { it.id })
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        var anyFailed = false
        searchOrder.forEach { provider ->
            val search: suspend () -> Boolean = {
                provider.searchIsolated(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
            val succeeded =
                if (provider.id == LyricsPlusProvider.id) {
                    val timeout = if (karaoke) LYRICSPLUS_MANUAL_TIMEOUT_FIRST_MS else LYRICSPLUS_MANUAL_TIMEOUT_LAST_MS
                    withTimeoutOrNull(timeout) { search() } ?: run {
                        Log.d(TAG, "${provider.name} manual search timed out after ${timeout}ms")
                    false
                    }
                } else {
                    search()
            }
            if (!succeeded) anyFailed = true
        }
        if (!anyFailed) cache.put(cacheKey, allResult)
    }

    companion object {
        private const val TAG = "LyricsHelper"
        private const val MAX_CACHE_SIZE = 3

        /** Per-provider timeout for a single remote lookup. */
        private const val PROVIDER_TIMEOUT_MS = 8000L

        /** Upper bound for resolving lyrics across all providers of a single song. */
        private const val OVERALL_TIMEOUT_MS = 12000L

        /** Maximum wait for word-synced lyrics after holding a line-synced result. */
        private const val WORD_SYNC_GRACE_MS = 3000L
        private val WORD_CAPABLE_PROVIDER_IDS = setOf(LyricsPlusProvider.id, BetterLyricsProvider.id)

        private const val LYRICSPLUS_MANUAL_TIMEOUT_FIRST_MS = 5000L

        private const val LYRICSPLUS_MANUAL_TIMEOUT_LAST_MS = 8000L
    }
}

/** Maximum age for reusing a saved result that says no lyrics were found. */
const val NEGATIVE_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Determines whether remote lyrics need fetching.
 *
 * Reuses found lyrics unless [forceRefresh] is true. Cached absence is checked against
 * [ttlMs] and the provider [signature].
 */
internal fun shouldFetchLyrics(
    entity: LyricsEntity?,
    signature: String,
    now: Long,
    forceRefresh: Boolean,
    ttlMs: Long = NEGATIVE_CACHE_TTL_MS,
): Boolean {
    if (forceRefresh) return true
    if (entity == null) return true
    if (entity.lyrics != LYRICS_NOT_FOUND) return false
    val lastChecked = entity.lastCheckedAt ?: return true
    val storedSignature = entity.providerSignature ?: return true
    if (storedSignature != signature) return true
    if (now < lastChecked) return true
    return now - lastChecked >= ttlMs
}

/** Lyrics candidate with its provider name. */
data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

/**
 * Outcome reported by a single provider during parallel resolution.
 */
private data class ProviderOutcome(
    val providerName: String,
    val result: LyricsFetchResult,
)

/**
 * Snapshot of the enabled providers taken once at the start of a fetch, together with a signature
 * derived from their stable ids (sorted, so provider order never affects it). The same snapshot drives
 * the search set, the all-NotFound decision and the stored signature, so a provider-configuration
 * change during a fetch cannot desync them.
 */
data class ProviderSelection(
    val providers: List<LyricsProvider>,
    val signature: String,
) {
    companion object {
        fun snapshot(context: Context, all: List<LyricsProvider>): ProviderSelection {
            val enabled = all.filter { it.isEnabled(context) }
            val signature = enabled.map { it.id }.sorted().joinToString(",")
            return ProviderSelection(enabled, signature)
        }
    }
}

/**
 * Aggregate outcome of resolving lyrics across every enabled provider for one song.
 */
sealed interface RemoteLyricsResult {
    data class Found(
        val provider: String,
        val raw: String,
        val synced: Boolean,
        val wordSynced: Boolean = false,
    ) : RemoteLyricsResult
    data object DefinitiveNotFound : RemoteLyricsResult
    data object Indeterminate : RemoteLyricsResult
    data object Skipped : RemoteLyricsResult
}

/** How a [LyricsFetchResult.Found] parses when judged for adoption. */
enum class FoundKind { WORD_SYNCED, LINE_SYNCED, UNSYNCED, UNPARSEABLE }

/**
 * Chooses the best lyrics from the results received so far.
 *
 * Keeps the first result of each type. Prefers word-synced lyrics, then line-synced lyrics,
 * then unsynced lyrics. If no result is usable, returns [RemoteLyricsResult.DefinitiveNotFound]
 * only when every expected provider reports that it found no lyrics. Otherwise returns
 * [RemoteLyricsResult.Indeterminate].
 *
 * @param enabledCount number of providers expected to report
 * @param preferWordSynced whether to keep collecting results after finding line-synced lyrics
 */
class RemoteLyricsAggregator(private val enabledCount: Int, private val preferWordSynced: Boolean) {
    private var adoptedWordSynced: RemoteLyricsResult.Found? = null
    private var heldLineSynced: RemoteLyricsResult.Found? = null
    private var heldUnsynced: RemoteLyricsResult.Found? = null

    val hasLineSynced: Boolean
        get() = heldLineSynced != null
    private var notFoundCount = 0
    private var nonNotFoundCount = 0

    /**
     * Records one provider's result.
     *
     * @return true if the caller should stop collecting results.
     */
    fun offer(providerName: String, result: LyricsFetchResult, classifyFound: (String) -> FoundKind): Boolean {
        when (result) {
            is LyricsFetchResult.Found -> when (classifyFound(result.raw)) {
                FoundKind.WORD_SYNCED -> {
                    if (adoptedWordSynced == null) {
                        adoptedWordSynced =
                            RemoteLyricsResult.Found(providerName, result.raw, synced = true, wordSynced = true)
                    }
                    return true
                }

                FoundKind.LINE_SYNCED -> {
                    if (heldLineSynced == null) {
                        heldLineSynced = RemoteLyricsResult.Found(providerName, result.raw, synced = true)
                    }
                    if (!preferWordSynced) return true
                }

                FoundKind.UNSYNCED -> if (heldUnsynced == null) {
                    heldUnsynced = RemoteLyricsResult.Found(providerName, result.raw, synced = false)
                }

                FoundKind.UNPARSEABLE -> nonNotFoundCount++ // not a usable result, but not an absence either
            }

            LyricsFetchResult.NotFound -> notFoundCount++
            is LyricsFetchResult.Failed -> nonNotFoundCount++
        }
        return false
    }

    /** Best result from the outcomes offered so far. */
    fun result(): RemoteLyricsResult = when {
        adoptedWordSynced != null -> adoptedWordSynced as RemoteLyricsResult.Found
        heldLineSynced != null -> heldLineSynced as RemoteLyricsResult.Found
        heldUnsynced != null -> heldUnsynced as RemoteLyricsResult.Found
        nonNotFoundCount == 0 && notFoundCount == enabledCount -> RemoteLyricsResult.DefinitiveNotFound
        else -> RemoteLyricsResult.Indeterminate
    }
}
