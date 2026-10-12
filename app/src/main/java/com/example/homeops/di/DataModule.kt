package com.example.homeops.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.example.homeops.data.db.ALL_MIGRATIONS
import com.example.homeops.data.db.CompletionDao
import com.example.homeops.data.db.HomeOpsDatabase
import com.example.homeops.data.db.RoomDao
import com.example.homeops.data.db.SnoozeDao
import com.example.homeops.data.db.TaskDao
import com.example.homeops.data.db.ZoneDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Hilt module: tells the dependency-injection graph how to build things it can't construct
 * from an `@Inject constructor` alone (library classes like the Room database, or interfaces).
 * `SingletonComponent` + `@Singleton` means one instance for the life of the app process.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): HomeOpsDatabase =
        Room
            .databaseBuilder(context, HomeOpsDatabase::class.java, HomeOpsDatabase.NAME)
            // Every schema change adds a Migration to ALL_MIGRATIONS. Never use
            // fallbackToDestructiveMigration(): it would silently wipe the user's history.
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides fun provideRoomDao(db: HomeOpsDatabase): RoomDao = db.roomDao()

    @Provides fun provideZoneDao(db: HomeOpsDatabase): ZoneDao = db.zoneDao()

    @Provides fun provideTaskDao(db: HomeOpsDatabase): TaskDao = db.taskDao()

    @Provides fun provideCompletionDao(db: HomeOpsDatabase): CompletionDao = db.completionDao()

    @Provides fun provideSnoozeDao(db: HomeOpsDatabase): SnoozeDao = db.snoozeDao()

    /** Exactly one DataStore per file per process, hence @Singleton. */
    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    /** Injected so time-dependent logic can be tested with a fixed clock. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
