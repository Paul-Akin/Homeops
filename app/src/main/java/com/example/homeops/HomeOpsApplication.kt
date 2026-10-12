package com.example.homeops

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** `@HiltAndroidApp` generates the app-wide dependency container. Registered in AndroidManifest.xml. */
@HiltAndroidApp
class HomeOpsApplication : Application()
