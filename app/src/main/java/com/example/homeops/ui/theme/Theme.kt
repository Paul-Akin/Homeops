package com.example.homeops.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Slate = Color(0xFF1F5F8B)
private val SlateLight = Color(0xFF9CCBF0)
private val Teal = Color(0xFF2E7D6B)
private val TealLight = Color(0xFF8FD3C1)

private val LightColors = lightColorScheme(primary = Slate, secondary = Teal)
private val DarkColors = darkColorScheme(primary = SlateLight, secondary = TealLight)

/**
 * App theme. On Android 12+ it follows the wallpaper ("dynamic color"); older devices get the
 * fixed palette above. Full dark-theme polish is an M8 item.
 */
@Composable
fun HomeOpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Status colors for due-ness indicators, shared across screens. */
object StatusColors {
    val OnTrack = Color(0xFF2E7D32)
    val Due = Color(0xFFEF6C00)
    val Overdue = Color(0xFFC62828)
}
