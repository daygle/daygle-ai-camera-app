package com.daygle.aicamera.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

private val Accent = Color(0xFF00A3FF)
private val AccentDark = Color(0xFF0084D1)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFC2E7FF),
    secondary = Color(0xFF30363D),
    onSecondary = Color(0xFFE6EDF0),
    secondaryContainer = Color(0xFF21262D),
    onSecondaryContainer = Color(0xFFC9D1D9),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLow = Color(0xFF161B22),
    surfaceContainer = Color(0xFF21262D),
    surfaceContainerHigh = Color(0xFF262C33),
    surfaceContainerHighest = Color(0xFF30363D),
    outline = Color(0xFF30363D),
    error = Color(0xFFF85149),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF30363D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EDF0),
    onSecondaryContainer = Color(0xFF1F2328),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFE6EDF0),
    onSurfaceVariant = Color(0xFF656D76),
    surfaceContainerLow = Color(0xFFF0F2F5),
    surfaceContainer = Color(0xFFE6EDF0),
    surfaceContainerHigh = Color(0xFFDDE3E6),
    surfaceContainerHighest = Color(0xFFD1D7DA),
    outline = Color(0xFFD1D7DA),
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
        val entryPoint = remember {
            EntryPointAccessors.fromApplication(
                view.context.applicationContext,
                ThemeEntryPoint::class.java
            )
        }
        val themeMode by entryPoint.appPreferences().themeMode.collectAsState(initial = ThemeMode.SYSTEM)
        when (themeMode) {
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
