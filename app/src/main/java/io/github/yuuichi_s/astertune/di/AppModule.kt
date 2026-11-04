package io.github.yuuichi_s.astertune.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.yuuichi_s.astertune.constants.MaxSongCacheSizeKey
import io.github.yuuichi_s.astertune.db.InternalDatabase
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase =
        InternalDatabase.newInstance(context)

    @Singleton
    @Provides
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class PlayerCache

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCacheSizeMb(@ApplicationContext context: Context): Int =
        context.dataStore.get(MaxSongCacheSizeKey, 0)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        @PlayerCache cacheSize: Int,
    ): SimpleCache {
        val constructor = {
            SimpleCache(
                context.filesDir.resolve("exoplayer"),
                when (cacheSize) {
                    -1 -> NoOpCacheEvictor()
                    else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
                },
                databaseProvider
            )
        }
        constructor().release()
        return constructor()
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(@ApplicationContext context: Context, databaseProvider: DatabaseProvider): SimpleCache {
        val constructor = {
            SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
        }
        constructor().release()
        return constructor()
    }
}
