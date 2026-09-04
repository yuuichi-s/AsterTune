/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import io.github.yuuichi_s.astertune.constants.AccountChannelHandleKey
import io.github.yuuichi_s.astertune.constants.AccountEmailKey
import io.github.yuuichi_s.astertune.constants.AccountImageFetchedKey
import io.github.yuuichi_s.astertune.constants.AccountImageUrlKey
import io.github.yuuichi_s.astertune.constants.AccountNameKey
import io.github.yuuichi_s.astertune.constants.ContentCountryKey
import io.github.yuuichi_s.astertune.constants.ContentLanguageKey
import io.github.yuuichi_s.astertune.constants.CountryCodeToName
import io.github.yuuichi_s.astertune.constants.DataSyncIdKey
import io.github.yuuichi_s.astertune.constants.InnerTubeCookieKey
import io.github.yuuichi_s.astertune.constants.LanguageCodeToName
import io.github.yuuichi_s.astertune.constants.MaxImageCacheSizeKey
import io.github.yuuichi_s.astertune.constants.ProxyEnabledKey
import io.github.yuuichi_s.astertune.constants.ProxyTypeKey
import io.github.yuuichi_s.astertune.constants.ProxyUrlKey
import io.github.yuuichi_s.astertune.constants.SYSTEM_DEFAULT
import io.github.yuuichi_s.astertune.constants.UseLoginForBrowse
import io.github.yuuichi_s.astertune.constants.VisitorDataKey
import io.github.yuuichi_s.astertune.extensions.toEnum
import io.github.yuuichi_s.astertune.extensions.toInetSocketAddress
import io.github.yuuichi_s.astertune.utils.CoilBitmapLoader
import io.github.yuuichi_s.astertune.utils.LocalArtworkPathKeyer
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.astertune.utils.normalizeDataSyncId
import io.github.yuuichi_s.astertune.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeLocale
import com.zionhuang.kugou.KuGou
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.Proxy
import java.util.Locale

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {
    private val TAG = App::class.simpleName.toString()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            System.setProperty("kotlinx.coroutines.debug", "on")
        }

        instance = this;

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "") // replace zh-Hant-* to zh-*
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse proxy url.", LENGTH_SHORT).show()
                reportException(e)
            }
        }

        if (dataStore[UseLoginForBrowse] != false) {
            YouTube.useLoginForBrowse = true
        }

        GlobalScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" } // Previously visitorData was sometimes saved as "null" due to a bug
                        ?: YouTube.visitorData().onFailure {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@App, "Failed to get visitorData.", LENGTH_SHORT).show()
                            }
                            reportException(it)
                        }.getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = normalizeDataSyncId(dataSyncId)
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        // Clear the rejected cookie to avoid parsing it again on startup.
                        Log.e(TAG, "Could not parse cookie. Clearing existing cookie. ${e.message}")
                        forgetAccount(this@App)
                    }
                }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheSize = dataStore[MaxImageCacheSizeKey]

        // will crash app if you set to 0 after cache starts being used
        if (cacheSize == 0) {
            return ImageLoader.Builder(this)
                .components {
                    add(CoilBitmapLoader.Factory(this@App))
                    add(LocalArtworkPathKeyer())
                }
                .crossfade(true)
                .allowHardware(false)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.3)
                        .build()
                }
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }

        return ImageLoader.Builder(this)
            .components {
                add(CoilBitmapLoader.Factory(this@App))
                add(LocalArtworkPathKeyer())
            }
            .crossfade(true)
            .allowHardware(false)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.3)
                    .build()
            }
            .diskCache(
                // Local images should bypass with DataSource.DISK
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes((cacheSize ?: 512) * 1024 * 1024L)
                    .build()
            )
            .build()
    }

    companion object {
        lateinit var instance: App
            private set

        fun forgetAccount(context: Context) {
            runBlocking {
                context.dataStore.edit { settings ->
                    settings.remove(InnerTubeCookieKey)
                    settings.remove(VisitorDataKey)
                    settings.remove(DataSyncIdKey)
                    settings.remove(AccountNameKey)
                    settings.remove(AccountEmailKey)
                    settings.remove(AccountChannelHandleKey)
                    settings.remove(AccountImageUrlKey)
                    settings.remove(AccountImageFetchedKey)
                }
            }
        }
    }
}
