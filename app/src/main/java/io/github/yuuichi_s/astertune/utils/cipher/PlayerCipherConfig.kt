/*
 * Copyright (C) 2026 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.cipher

import android.os.SystemClock
import android.util.Log
import io.github.yuuichi_s.astertune.App
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import org.json.JSONObject
import java.io.File

/**
 * Signature deobfuscation config for one player.js, looked up by its hash and used by [CipherWebView].
 *
 * @property sigFuncName name of the deobfuscation function
 * @property sigConstantArgs constants placed before the signature, so the call is
 *   `sigFuncName(sigConstantArgs..., sig)`
 * @property nClass the player's URL builder class used to apply the n-transform to the `n`
 *   throttling parameter
 */
data class PlayerCipherConfig(
    val sigFuncName: String,
    val sigConstantArgs: List<Int>,
    val nClass: String,
)

/**
 * Provides player.js cipher configs keyed by the 8-hex player hash (aliases included).
 *
 * The data is `player_configs.json` from ZemerTeam/zemer-cipher
 * (https://github.com/ZemerTeam/zemer-cipher, GPL-3.0), whose upstream validates each entry against
 * the live CDN before shipping it.
 *
 * A copy is bundled in the assets so playback works offline and on a fresh install, but YouTube
 * rotates player.js every few days, which makes any bundled snapshot go stale within weeks. So the
 * bundle is only the floor: when a player turns up that no known config covers, [refreshFromRemote]
 * pulls the current set from upstream and caches it in the app's files dir. Both sources are merged
 * (remote wins on conflicts), so a truncated or pruned remote file can never lose bundled entries.
 */
object PlayerCipherConfigStore {

    private const val TAG = "PlayerCipherConfig"
    private const val ASSET_NAME = "player_configs.json"
    private const val CACHE_NAME = "player_configs.json"
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"

    /** Only this schema is understood; anything else is ignored so a future format cannot corrupt the map. */
    private const val SCHEMA_VERSION = 1

    /** Sanity cap on how much of the response body is read into memory; the real file is tens of KB. */
    private const val MAX_CONFIG_BYTES = 4L * 1024 * 1024

    // sigFuncName and nClass are embedded directly in JavaScript source, so their character sets are restricted.
    private val JS_IDENTIFIER = Regex("^[A-Za-z_$][A-Za-z0-9_$]*$")

    /**
     * Minimum time between network attempts. A player that stays unknown (upstream has not published
     * it yet) must not cause a fetch for every played song.
     */
    private const val MIN_REFRESH_INTERVAL_MS = 15 * 60 * 1000L

    @Volatile
    private var loaded: Map<String, PlayerCipherConfig>? = null

    private val refreshMutex = Mutex()

