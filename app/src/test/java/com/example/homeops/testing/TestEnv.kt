package com.example.homeops.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.homeops.data.db.HomeOpsDatabase
import com.example.homeops.data.prefs.SettingsRepository
import com.example.homeops.data.repo.HomeRepository
import com.example.homeops.data.repo.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** A Clock the test can move: `clock.now = ...`. */
class MutableClock(
    var now: Instant,
    private val zoneId: ZoneId = ZoneId.of("America/New_York"),
) : Clock() {
    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}

/** In-memory database, repositories, and a throwaway DataStore file for Robolectric tests. */
class TestEnv(
    start: Instant,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val clock = MutableClock(start)
    val db: HomeOpsDatabase =
        Room.inMemoryDatabaseBuilder(context, HomeOpsDatabase::class.java).allowMainThreadQueries().build()
    val home = HomeRepository(db.roomDao(), db.zoneDao())
    val tasks = TaskRepository(db, db.taskDao(), db.completionDao(), db.snoozeDao(), clock)

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // DataStore allows one instance per file per process, so each env gets a fresh file.
    private val storeFile = File(context.filesDir, "test-${UUID.randomUUID()}.preferences_pb")
    val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope) { storeFile })

    fun close() {
        db.close()
        storeScope.cancel()
        storeFile.delete()
    }
}
