# Puzzle Engine Core — Shared Extraction Spec

**Document purpose:** Specification for extracting the reusable engine out of the nonogram app, so that Kakuro, Killer Sudoku, and later puzzle types are each a thin rule module plus theming rather than a fresh build.

**Read this before `kakuro-build-plan.md` or `killer-sudoku-build-plan.md`.** Both are delta specs that assume this core exists.

**Prerequisite:** the nonogram app (game one) is built and shipped. Do not start this extraction against an unfinished game one — you cannot generalise an abstraction from a single incomplete example, and you will get the interfaces wrong.

---

## 1. Why extract

The expensive parts of game one were not the UI. They were:

1. The solve-until-fixpoint architecture with uniqueness guarantees
2. The generate → verify → rate → reject pipeline
3. The binary pack format and offline generation tool
4. The daily/streak/archive shell and its Room schema
5. Ads and billing wiring, with all its edge cases

All five are puzzle-agnostic. Only the deduction techniques and the grid rendering are puzzle-specific. Extracting correctly should take game two from ~14 weeks to ~6, and game three to ~3.

**Counter-check before you start:** if game one's retention numbers came back bad (D1 under 25%), do not build this. The problem is the product, not the pipeline, and a reusable engine for making games nobody retains is worth nothing. Extraction is the correct move only if game one proved the loop works.

---

## 2. Repo structure

Convert to a multi-module Gradle project:

```
:core:puzzle       # pure Kotlin. Solver framework, generation pipeline, pack codec.
:core:data         # Room schema, repositories, settings, progress
:core:ui           # theme, shared composables, grid input gestures, navigation shell
:core:monetize     # AdManager, BillingManager
:games:nonogram    # rule module: model, techniques, renderer
:games:kakuro      # rule module
:games:killer      # rule module
:app:nonogram      # thin app module: manifest, ids, theming, store config
:app:kakuro
:app:killer
```

Each shipped app is a separate Play listing with its own package name, its own icon, and its own store keywords. They share code, not identity.

**`:core:puzzle` must remain free of all Android imports.** This is what keeps JVM unit tests fast and lets the offline generator reuse the real solver rather than a copy of it.

---

## 3. Generalised puzzle model

The nonogram model is boolean-per-cell. Kakuro and Killer are digit-per-cell. Generalise to a candidate bitmask over a value domain.

```kotlin
/** Values a cell may hold, as a bitmask. Bit i set = value i is still possible. */
@JvmInline
value class Candidates(val mask: Int) {
    val isEmpty: Boolean get() = mask == 0
    val isSolved: Boolean get() = mask.countOneBits() == 1
    val solvedValue: Int get() = mask.countTrailingZeroBits()
    infix fun and(other: Candidates) = Candidates(mask and other.mask)
    infix fun remove(value: Int) = Candidates(mask and (1 shl value).inv())
}
```

Nonogram uses a 2-bit domain (empty/filled). Kakuro and Killer use bits 1–9. Same type, same operations, no per-game variants.

```kotlin
interface PuzzleRules<P : Puzzle> {
    /** All deduction techniques, cheapest first. */
    val techniques: List<Technique<P>>
    /** Cells whose candidates the solver tracks. */
    fun cellCount(puzzle: P): Int
    /** Initial candidate state before any deduction. */
    fun initialCandidates(puzzle: P): CandidateGrid
    /** Validate a completed assignment against the rules. */
    fun isValidSolution(puzzle: P, values: IntArray): Boolean
}

fun interface Technique<P : Puzzle> {
    /** Apply once. Return true if anything changed. */
    fun apply(puzzle: P, grid: CandidateGrid): Boolean
    val tier: Int   // difficulty weight; see §5
}
```

---

## 4. Solver framework

```kotlin
sealed interface SolveResult {
    data class Unique(val depth: Int, val maxTier: Int) : SolveResult
    data object Ambiguous : SolveResult      // stalled with cells unresolved
    data object Contradiction : SolveResult  // a cell has zero candidates
}

class Solver<P : Puzzle>(private val rules: PuzzleRules<P>) {
    fun solve(puzzle: P): SolveResult
}
```

Algorithm, unchanged in spirit from game one:

```
grid = rules.initialCandidates(puzzle)
depth = 0; maxTier = 0
loop:
    progress = false
    for technique in rules.techniques (cheapest tier first):
        if technique.apply(puzzle, grid):
            progress = true
            maxTier = max(maxTier, technique.tier)
            break            # restart from cheapest technique after any change
    if any cell has zero candidates: return Contradiction
    if all cells solved: return Unique(depth, maxTier)
    if !progress: return Ambiguous
    depth++
```

**Restarting from the cheapest technique after every change is deliberate.** It makes `maxTier` mean "the hardest technique genuinely required", which is what difficulty rating depends on. Without the restart you overstate difficulty.

**Only `Unique` is ever accepted into a shipped pack.** No guessing, no backtracking, no trial-and-error. This rule carried game one's review scores and it carries these too.

A separate backtracking verifier lives in `:core:puzzle` **test sources only** — used to independently prove that accepted puzzles have exactly one solution. It never ships.

---

## 5. Difficulty rating

Two signals, combined:

- **`maxTier`** — the hardest technique required. Primary signal, and much more meaningful than raw depth for digit puzzles.
- **`depth`** — number of fixpoint iterations. Secondary; separates long-but-easy from short-but-sharp.