    @Volatile
    private var lastRefreshAtMs = 0L

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .build()
    }

    fun get(playerHash: String?): PlayerCipherConfig? = playerHash?.let { current()[it] }

    /** All known player hashes, aliases included. For diagnostics only. */
    fun knownHashes(): Set<String> = current().keys

    /**
     * Fetches the current config set from upstream, caches it and merges it into the map.
     *
     * Rate limited to one network attempt per [MIN_REFRESH_INTERVAL_MS]; call it when a player hash
     * misses, not speculatively.
     *
     * @return true if the map gained hashes it did not have before
     */
    suspend fun refreshFromRemote(): Boolean = refreshMutex.withLock {
        val now = SystemClock.elapsedRealtime()
        val sinceLast = now - lastRefreshAtMs
        if (lastRefreshAtMs != 0L && sinceLast < MIN_REFRESH_INTERVAL_MS) {
            Log.d(TAG, "Skipping remote refresh, last attempt ${sinceLast / 1000}s ago")
            return@withLock false
        }
        lastRefreshAtMs = now

        val text = withContext(Dispatchers.IO) { download() } ?: return@withLock false
        val remote = runCatching { parse(text) }.getOrElse {
            Log.e(TAG, "Could not parse remote configs", it)
            emptyMap()
        }
        if (remote.isEmpty()) {
            Log.w(TAG, "Remote configs held no usable entries, keeping current map")
            return@withLock false
        }

        writeCache(text)
        val added = merge(remote)
        Log.i(TAG, "Refreshed player cipher configs: ${remote.size} remote entries, ${added.size} new")
        added.isNotEmpty()
    }

    @Synchronized
    private fun current(): Map<String, PlayerCipherConfig> = loaded ?: load().also { loaded = it }

    /** Folds [remote] into the live map and returns the hashes that were not known before. */
    @Synchronized
    private fun merge(remote: Map<String, PlayerCipherConfig>): Set<String> {
        val before = current()
        loaded = before + remote
        return remote.keys - before.keys
    }

    private fun load(): Map<String, PlayerCipherConfig> {
        val bundled = readAsset()
        val cached = readCache()
        if (cached.isEmpty()) {
            Log.d(TAG, "Loaded ${bundled.size} player cipher configs (bundled only)")
            return bundled
        }
        // cached entries win, bundled ones are kept as a floor
        val merged = bundled + cached
        Log.d(TAG, "Loaded ${merged.size} player cipher configs " +
                "(${bundled.size} bundled, ${cached.size} cached)")
        return merged
    }

    private fun readAsset(): Map<String, PlayerCipherConfig> = try {
        val text = App.instance.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        parse(text)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load $ASSET_NAME", e)
        emptyMap()
    }

    private fun readCache(): Map<String, PlayerCipherConfig> = try {
        val file = File(App.instance.filesDir, CACHE_NAME)
        if (file.exists()) parse(file.readText()) else emptyMap()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read cached configs", e)
        emptyMap()
    }

    // Written via a temp file so a kill mid-write cannot leave a half-file that shadows the bundle.
    private fun writeCache(text: String) {
        try {
            val dir = App.instance.filesDir
            val target = File(dir, CACHE_NAME)
            val tmp = File(dir, "$CACHE_NAME.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    Log.w(TAG, "Could not move cached configs into place")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache configs", e)
        }
    }

    private fun download(): String? = try {
        val request = Request.Builder().url(REMOTE_URL).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Config fetch failed: HTTP ${response.code}")
                null
            } else {
                val body = response.body
                when {
                    body == null -> null
                    exceedsLimit(body.source(), MAX_CONFIG_BYTES) -> {
                        Log.e(TAG, "Config fetch rejected: over $MAX_CONFIG_BYTES bytes")
                        null
                    }
                    else -> body.string()
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Config fetch failed", e)
        null
    }

    private fun parse(text: String): Map<String, PlayerCipherConfig> {
        val root = JSONObject(text)
        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion != SCHEMA_VERSION) {
            Log.w(TAG, "Ignoring configs with unsupported schemaVersion $schemaVersion")
            return emptyMap()
        }
        val players = root.getJSONObject("players")
        val result = mutableMapOf<String, PlayerCipherConfig>()
        players.keys().forEach { hash ->
            val entry = players.getJSONObject(hash)
            val config = parseEntry(entry) ?: return@forEach
            result[hash] = config
            entry.optJSONArray("aliases")?.let { aliases ->
                for (i in 0 until aliases.length()) {
                    result[aliases.getString(i)] = config
                }
            }
        }
        return result
    }

    private fun parseEntry(entry: JSONObject): PlayerCipherConfig? =
        parseConfig(entry.optString("sig"), entry.optString("nClass"))

    // sig is a `name(int,int,INPUT)` call; returns null on any malformed field so one bad entry can't break the map.
    internal fun parseConfig(sig: String, nClass: String): PlayerCipherConfig? {
        if (sig.isEmpty() || !JS_IDENTIFIER.matches(nClass)) return null

        val open = sig.indexOf('(')
        if (open <= 0 || !sig.endsWith(")")) return null
        val funcName = sig.substring(0, open)
        if (!JS_IDENTIFIER.matches(funcName)) return null

        val args = sig.substring(open + 1, sig.length - 1).split(",").map { it.trim() }
        if (args.lastOrNull() != "INPUT") return null
        val constants = args.dropLast(1).map { it.toIntOrNull() ?: return null }
        if (constants.isEmpty()) return null

        return PlayerCipherConfig(funcName, constants, nClass)
    }

    /** True when [source] holds more than [maxBytes], without reading the whole body. */
    internal fun exceedsLimit(source: BufferedSource, maxBytes: Long): Boolean {
        source.request(maxBytes + 1)
        return source.buffer.size > maxBytes
    }
}
