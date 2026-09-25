package com.ganim.killersudoku.ui

import android.content.Context
import com.ganim.killersudoku.daily.GameClock
import com.ganim.killersudoku.data.ProgressRepository
import com.ganim.killersudoku.data.PuzzleRepository
import com.ganim.killersudoku.data.SettingsRepository
import com.ganim.killersudoku.data.db.KillerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The dependency graph, assembled by hand - DI frameworks are overkill at this size. */
class AppContainer(context: Context, val clock: GameClock = GameClock.System) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database by lazy { KillerDatabase.get(appContext) }

    val puzzles: PuzzleRepository by lazy { PuzzleRepository(appContext.assets) }

    val progress: ProgressRepository by lazy {
        ProgressRepository(database.puzzleProgressDao(), database.dailyRecordDao(), database.userStatsDao(), clock)
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    init {
        // Warm what the first screens need, off the main thread. Nonogram measured each
        // of these as a visible hitch on a Galaxy A15 when left to whichever screen got
        // there first: the pack index (5,000 content hashes), the ladder, today's daily,
        // and the desugared java.time locale tables.
        appScope.launch(Dispatchers.Default) {
            puzzles.ladder
            puzzles.dailyIdFor(clock.today())
            val locale = java.util.Locale.getDefault()
            clock.today().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
            clock.today().month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        }
    }
}
