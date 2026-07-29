package com.daygle.aicamera.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Accent = Color(0xFF3DDC97)
private val AccentDark = Color(0xFF1FB877)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF04150E),
    secondary = Color(0xFF7FD8FF),
    background = Color(0xFF0B1220),
    surface = Color(0xFF131C2B),
    surfaceVariant = Color(0xFF1C2740),
    onBackground = Color(0xFFE6ECF5),
    onSurface = Color(0xFFE6ECF5),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    secondary = Color(0xFF0B72A8),
    background = Color(0xFFF6F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7ECF3),
    error = Color(0xFFC62828),
)

@Composable
fun DaygleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
