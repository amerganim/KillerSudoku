package com.ganim.killersudoku.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** The app theme. Structure lifted from Nonogram; the palette in [Color.kt] is Killer's own. */

val LocalBoardColors = staticCompositionLocalOf { LightBoardColors }

/** True when the player has asked the system to reduce animation (ANIMATOR_DURATION_SCALE 0). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Animation durations. All under 300ms, and going through [duration] is what makes the
 * reduce-motion setting apply everywhere at once.
 */
object Motion {
    const val QUICK = 140
    const val STANDARD = 260
    const val MAXIMUM = 300

    fun duration(base: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else base
}

@Composable
fun KillerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    // The system picks status-bar icon colour from this flag, not from what is behind
    // them; without it the light theme gets white icons on paper.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalBoardColors provides if (darkTheme) DarkBoardColors else LightBoardColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
