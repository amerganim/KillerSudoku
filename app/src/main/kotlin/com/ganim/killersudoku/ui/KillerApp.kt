package com.ganim.killersudoku.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ganim.killersudoku.daily.DailyScreen
import com.ganim.killersudoku.daily.DailyViewModel
import com.ganim.killersudoku.data.SavedBoard
import com.ganim.killersudoku.data.Settings
import com.ganim.killersudoku.game.GameScreen
import com.ganim.killersudoku.game.GameViewModel
import com.ganim.killersudoku.progression.LevelLadder
import com.ganim.killersudoku.progression.PlayScreen
import com.ganim.killersudoku.progression.PlayViewModel
import com.ganim.killersudoku.ui.components.GameIcon
import com.ganim.killersudoku.ui.components.Glyph
import com.ganim.killersudoku.ui.theme.KillerTheme
import com.ganim.killersudoku.ui.theme.LocalBoardColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private object Routes {
    const val PLAY = "play"
    const val DAILY = "daily"
    const val SETTINGS = "settings"
    const val HOW_TO_PLAY = "howtoplay"
    const val GAME = "game/{puzzleId}?date={date}"

    fun game(puzzleId: String, date: LocalDate?): String = "game/$puzzleId?date=${date?.toString().orEmpty()}"
}

private data class Destination(val route: String, val label: String, val glyph: Glyph)

private val destinations = listOf(
    Destination(Routes.PLAY, "Play", Glyph.STAIRS),
    Destination(Routes.DAILY, "Daily", Glyph.CALENDAR),
    Destination(Routes.SETTINGS, "Settings", Glyph.SLIDERS),
)

/**
 * Single activity, three tabs - Play (the ladder), Daily, Settings - lifted from
 * Nonogram's shell. The bottom bar hides on the game screen, where the board needs the
 * room. The active tab wears the accent outright, the same "this one" signal as the
 * primary button and an active tool.
 */
