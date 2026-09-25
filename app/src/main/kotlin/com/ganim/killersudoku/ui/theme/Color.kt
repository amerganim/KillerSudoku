package com.ganim.killersudoku.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app draws with.
 *
 * ## Graphite & Tangerine
 *
 * The portfolio shares structure and gives each app its own identity (puzzle-engine-core
 * 9). Nonogram owns indigo and gold, so Killer Sudoku is graphite and tangerine: cooler
 * ground, warmer accent, recognisably a sibling and not a copy.
 *
 * It keeps Nonogram's rule of **one colour per meaning**:
 *
 * - **accent** (tangerine) - the thing to press, and the selected cell. Nothing else.
 * - **cellMistake** (rose) - what costs you something: a wrong digit, a clash, a life.
 * - **success** (mint) - confirmed and safe: a solved day, a finished puzzle.
 * - **info** (sky) - being explained: a hint, and the cage you are working in.
 *
 * ## The board stays quiet
 *
 * A killer board carries more marks than a nonogram: digits, pencil marks, dashed cage
 * outlines and cage sums, all in one cell. So the board uses ink and muted ink only, and
 * every highlight is a low-alpha wash rather than a fill. There are no givens in killer
 * sudoku, so entered digits need no second colour - they are simply ink, and only a wrong
 * one changes colour.
 *
 * ## The two rules this file lives under
 *
 * The only place a colour literal may appear (`ThemePurityTest`), and every pair below is
 * WCAG-checked (`ThemeContrastTest`). Light accents are darker versions of the same hues,
 * because tangerine on white is 2:1 and unreadable as text.
 */

// --- light: warm paper, graphite ink ------------------------------------------------------

private val PaperLight = Color(0xFFF6F3EE)
private val SurfaceLight = Color(0xFFFFFFFF)
private val RaisedLight = Color(0xFFFFFFFF)
private val StrokeLight = Color(0xFFE4DDD2)
private val InkLight = Color(0xFF1C2230)
private val InkMutedLight = Color(0xFF4F5768)
private val AccentLight = Color(0xFFB34A00)
private val AccentFillLight = Color(0xFFFF9A4D)
private val AccentDeepLight = Color(0xFFA34300)
private val OnAccentLight = Color(0xFFFFFFFF)
private val OnAccentFillLight = Color(0xFF1C2230)
private val SuccessLight = Color(0xFF12795A)
private val InfoLight = Color(0xFF0B6A99)
private val MistakeLight = Color(0xFFAE1236)
private val GridMinorLight = Color(0xFFDDD6CA)
private val GridMajorLight = Color(0xFF3A4152)
private val CageLight = Color(0xFF6B7385)

// --- dark: graphite, tangerine ------------------------------------------------------------
// Authored outright, not derived by inverting light: dark is the default for many puzzle
// players, and an inverted palette looks washed out.

private val PaperDark = Color(0xFF10151F)
private val SurfaceDark = Color(0xFF1A2130)
private val RaisedDark = Color(0xFF232C3F)
private val StrokeDark = Color(0xFF323D55)
private val InkDark = Color(0xFFF2F4F8)
private val InkMutedDark = Color(0xFFB3BDD2)
private val AccentDark = Color(0xFFFF9A4D)
private val AccentDeepDark = Color(0xFFC4631E)
private val OnAccentDark = Color(0xFF10151F)
private val SuccessDark = Color(0xFF4FD69C)
private val InfoDark = Color(0xFF6CC3FF)
private val MistakeDark = Color(0xFFFF8A9C)
private val GridMinorDark = Color(0xFF2C364A)
private val GridMajorDark = Color(0xFF8C98B3)
private val CageDark = Color(0xFF8E9AB4)

/**
 * Colours Material's scheme has no slot for. Names match Nonogram's where the meaning is
 * the same (`clueText` is the primary ink, `highlight` the row/column wash), so the shared
 * components read identically in both apps.
 */
@Immutable
data class BoardColors(
    /** The page. */
    val boardBackground: Color,
    val surface: Color,
    /** A card above [surface]. */
    val raised: Color,
    /** Hairline around a card. */
    val stroke: Color,
    /** A board cell. */
    val cell: Color,
    val gridLine: Color,
    val gridLineMajor: Color,
    /** Dashed cage outline. Held to 3:1 against a cell: it is how the puzzle is read. */
    val cage: Color,
    /** Primary ink: digits, cage sums, titles. */
    val clueText: Color,
    /** Pencil marks and secondary text. */
    val textMuted: Color,
    val cellMistake: Color,
    /**
     * The selected cell's wash. Deliberately light: the selection is carried by an
     * accent ring ([accent]), because a wash strong enough to find at a glance took a
     * wrong digit on it down to 2.9:1 on dark.
     */
    val selected: Color,
    /** Row, column and box of the selection. Kept subtle. */
    val highlight: Color,
    /** The cage the selection sits in. */
    val cageHighlight: Color,
    /** Other cells holding the selected digit. */
    val sameDigit: Color,
    /** A clash, washed under the cell. */
    val mistakeWash: Color,
    /** The cell a hint is about. */
    val hintWash: Color,
    val accent: Color,
    val accentFill: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val onAccentFill: Color,
    val success: Color,
    val info: Color,
)

