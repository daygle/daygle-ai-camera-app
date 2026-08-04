package com.daygle.aicamera.ui.theme

import android.app.Activity
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
import com.daygle.aicamera.data.AppPreferencesStore
import com.daygle.aicamera.data.ThemeMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

private val Accent = Color(0xFF00A3FF)
private val AccentDark = Color(0xFF0084D1)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    secondary = Color(0xFF30363D),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    onBackground = Color(0xFFF0F6FC),
    onSurface = Color(0xFFF0F6FC),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFF85149),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    secondary = Color(0xFF30363D),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EDF0),
    onBackground = Color(0xFF1F2328),
    onSurface = Color(0xFF1F2328),
    onSurfaceVariant = Color(0xFF656D76),
    error = Color(0xFFCF222E),
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThemeEntryPoint {
    fun appPreferences(): AppPreferencesStore
}

@Composable
fun DaygleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val effectiveDark = if (view.isInEditMode) darkTheme else {
        val entryPoint = EntryPointAccessors.fromApplication(
            view.context.applicationContext,
            ThemeEntryPoint::class.java
        )
        val mode = runBlocking { entryPoint.appPreferences().currentThemeMode() }
        when (mode) {
            ThemeMode.SYSTEM -> darkTheme
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    }
    val colors = if (effectiveDark) DarkColors else LightColors
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDark
        }
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
