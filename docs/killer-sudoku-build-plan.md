# Killer Sudoku — Build Plan (Game Two)

**Delta spec.** Assumes `puzzle-engine-core.md` is implemented. Only the parts specific to Killer Sudoku are described here; everything else — shell, daily, streak, archive, ads, billing, store process — is inherited from core and from `nonogram-app-build-plan.md`.

**Build this before Kakuro.** Rationale in §1.

**Estimated timeline: 6 weeks part-time**, versus 14 for game one.

---

## 1. Why this one first

- **Single grid size (9×9).** No responsive layout work across four sizes, no pinch-zoom requirement. A large chunk of game one's UI cost disappears.
- **Well-trodden generation.** Producing a valid complete sudoku grid is a solved problem; the only novel part is cage partitioning.
- **Larger search demand** than Kakuro, and the incumbent apps — while more numerous — are mostly ad-saturated and visually dated, which is the same opening game one exploited.
- **The sudoku technique library it forces you to build is reusable** for later variants (Arrow Sudoku, Thermo, Sandwich) that are then near-free to ship.

Tradeoff: more competition than Kakuro. You are not competing with plain Sudoku (hopeless) — the target keyword is *killer sudoku*, which is a far narrower field.

---

## 2. Rules (as implemented)

- 9×9 grid, standard sudoku constraints: digits 1–9 unique in each row, column, and 3×3 box.
- Grid is partitioned into **cages**. Each cage has a target sum.
- **Digits do not repeat within a cage.** (The standard rule. Some variants permit repeats; do not implement that.)
- **Zero given digits.** Classic killer sudoku starts empty; the cages carry all the information. This is also what makes generation tractable — you verify uniqueness from a blank board.

---

## 3. Model

```kotlin
data class Cage(
    val cells: IntArray,   // cell indices 0..80
    val sum: Int
)

data class KillerPuzzle(
    override val id: String,
    val cages: List<Cage>,
    val solution: IntArray,     // 81 digits, 1..9
    val difficulty: Difficulty,
    val maxTier: Int,
    val depth: Int
) : Puzzle
```

Precompute at load: `cageOf: IntArray` (cell → cage index), and per-cage `Candidates` from `DigitCombos.possibleDigits(size, sum)`.

---

## 4. Deduction techniques

Ordered by tier. The solver applies cheapest-first and restarts after any change (core §4).

| Tier | Technique | Notes |
|---|---|---|
| 1 | Cage combination restriction | `DigitCombos.possibleDigits(cage.size, cage.sum)` intersected into every cell of the cage. Also apply `requiredDigits` — if a digit appears in every valid combination, it must be somewhere in the cage. |
| 1 | Naked single | One candidate left → assign, eliminate from peers (row, column, box, cage). |
| 1 | Hidden single | A digit possible in only one cell of a unit → assign. |
| 2 | Rule of 45 (innies/outies) | Each row, column, and box sums to 45. For a unit where cages are wholly contained except one or two cells, the remainder is forced. This is *the* signature killer technique — implement it well. |
| 2 | Cage–unit elimination | A cage wholly inside one row/column/box: its digits are excluded from the rest of that unit. |
| 3 | Naked pairs/triples | Within row, column, box, or cage. |
| 3 | Hidden pairs/triples | Same units. |
| 3 | Cage split | Cage spanning two units — combination analysis restricts which digits fall on each side. |
| 4 | Pointing pairs / box–line reduction | Standard sudoku intersection removal. |
| 4 | Cage sum chaining | Multi-unit rule-of-45 across 2–3 rows or columns at once. |

**Do not implement X-Wing, Swordfish, or chaining techniques.** Any puzzle needing them is too hard for a mobile audience and should be rejected during generation rather than solved.

### Difficulty mapping

| Difficulty | Requires up to tier | Target share of pack |
|---|---|---|
| EASY | 1 | 30% |
| MEDIUM | 2 | 30% |
| HARD | 3 | 25% |
| EXPERT | 4 | 15% |

Calibrate against real play during Phase 1 and record final thresholds in a comment. An "easy" killer sudoku should still take 8–12 minutes; these are not fast puzzles and players do not expect them to be.

---

## 5. Generation

```
1. Generate a complete valid sudoku solution.
   Seed a canonical grid, then apply random structure-preserving transforms:
   digit relabelling (9! options), row swaps within bands, column swaps within
   stacks, band swaps, stack swaps, transpose. This is far faster and simpler
   than backtracking from empty, and the resulting distribution is fine.

2. Partition the 81 cells into cages.
   - Cage sizes 2–5, weighted toward 2–4. A few singletons are acceptable
     (they act as givens) but cap them at ~2 per puzzle or the puzzle
     becomes trivial.
   - Grow each cage by random walk from an unassigned seed cell.
   - REJECT any growth step that would put a duplicate digit in the cage.
   - Prefer compact cages; long snaking cages look bad and rate poorly.

3. Derive each cage sum from the solution.

4. Solve from a blank board with the tier-limited technique set.

5. Accept only if Unique and maxTier <= 4. Discard otherwise.

6. Rate by maxTier and depth; keep if it matches the target difficulty bucket.
```