val LightBoardColors = BoardColors(
    boardBackground = PaperLight,
    surface = SurfaceLight,
    raised = RaisedLight,
    stroke = StrokeLight,
    cell = SurfaceLight,
    gridLine = GridMinorLight,
    gridLineMajor = GridMajorLight,
    cage = CageLight,
    clueText = InkLight,
    textMuted = InkMutedLight,
    cellMistake = MistakeLight,
    selected = AccentFillLight.copy(alpha = 0.30f),
    highlight = InfoLight.copy(alpha = 0.07f),
    cageHighlight = InfoLight.copy(alpha = 0.15f),
    sameDigit = AccentFillLight.copy(alpha = 0.20f),
    mistakeWash = MistakeLight.copy(alpha = 0.14f),
    hintWash = InfoLight.copy(alpha = 0.16f),
    accent = AccentLight,
    accentFill = AccentFillLight,
    accentDeep = AccentDeepLight,
    onAccent = OnAccentLight,
    onAccentFill = OnAccentFillLight,
    success = SuccessLight,
    info = InfoLight,
)

val DarkBoardColors = BoardColors(
    boardBackground = PaperDark,
    surface = SurfaceDark,
    raised = RaisedDark,
    stroke = StrokeDark,
    cell = SurfaceDark,
    gridLine = GridMinorDark,
    gridLineMajor = GridMajorDark,
    cage = CageDark,
    clueText = InkDark,
    textMuted = InkMutedDark,
    cellMistake = MistakeDark,
    selected = AccentDark.copy(alpha = 0.20f),
    highlight = InfoDark.copy(alpha = 0.06f),
    cageHighlight = InfoDark.copy(alpha = 0.12f),
    sameDigit = AccentDark.copy(alpha = 0.13f),
    mistakeWash = MistakeDark.copy(alpha = 0.14f),
    hintWash = InfoDark.copy(alpha = 0.18f),
    accent = AccentDark,
    accentFill = AccentDark,
    accentDeep = AccentDeepDark,
    onAccent = OnAccentDark,
    onAccentFill = OnAccentDark,
    success = SuccessDark,
    info = InfoDark,
)

internal val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    secondary = InkMutedLight,
    onSecondary = SurfaceLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = PaperLight,
    onSurfaceVariant = InkMutedLight,
    background = PaperLight,
    onBackground = InkLight,
    error = MistakeLight,
    onError = SurfaceLight,
    outline = StrokeLight,
)

internal val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    secondary = InkMutedDark,
    onSecondary = PaperDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = RaisedDark,
    onSurfaceVariant = InkMutedDark,
    background = PaperDark,
    onBackground = InkDark,
    error = MistakeDark,
    onError = PaperDark,
    outline = StrokeDark,
)

/**
 * Pairs the contrast test checks. Text needs 4.5:1, graphical objects 3:1.
 *
 * Board text is checked against the cell *with each wash composited over it*, because
 * a digit in the selected cell sits on the selection wash, not on the bare cell - that is
 * where a too-strong highlight would quietly eat the contrast.
 */
internal fun contrastPairs(colors: BoardColors): List<ContrastPair> {
    val washes = listOf(
        "cell" to colors.cell,
        "selected" to colors.selected.over(colors.cell),
        "cage highlight" to colors.cageHighlight.over(colors.cell),
        "same digit" to colors.sameDigit.over(colors.cell),
        "clash" to colors.mistakeWash.over(colors.cell),
        "hint" to colors.hintWash.over(colors.cell),
    )
    val board = washes.flatMap { (name, ground) ->
        listOf(
            ContrastPair("digit on $name", colors.clueText, ground, 4.5),
            ContrastPair("wrong digit on $name", colors.cellMistake, ground, 4.5),
            // Pencil marks are small; they are held to the text ratio, not the graphic one.
            ContrastPair("pencil mark on $name", colors.textMuted, ground, 4.5),
            ContrastPair("cage outline on $name", colors.cage, ground, 3.0),
        )
    }
    return board + listOf(
        ContrastPair("text on page", colors.clueText, colors.boardBackground, 4.5),
        ContrastPair("text on raised", colors.clueText, colors.raised, 4.5),
        ContrastPair("muted text on page", colors.textMuted, colors.boardBackground, 4.5),
        ContrastPair("muted text on surface", colors.textMuted, colors.surface, 4.5),
        ContrastPair("muted text on raised", colors.textMuted, colors.raised, 4.5),
        ContrastPair("accent on page", colors.accent, colors.boardBackground, 4.5),
        ContrastPair("accent on surface", colors.accent, colors.surface, 4.5),
        ContrastPair("accent on raised", colors.accent, colors.raised, 4.5),
        ContrastPair("text on accent", colors.onAccent, colors.accent, 4.5),
        ContrastPair("text on accent fill", colors.onAccentFill, colors.accentFill, 4.5),
        ContrastPair("accent fill edge on page", colors.accentDeep, colors.boardBackground, 3.0),
        ContrastPair("accent fill edge on surface", colors.accentDeep, colors.surface, 3.0),
        ContrastPair("mistake on page", colors.cellMistake, colors.boardBackground, 4.5),
        ContrastPair("mistake on surface", colors.cellMistake, colors.surface, 4.5),
        ContrastPair("success on page", colors.success, colors.boardBackground, 4.5),
        ContrastPair("success on surface", colors.success, colors.surface, 4.5),
        ContrastPair("text on success", colors.onAccent, colors.success, 4.5),
        ContrastPair("info on page", colors.info, colors.boardBackground, 4.5),
        ContrastPair("info on surface", colors.info, colors.surface, 4.5),
        ContrastPair("major grid line on cell", colors.gridLineMajor, colors.cell, 3.0),
    )
}

/** [this] composited over an opaque [ground]. */
internal fun Color.over(ground: Color): Color {
    if (alpha >= 1f) return this
    return Color(
        red = red * alpha + ground.red * (1 - alpha),
        green = green * alpha + ground.green * (1 - alpha),
        blue = blue * alpha + ground.blue * (1 - alpha),
        alpha = 1f,
    )
}

internal data class ContrastPair(
    val name: String,
    val foreground: Color,
    val background: Color,
    val minimumRatio: Double,
)
