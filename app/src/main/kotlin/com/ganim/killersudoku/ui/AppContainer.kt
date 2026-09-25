package com.ganim.killersudoku.ui

import android.content.Context
import com.ganim.killersudoku.daily.GameClock
import com.ganim.killersudoku.data.ProgressRepository
import com.ganim.killersudoku.data.PuzzleRepository
import com.ganim.killersudoku.data.SettingsRepository
import com.ganim.killersudoku.data.db.KillerDatabase
import com.ganim.killersudoku.monetize.AdManager
import com.ganim.killersudoku.monetize.AdMobAdManager
import com.ganim.killersudoku.monetize.BillingManager
import com.ganim.killersudoku.monetize.MonetizationRepository
import com.ganim.killersudoku.monetize.PlayBillingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The dependency graph, assembled by hand - DI frameworks are overkill at this size.
 * Held by [com.ganim.killersudoku.KillerApplication], so it lives as long as the process.
 */
class AppContainer(context: Context, val clock: GameClock = GameClock.System) {

    private val appContext = context.applicationContext

    /** Outlives any screen: billing and ad loads must not die with a composable. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database by lazy { KillerDatabase.get(appContext) }

    val puzzles: PuzzleRepository by lazy { PuzzleRepository(appContext.assets) }

    val progress: ProgressRepository by lazy {
        ProgressRepository(database.puzzleProgressDao(), database.dailyRecordDao(), database.userStatsDao(), clock)
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val monetization: MonetizationRepository by lazy { MonetizationRepository(appContext, clock) }

    val ads: AdManager by lazy { AdMobAdManager(appContext, appScope).also { it.initialize() } }

    val billing: BillingManager by lazy {
        PlayBillingManager(
            context = appContext,
            scope = appScope,
            onHintPackPurchased = { count -> appScope.launch { monetization.grantHintPack(count) } },
        ).also { it.connect() }
    }

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

    private var entitlementMirror: Job? = null

    /**
     * Restores purchases on every foreground, not just the first launch, so entitlement
     * survives a reinstall or a new device with nothing stored locally. The mirror into
     * the local cache is started once: a new collector per foreground would stack up.
     *
     * Entitlements belong to this package. Buying "remove ads" in Nonogram does not remove
     * them here, and the killer build plan calls that the most common launch-day bug of a
     * second app - so nothing here reads another app's state.
     */
    fun onAppForegrounded() {
        billing.refresh()
        if (entitlementMirror == null) {
            entitlementMirror = appScope.launch {
                billing.entitlements.collect { monetization.cacheAdFree(it.adFree) }
            }
        }
    }
}
