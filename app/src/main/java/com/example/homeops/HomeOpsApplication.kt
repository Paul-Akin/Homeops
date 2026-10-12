package com.example.homeops

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.homeops.notify.NotificationChannels
import com.example.homeops.work.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * `@HiltAndroidApp` generates the app-wide dependency container. Registered in AndroidManifest.xml.
 *
 * Implementing Configuration.Provider hands WorkManager Hilt's worker factory, so workers can have
 * injected constructors. (The manifest disables WorkManager's default auto-initializer for this.)
 */
@HiltAndroidApp
class HomeOpsApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var reminderScheduler: ReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate() // Hilt injects the @Inject fields here.
        NotificationChannels.createAll(this)
        reminderScheduler.ensureDailyRecompute()
        appScope.launch { reminderScheduler.rescheduleDigest() }
    }
}