**Expect a high rejection rate at the EXPERT end** — most random cage partitions solve too easily. To bias toward harder puzzles, increase average cage size and reduce singletons. To bias easier, do the opposite.

**Independent verification:** every accepted puzzle must be run through the test-only backtracking solver to confirm exactly one solution. Non-negotiable — a killer sudoku with two solutions is the single worst bug this app can ship.

### Pack

5,000 puzzles: 1,500 easy, 1,500 medium, 1,250 hard, 750 expert.

Encoding per puzzle: cage layout as an 81-entry cage-index array (packed 6 bits each ≈ 61 bytes) plus cage sums (7 bits each). The solution need not be stored — it is recoverable by running the solver, but store it anyway for fast mistake-checking; 81 digits at 4 bits = 41 bytes. Roughly 120 bytes per puzzle → about 600 KB for the pack. Acceptable.

---

## 6. UI specifics

Most of game one's grid work does not transfer — this is a digit-entry grid, not a paint grid. Budget 2 weeks.

- **Cage borders as dashed lines** inset inside the cell, with the sum in small type in the top-left cell of each cage. This is the conventional rendering and players expect it; do not invent an alternative.
- **Digit entry**: tap a cell to select, then tap a digit on a persistent bottom number pad. Do not use a popup keypad — it covers the grid and is the most common complaint about sudoku apps.
- **Pencil marks (candidate notes) are mandatory, not optional.** Killer sudoku is unplayable without them. Toggle between pen and pencil mode on the pad. Support up to 9 marks per cell, rendered as a 3×3 mini-grid.
- **Auto-candidate mode** as a setting: the app fills and maintains pencil marks automatically. Many players want this; purists don't. Default off.
- **Highlighting**: selected cell, its row/column/box, its cage, and all cells containing the same digit. This is a large part of perceived quality.
- **Conflict indication**: duplicate digit in a unit or cage highlights in the error colour immediately.
- **Mistake counter: 3 lives**, same as game one. A digit differing from the solution costs a life.
- Undo stack, minimum 100 steps (higher than game one — digit puzzles involve more experimentation).

**Hints** work as in game one: run the solver against the player's current board state and reveal one cell that is *logically deducible right now*, ideally naming the technique ("Rule of 45 on row 4"). Naming the technique turns a hint into teaching and is worth the small extra work.

---

## 7. Phases and acceptance criteria

**Phase 1 — Engine (2 weeks).** Techniques, solver, generator, pack tool.
- [ ] Every technique has unit tests over hand-constructed positions
- [ ] Solver classifies a suite of ≥10 known puzzles (unique / ambiguous / contradictory) correctly
- [ ] 100 generated puzzles per difficulty, all independently verified unique by the backtracking solver
- [ ] Difficulty distribution within 5% of target across a 1,000-puzzle sample
- [ ] Full 5,000 pack generated, under 1 MB, round-trip test passes
- [ ] `:core:puzzle` and `:games:killer` free of Android imports

**Phase 2 — Game screen (2 weeks).**
- [ ] Digit entry, pencil marks, auto-candidates, undo all correct
- [ ] Cage borders and sums render correctly for every cage shape in the pack (test against the full pack, not a sample — snaking cages will find your rendering bugs)
- [ ] 60fps on the low-end device profile
- [ ] State survives process death mid-puzzle
- [ ] Hint returns only logically-deducible cells and names the technique

**Phase 3 — Shell + theming (1 week).** Inherited from core; app-specific palette and icon.

**Phase 4 — Monetization + hardening (1 week).** Inherited. Re-verify billing end to end in the new package — entitlements do not carry across apps, and this is the most common launch-day bug when shipping a second app.

**Phase 5 — Store + closed testing.** Inherited from `nonogram-app-build-plan.md` §10–11.

> **Closed testing applies per app.** Game two needs its own 12 testers for 14 days. Start recruiting during Phase 2. Your game-one testers are the obvious first ask and most will say yes.

---

## 8. Store positioning

Keywords: killer sudoku, sumdoku, sum sudoku, samunamupure, killer sudoku offline, killer sudoku free.

Do not target "sudoku" — you will not rank and the traffic would not convert anyway.

Differentiators to state plainly in the listing: no forced ads, works fully offline, 5,000 puzzles, every puzzle solvable by logic with no guessing required, technique-naming hints. That last one is a genuine differentiator — most competitors just reveal a cell.
