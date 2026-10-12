package com.example.homeops.work

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.homeops.testing.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

/** Scheduling wiring: WorkManager gets (or loses) the digest job as settings change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderSchedulerTest {
    private val zone = java.time.ZoneId.of("America/New_York")
    private val monday10am = ZonedDateTime.of(2026, 10, 12, 10, 0, 0, 0, zone)
    private lateinit var env: TestEnv
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: ReminderScheduler

    @Before
    fun setUp() {
        env = TestEnv(monday10am.toInstant())
        val config =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(env.context, config)
        workManager = WorkManager.getInstance(env.context)
        scheduler = ReminderScheduler({ workManager }, env.tasks, env.settings, env.clock)
    }

    @After
    fun tearDown() = env.close()

    private fun digestStates() =
        workManager.getWorkInfosForUniqueWork(ReminderScheduler.WORK_DIGEST).get().map { it.state }

    @Test
    fun remindersOffMeansNoJobAndNoPreview() =
        runTest {
            scheduler.rescheduleDigest()
            assertNull(scheduler.preview())
            assertTrue(digestStates().none { it == WorkInfo.State.ENQUEUED })
        }

    @Test
    fun enablingSchedulesTodaysDigestAtTheConfiguredTime() =
        runTest {
            env.settings.updateReminderSettings { it.copy(enabled = true, defaultMinute = 19 * 60) }
            scheduler.rescheduleDigest()

            assertEquals(listOf(WorkInfo.State.ENQUEUED), digestStates())
            val (at, time) = scheduler.preview()!!
            assertEquals(monday10am.withHour(19), at)
            assertEquals(false, time.learned)
        }

    @Test
    fun afterTodaysDigestTheNextOneIsTomorrow() =
        runTest {
            env.settings.updateReminderSettings { it.copy(enabled = true) }
            env.settings.markDigestSent(monday10am.toLocalDate())
            assertEquals(monday10am.plusDays(1).withHour(18), scheduler.preview()!!.first)
        }

    @Test
    fun disablingCancelsThePendingDigest() =
        runTest {
            env.settings.updateReminderSettings { it.copy(enabled = true) }
            scheduler.rescheduleDigest()
            env.settings.updateReminderSettings { it.copy(enabled = false) }
            scheduler.rescheduleDigest()
            assertEquals(listOf(WorkInfo.State.CANCELLED), digestStates())
        }
}
