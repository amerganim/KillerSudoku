package com.ganim.killersudoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ganim.killersudoku.data.Settings
import com.ganim.killersudoku.ui.components.GameIcon
import com.ganim.killersudoku.ui.components.Glyph
import com.ganim.killersudoku.ui.components.Meter
import com.ganim.killersudoku.ui.components.Panel
import com.ganim.killersudoku.ui.theme.LocalBoardColors

/**
 * Settings (build plan 6.1).
 *
 * Short on purpose. Every switch is one a plan asks for: auto-candidates as a setting
 * (killer plan 6), haptics disableable and dark mode a real choice (inherited from
 * Nonogram).
 *
 * How to play leads, because someone who opens Settings mid-puzzle is usually looking
 * for exactly that.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    completedCount: Int,
    totalCount: Int,
    dailyCount: Int,
    onHapticsChanged: (Boolean) -> Unit,
    onAutoNotesChanged: (Boolean) -> Unit,
    onMorePuzzles: (CrossPromo) -> Unit,
    store: StoreUi,
    onBuy: (String) -> Unit,
    onThemeChanged: (Boolean?) -> Unit,
    onHowToPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.boardBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = colors.clueText)

        ActionRow(
            glyph = Glyph.SPARK,
            tint = colors.info,
            title = "How to play",
            subtitle = "The rules, and the rule of 45 that unlocks them.",
            onClick = onHowToPlay,
        )

        SwitchRow(
            title = "Auto notes",
            subtitle = "Fill and update pencil marks for you. Off keeps the puzzle yours.",
            checked = settings.autoNotes,
            onChange = onAutoNotesChanged,
        )

        SwitchRow(
            title = "Haptic feedback",
            subtitle = "A tick on every digit you place.",
            checked = settings.hapticsEnabled,
            onChange = onHapticsChanged,
        )

        Panel(Modifier.fillMaxWidth()) {
            Text("Theme", style = MaterialTheme.typography.titleSmall, color = colors.clueText)
            Box(Modifier.height(10.dp))
            val options = listOf<Pair<String, Boolean?>>(
                "System" to null,
                "Light" to false,
                "Dark" to true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                options.forEach { (label, value) ->
                    Segment(
                        label = label,
                        selected = settings.darkThemeOverride == value,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeChanged(value) },
                    )
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "Puzzles solved",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.clueText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // Counted, not hard-coded: it moves whenever the pack is rebuilt.
                    "%,d".format(completedCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.accent,
                )
                Text(
                    " / %,d".format(totalCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            Box(Modifier.height(10.dp))
            Meter(
                fraction = completedCount.toFloat() / totalCount.coerceAtLeast(1),
                tint = colors.accent,
            )
            Box(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Tally(dailyCount, "daily", colors.success)
            }
        }

        StoreSection(store, onBuy)

        // Cross-promotion (puzzle-engine-core 10): a hardcoded list, not an SDK.
        Text("More puzzles", style = MaterialTheme.typography.titleSmall, color = colors.textMuted)
        CrossPromo.entries.forEach { app ->
            ActionRow(
                glyph = Glyph.GRID,
                tint = colors.accent,
                title = app.title,
                subtitle = app.pitch,
                onClick = { onMorePuzzles(app) },
            )
        }
    }
}

/** What the store section shows. Prices are null until Play returns product details. */
data class StoreUi(
    val adFree: Boolean = false,
    val pending: Boolean = false,
    val hintsRemaining: Int = 0,
    val removeAdsPrice: String? = null,
    val hintPackPrice: String? = null,
)

/**
 * The two products. Nonogram shipped its billing with no way into it - nothing called
 * `launchPurchase` - so this section exists on purpose, and each row is only tappable
 * once Play has returned a price for it. A row that opens nothing looks broken.
 */
@Composable
private fun StoreSection(store: StoreUi, onBuy: (String) -> Unit) {
    val colors = LocalBoardColors.current
    Text("Store", style = MaterialTheme.typography.titleSmall, color = colors.textMuted)
    if (store.pending) {
        // Cash and carrier billing complete later; say so rather than look failed.
        Panel(Modifier.fillMaxWidth(), tint = colors.info) {
            Text("Payment pending", style = MaterialTheme.typography.titleSmall, color = colors.clueText)
            Text(
                "Google is still confirming a purchase. It will unlock here by itself.",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textMuted,
            )
        }
    }
    if (store.adFree) {
        Panel(Modifier.fillMaxWidth(), tint = colors.success) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameIcon(Glyph.CHECK, colors.success, size = 20.dp)
                Text("Ads removed. Thank you.", style = MaterialTheme.typography.titleSmall, color = colors.clueText)
            }
        }
    } else {
        StoreRow(
            title = "Remove ads",
            subtitle = "No more videos between puzzles. Optional videos for hints stay available.",
            price = store.removeAdsPrice,
            onClick = { onBuy(com.ganim.killersudoku.monetize.Sku.REMOVE_ADS) },
        )
    }
    StoreRow(
        title = "25 hints",
        subtitle = "You have ${store.hintsRemaining}. Three free ones refill every day.",
        price = store.hintPackPrice,
        onClick = { onBuy(com.ganim.killersudoku.monetize.Sku.HINT_PACK_25) },
    )
}

@Composable
private fun StoreRow(title: String, subtitle: String, price: String?, onClick: () -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), onClick = if (price != null) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.clueText)
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
            }
            Box(Modifier.width(10.dp))
            Text(
                price ?: "…",
                style = MaterialTheme.typography.titleMedium,
                color = if (price != null) colors.accent else colors.textMuted,
            )
        }
    }
}

/** The other apps in the portfolio. Opening one goes to its Play listing. */
enum class CrossPromo(val title: String, val pitch: String, val packageName: String) {
    NONOGRAM("Daily Nonogram", "Picture logic puzzles. Also no guessing, ever.", "com.ganim.nonogram"),
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.clueText)
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.success,
                    checkedBorderColor = colors.success,
                    uncheckedThumbColor = colors.textMuted,
                    uncheckedTrackColor = colors.surface,
                    uncheckedBorderColor = colors.stroke,
                ),
            )
        }
    }
}

@Composable
private fun Tally(value: Int, label: String, tint: Color) {
    val colors = LocalBoardColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.labelLarge, color = tint)
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
    }
}

/** A whole card that is one tap: a glyph, a title, a line of why, and an arrow. */
@Composable
private fun ActionRow(
    glyph: Glyph,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    Panel(Modifier.fillMaxWidth(), tint = tint, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint.copy(alpha = BADGE_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                GameIcon(glyph, tint, size = 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.clueText)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
            }
            GameIcon(Glyph.ARROW, tint, size = 20.dp)
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) colors.accentFill else Color.Transparent)
            .border(1.dp, if (selected) colors.accentDeep else colors.stroke, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onAccentFill else colors.textMuted,
        )
    }
}

private const val BADGE_ALPHA = 0.22f