@Composable
fun KillerApp(container: AppContainer) {
    // Nullable until DataStore answers: a non-null default would report "tutorial not
    // seen" for one frame and open the walkthrough on every launch.
    val loadedSettings by container.settings.settings.collectAsState(initial = null)
    val settings = loadedSettings ?: Settings()
    val completedCount by container.progress.observeCompletedCount().collectAsState(initial = 0)
    val completedIds by container.progress.observeCompletedIds().collectAsState(initial = emptySet())
    val stats by container.progress.observeStats().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    KillerTheme(darkTheme = settings.darkThemeOverride ?: isSystemInDarkTheme()) {
        val nav = rememberNavController()

        var tutorialOffered by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(loadedSettings) {
            val known = loadedSettings ?: return@LaunchedEffect
            if (tutorialOffered) return@LaunchedEffect
            tutorialOffered = true
            if (!known.tutorialSeen) nav.navigate(Routes.HOW_TO_PLAY)
        }

        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val showBar = currentRoute in destinations.map { it.route }

        Scaffold(
            containerColor = LocalBoardColors.current.boardBackground,
            bottomBar = { if (showBar) BottomBar(currentRoute) { navigateTop(nav, it) } },
        ) { padding ->
            NavHost(nav, startDestination = Routes.PLAY, modifier = Modifier.fillMaxSize().padding(padding)) {
                composable(Routes.PLAY) {
                    val model: PlayViewModel = viewModel(factory = PlayViewModel.Factory(container.puzzles, container.progress))
                    PlayScreen(
                        viewModel = model,
                        // A ladder level records no date: finishing level 9 must not move the streak.
                        onPlay = { id -> nav.navigate(Routes.game(id, null)) },
                        onHowToPlay = { nav.navigate(Routes.HOW_TO_PLAY) },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }

                composable(Routes.DAILY) {
                    val model: DailyViewModel = viewModel(
                        factory = DailyViewModel.Factory(container.puzzles, container.progress, container.clock),
                    )
                    DailyScreen(
                        viewModel = model,
                        onPlay = { id, date -> nav.navigate(Routes.game(id, date)) },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        completedCount = completedCount,
                        totalCount = container.puzzles.count,
                        dailyCount = stats?.totalCompleted ?: 0,
                        onHapticsChanged = { scope.launch { container.settings.setHapticsEnabled(it) } },
                        onAutoNotesChanged = { scope.launch { container.settings.setAutoNotes(it) } },
                        onThemeChanged = { scope.launch { container.settings.setDarkThemeOverride(it) } },
                        onHowToPlay = { nav.navigate(Routes.HOW_TO_PLAY) },
                        onMorePuzzles = { app -> openStoreListing(context, app.packageName) },
                        modifier = Modifier.statusBarsPadding(),
                    )
                }

                composable(Routes.HOW_TO_PLAY) {
                    LaunchedEffect(Unit) { container.settings.setTutorialSeen(true) }
                    HowToPlayScreen(onDone = { nav.popBackStack() })
                }

                composable(Routes.GAME) { entry ->
                    val puzzleId = entry.arguments?.getString("puzzleId").orEmpty()
                    val dailyDate = entry.arguments?.getString("date")?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                    val puzzle = remember(puzzleId) { container.puzzles.puzzleById(puzzleId) }
                    if (puzzle == null) {
                        // A stale id (e.g. a regenerated pack): go back rather than show an empty board.
                        LaunchedEffect(puzzleId) { nav.popBackStack() }
                        return@composable
                    }

                    // Loaded before the view model exists, so the board is never briefly empty.
                    var restored by remember(puzzleId) { mutableStateOf<Loaded?>(null) }
                    LaunchedEffect(puzzleId) { restored = Loaded(container.progress.loadInProgress(puzzleId)) }
                    val load = restored ?: return@composable

                    val level = remember(puzzleId) {
                        LevelLadder.allLevels(container.puzzles.ladder).firstOrNull { it.id == puzzleId }
                    }
                    val title = when {
                        dailyDate != null -> "Daily · " + dailyDate.format(DateTimeFormatter.ofPattern("d MMM"))
                        level != null -> "Level ${level.number}"
                        else -> "Free play"
                    }

                    val model: GameViewModel = viewModel(
                        key = puzzleId,
                        factory = GameViewModel.Factory(puzzleId, puzzle, title, load.board, dailyDate, container.progress),
                    )
                    LaunchedEffect(settings.autoNotes) { model.setAutoNotes(settings.autoNotes) }

                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    DisposableEffect(lifecycle, model) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> model.onResume()
                                Lifecycle.Event.ON_PAUSE -> model.onPause()
                                else -> Unit
                            }
                        }
                        lifecycle.addObserver(observer)
                        onDispose {
                            lifecycle.removeObserver(observer)
                            model.onPause()
                        }
                    }

                    // After a ladder level, offer the next unsolved one - counting this one as solved.
                    val nextLevel = if (level == null) null else LevelLadder.next(container.puzzles.ladder, completedIds + puzzleId)
                    GameScreen(
                        vm = model,
                        haptics = settings.hapticsEnabled,
                        onToggleAutoNotes = { scope.launch { container.settings.setAutoNotes(!settings.autoNotes) } },
                        onExit = { nav.popBackStack() },
                        onNext = nextLevel?.let { next ->
                            {
                                nav.navigate(Routes.game(next.id, null)) {
                                    popUpTo(Routes.GAME) { inclusive = true }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Wraps a nullable board so "not loaded yet" and "nothing saved" stay distinct. */
private class Loaded(val board: SavedBoard?)

@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.stroke))
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(shape)
                        .background(if (selected) colors.accentFill else Color.Transparent)
                        .then(if (selected) Modifier.border(1.dp, colors.accentDeep, shape) else Modifier)
                        .clickable(role = Role.Tab) { onSelect(destination.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = if (selected) colors.onAccentFill else colors.textMuted
                    GameIcon(destination.glyph, tint, size = 21.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(destination.label, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}

private fun navigateTop(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(Routes.PLAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The Play Store app if present, the web listing otherwise. */
private fun openStoreListing(context: android.content.Context, packageName: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
    }
}
