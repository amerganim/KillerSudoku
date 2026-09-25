package com.ganim.killersudoku.engine

import com.ganim.killersudoku.engine.generator.CageRepair
import com.ganim.killersudoku.engine.generator.PackBuilder
import com.ganim.killersudoku.engine.model.Cage
import com.ganim.killersudoku.engine.model.Difficulty
import com.ganim.killersudoku.engine.model.KillerPuzzle
import com.ganim.killersudoku.engine.pack.PuzzlePack
import com.ganim.killersudoku.engine.solver.SolveResult
import com.ganim.killersudoku.engine.solver.Solver
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.io.File

class SolverAndGeneratorTest {
    private val solver = Solver()

    companion object {
        /** Build plan Phase 1 asks for 100; -PperDifficulty=100 runs the full check. */
        private val perDifficulty = System.getProperty("killer.perDifficulty")?.toInt() ?: 25

        /** Shared across test methods - JUnit makes a fresh instance for each one. */
        private val generated: Map<Difficulty, List<KillerPuzzle>> by lazy {
            PackBuilder(seed = 42).build(Difficulty.entries.associateWith { perDifficulty })
                .puzzles.mapValues { (_, list) -> list.map { it.puzzle } }
        }
    }

    @Test fun `pack building is deterministic regardless of thread count`() {
        val quota = Difficulty.entries.associateWith { 2 }
        val a = PackBuilder(seed = 7, threads = 1).build(quota).puzzles
        val b = PackBuilder(seed = 7, threads = 6).build(quota).puzzles
        for (d in Difficulty.entries) {
            a.getValue(d).map { it.puzzle.cages } shouldBe b.getValue(d).map { it.puzzle.cages }
        }
    }

    @Test fun `generated puzzles are unique by logic and by independent backtracking`() {
        for ((d, puzzles) in generated) for (p in puzzles) {
            p.difficulty shouldBe d
            val result = solver.solve(p).shouldBeInstanceOf<SolveResult.Unique>()
            result.solution.toList() shouldBe p.solution.toList()
            Difficulty.forTier(maxOf(1, result.maxTier)) shouldBe d
            BacktrackingVerifier.countSolutions(p) shouldBe 1
        }
    }

    @Test fun `generated cages are well formed`() {
        for (p in generated.values.flatten()) {
            for (cage in p.cages) {
                (cage.size in 1..6) shouldBe true
                cage.cells.map { p.solution[it] }.distinct().size shouldBe cage.size
            }
            (p.cages.count { it.size == 1 } <= 2) shouldBe true
        }
    }

    @Test fun `ambiguous when every row is one cage of 45`() {
        val solution = IntArray(81) { i -> val r = i / 9; val c = i % 9; (r * 3 + r / 3 + c) % 9 + 1 }
        val puzzle = KillerPuzzle.fromLayout(0, solution, IntArray(81) { it / 9 }, Difficulty.EASY)
        solver.solve(puzzle) shouldBe SolveResult.Ambiguous
        BacktrackingVerifier.countSolutions(puzzle) shouldBe 2
    }

    @Test fun `contradiction when a cage sum is impossible`() {
        val base = generated.getValue(Difficulty.EASY).first()
        val k = base.cages.indexOfFirst { it.size == 2 }
        val broken = base.cages.toMutableList().also { it[k] = Cage(it[k].cells, 2) }
        val puzzle = KillerPuzzle(0, broken, base.solution, Difficulty.EASY)
        solver.solve(puzzle) shouldBe SolveResult.Contradiction
        BacktrackingVerifier.countSolutions(puzzle) shouldBe 0
    }

    @Test fun `pack round trip`() {
        val puzzles = Difficulty.entries.flatMap { generated.getValue(it) }
        val pack = PuzzlePack.decode(PuzzlePack.encode(puzzles))
        pack.size shouldBe puzzles.size
        Difficulty.entries.forEach { pack.count(it) shouldBe perDifficulty }
        puzzles.forEachIndexed { i, p ->
            val q = pack[i]
            q.difficulty shouldBe p.difficulty
            q.cages shouldBe p.cages
            q.solution.toList() shouldBe p.solution.toList()
        }
    }

    /** Build plan Phase 1: the full pack is under 1 MB and round-trips. Skipped until generated. */
    @Test fun `bundled pack round trips and every cage is well formed`() {
        val file = File("../app/src/main/assets/puzzles.bin")
        Assumptions.assumeTrue(file.exists(), "pack not generated yet")
        val bytes = file.readBytes()
        (bytes.size < 1024 * 1024) shouldBe true
        val pack = PuzzlePack.decode(bytes)
        pack.size shouldBe 5000
        listOf(1500, 1500, 1250, 750) shouldBe Difficulty.entries.map { pack.count(it) }
        for (i in 0 until pack.size) {
            val p = pack[i]
            PuzzlePack.decodeRecord(PuzzlePack.encodeRecord(p), 0, i, p.difficulty) shouldBe p
            for (cage in p.cages) {
                CageRepair.connected(cage.cells.toList()) shouldBe true
                cage.cells.map { p.solution[it] }.distinct().size shouldBe cage.size
            }
        }
    }

    @Test fun `engine has no Android imports`() {
        val offenders = File("src/main").walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readLines().any { it.startsWith("import android") || it.startsWith("import androidx") } }
            .map { it.name }.toList()
        offenders shouldBe emptyList()
    }
}