Each game defines its own tier→difficulty mapping (see the game specs). Calibrate empirically during that game's Phase 1 and record the final thresholds in a comment.

---

## 6. Digit combination table

**Shared by Kakuro and Killer Sudoku. Build once, in `:core:puzzle`.**

Every non-empty subset of the digits 1–9 — exactly 511 of them — indexed by size and sum.

```kotlin
object DigitCombos {
    /** All masks of `size` distinct digits 1..9 summing to `sum`. */
    fun masksFor(size: Int, sum: Int): List<Int>
    /** Union of all such masks — every digit that could appear. */
    fun possibleDigits(size: Int, sum: Int): Candidates
    /** Intersection — digits appearing in every valid combination. */
    fun requiredDigits(size: Int, sum: Int): Candidates
}
```

Precompute at class-init by enumerating all 511 subsets; the whole table is a few KB. `possibleDigits` and `requiredDigits` are the workhorses — a kakuro run and a killer cage both reduce to "distinct digits, known count, known sum", so they share this table exactly.

Valid sums for a given size range from `size*(size+1)/2` up to the sum of the top `size` digits. Return empty for out-of-range queries rather than throwing.

---

## 7. Generation pipeline

```kotlin
interface PuzzleGenerator<P : Puzzle> {
    fun generateCandidate(rng: Random, spec: GenSpec): P?
}

class GenerationPipeline<P : Puzzle>(
    private val generator: PuzzleGenerator<P>,
    private val solver: Solver<P>,
    private val rater: DifficultyRater
) {
    fun generate(spec: GenSpec, count: Int): List<P>
}
```

Loop: generate candidate → solve → accept only if `Unique` and rated difficulty matches target → else discard and retry. High rejection rates are expected and harmless because this runs offline on the JVM, never on device.

Parallelise across cores — killer sudoku generation in particular benefits, and a 5,000-puzzle pack should build in minutes not hours.

---

## 8. Pack format

Generalise game one's encoder. Header: magic bytes, format version, puzzle count, per-difficulty index offsets. Body: length-prefixed records, game-specific payload.

```kotlin
interface PackCodec<P : Puzzle> {
    fun encode(puzzle: P, out: BitWriter)
    fun decode(input: BitReader): P
}
```

**Store the minimum and derive the rest at load.** Game one stores the solution bitset and derives clues. Killer stores the cage layout and sums, deriving nothing else. Kakuro stores the black-cell layout plus run sums. Target: full 5,000-puzzle pack under 1 MB per game.

Round-trip test is mandatory: encode → decode → assert structural equality, across the entire pack.

---

## 9. Shell, data, monetization

Lift from game one essentially unchanged. The daily/streak/archive logic is already puzzle-agnostic — it deals in puzzle IDs, not puzzle contents.

`:core:data` — Room schema (`PuzzleProgress`, `DailyRecord`, `UserStats`), repositories, DataStore settings. Add a `gameId` column so a future combined app could host multiple puzzle types; individual apps just ignore it.

`:core:ui` — theme system, navigation shell, calendar, archive browser, results card, settings. Each app supplies its own colour palette and accent; structure is shared.

`:core:monetize` — `AdManager` and `BillingManager` verbatim. **Ad unit IDs and billing SKUs come from each app module's `BuildConfig`, never hardcoded in core.** Keep the same placements, frequency caps, and the never-interrupt-an-in-progress-puzzle rule.

---

## 10. Cross-promotion

The one genuinely new piece. With multiple apps live, each should promote the others.

- A "More puzzles" entry in settings, listing your other apps with Play deep links
- A single non-intrusive house-ad slot on the results card, shown only to players who have completed 5+ puzzles, and never in place of a revenue ad more than once per session
- Do not build a cross-promo SDK. A hardcoded list is correct at this scale.

This is the compounding return on the portfolio strategy: game three's launch gets real installs from games one and two on day one, instead of starting cold.

---

## 11. Extraction phases

**Phase E1 — Extract without behaviour change (1 week).**
Move code into the module structure above. Nonogram must build, pass every existing test, and behave identically. No new features. Ship a nonogram update from the refactored tree and confirm no crash-rate regression before proceeding.

**Phase E2 — Generalise the model (1 week).**
Introduce `Candidates`, `PuzzleRules`, `Technique`, the generalised `Solver`. Port nonogram onto the new interfaces. Its full test suite must still pass — that suite is your proof the abstraction is correct.

**Phase E3 — Shared utilities (3 days).**
`DigitCombos`, generalised `PackCodec`, parallel generation pipeline. Unit tests for the combination table against hand-verified values (e.g. size 2 sum 17 → exactly one combination; size 9 sum 45 → exactly one).

**Phase E4 — Cross-promo (2 days).**
Ship as a nonogram update so the slot is live before game two launches.

### Acceptance criteria

- [ ] Nonogram builds from the multi-module tree and passes 100% of its prior tests
- [ ] `:core:puzzle` contains zero Android imports (enforced by test or lint rule)
- [ ] `DigitCombos` enumerates exactly 511 subsets; spot-checks pass
- [ ] Pack round-trip passes over the full nonogram pack
- [ ] Refactored nonogram shipped to production with no crash-rate regression over 7 days
