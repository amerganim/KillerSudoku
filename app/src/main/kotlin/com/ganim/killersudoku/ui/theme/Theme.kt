package com.ganim.killersudoku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Board-specific colours that Material's scheme has no slot for. */
@Immutable
class BoardColors(
    val background: Color,
    val gridThin: Color,
    val gridThick: Color,
    val cage: Color,
    val cageSum: Color,
    val given: Color,
    val entered: Color,
    val error: Color,
    val errorFill: Color,
    val note: Color,
    val selected: Color,
    val related: Color,
    val sameDigit: Color,
    val cageHighlight: Color,
    val hint: Color,
)

private val LightBoard = BoardColors(
    background = Color(0xFFFFFDF8),
    gridThin = Color(0xFFD5D0C6),
    gridThick = Color(0xFF3D3A35),
    cage = Color(0xFF6B665E),
    cageSum = Color(0xFF3D3A35),
    given = Color(0xFF1F1D1A),
    entered = Color(0xFF2F5FA8),
    error = Color(0xFFC62828),
    errorFill = Color(0x22C62828),
    note = Color(0xFF6B665E),
    selected = Color(0xFFBFD5F5),
    related = Color(0xFFEFEAE0),
    sameDigit = Color(0xFFD9E5F7),
    cageHighlight = Color(0xFFF6EFD9),
    hint = Color(0xFFFFE08A),
)

private val DarkBoard = BoardColors(
    background = Color(0xFF1C1B1A),
    gridThin = Color(0xFF3A3834),
    gridThick = Color(0xFFB9B3A8),
    cage = Color(0xFF9C968B),
    cageSum = Color(0xFFD8D2C6),
    given = Color(0xFFECE6DA),
    entered = Color(0xFF8DB4F0),
    error = Color(0xFFFF7B72),
    errorFill = Color(0x33FF7B72),
    note = Color(0xFFA8A296),
    selected = Color(0xFF2E4A73),
    related = Color(0xFF282623),
    sameDigit = Color(0xFF263850),
    cageHighlight = Color(0xFF2F2B21),
    hint = Color(0xFF6B5410),
)

val LocalBoardColors = staticCompositionLocalOf { LightBoard }

@Composable
fun KillerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(primary = Color(0xFF8DB4F0), background = Color(0xFF141312), surface = Color(0xFF141312))
    } else {
        lightColorScheme(primary = Color(0xFF2F5FA8), background = Color(0xFFF7F4EE), surface = Color(0xFFF7F4EE))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBoardColors provides if (dark) DarkBoard else LightBoard) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
