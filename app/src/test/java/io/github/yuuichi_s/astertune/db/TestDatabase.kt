package io.github.yuuichi_s.astertune.db

import androidx.room.Room
import org.robolectric.RuntimeEnvironment

internal fun createEmptyTestDatabase(): MusicDatabase = MusicDatabase(
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InternalDatabase::class.java)
        .allowMainThreadQueries()
        .build()
)
