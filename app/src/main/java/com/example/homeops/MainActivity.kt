package com.example.homeops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.homeops.ui.nav.HomeOpsNavHost
import com.example.homeops.ui.theme.HomeOpsTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity; every screen is a Compose destination inside [HomeOpsNavHost]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeOpsTheme {
                HomeOpsNavHost()
            }
        }
    }
}
