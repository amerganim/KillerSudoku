package com.ganim.killersudoku.daily

import com.ganim.killersudoku.engine.model.Difficulty
import java.time.DayOfWeek
import java.time.LocalDate

/** The difficulty the daily puzzle takes on a given day. Killer sudoku has one size. */
data class DailySpec(val difficulty: Difficulty)

/**
 * Which difficulty each weekday gets. Same shape as Nonogram's week: start gently, build
 * through the week, peak on Saturday when people have time, ease off on Sunday. A player
 * who can only manage fifteen minutes on a weeknight is never locked out of their streak
 * by an expert puzzle.
 *
 * Killer sudoku takes longer than a nonogram - an easy one is 8-12 minutes (build plan
 * 4) - which is why expert appears once a week and not twice.
 */
object DailySchedule {

    /**
     * Any Monday; only used to turn a bare weekday into a date the rotation can read.
     * Declared first: object initialisers run in order, and [allSpecs] reads it.
     */
    private val MONDAY_REFERENCE: LocalDate = LocalDate.of(2024, 1, 1)

    fun specFor(date: LocalDate): DailySpec = when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> DailySpec(Difficulty.EASY)
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY -> DailySpec(Difficulty.MEDIUM)
        DayOfWeek.FRIDAY -> DailySpec(Difficulty.HARD)
        DayOfWeek.SATURDAY -> DailySpec(Difficulty.EXPERT)
    }

    val allSpecs: Set<DailySpec> = DayOfWeek.entries.map(::specForWeekday).toSet()

    fun specForWeekday(day: DayOfWeek): DailySpec = specFor(MONDAY_REFERENCE.with(day))

    /**
     * The weekdays that draw from the same pool as [day], in week order. Shared pools
     * are counted together by [DailySelector] so two slots never walk one pool
     * independently and collide.
     */
    fun weekdaysSharing(day: DayOfWeek): List<DayOfWeek> {
        val spec = specForWeekday(day)
        return DayOfWeek.entries.filter { specForWeekday(it) == spec }
    }
}
