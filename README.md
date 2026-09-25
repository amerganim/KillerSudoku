# Killer Sudoku

Game two of the puzzle portfolio. Spec: [docs/killer-sudoku-build-plan.md](docs/killer-sudoku-build-plan.md),
shared-engine design: [docs/puzzle-engine-core.md](docs/puzzle-engine-core.md).

## Structure

| Module | What | Android? |
|---|---|---|
| `:engine` | Model, `DigitCombos`, solver + techniques, generator, pack codec, offline pack tool | No — pure Kotlin/JVM |
| `:app` | Compose UI, game session, hints, saved game | Yes |

`:engine` is laid out like the future `:core:puzzle` + `:games:killer` split from the core spec. Nonogram has
not been extracted yet, so this repo stands alone for now. When the extraction happens, `:engine` moves
across without a rewrite. A test fails the build if an Android import ever lands in `:engine`.

## Build

No `java` on PATH on this machine. Use Android Studio's JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :engine:test                          # engine suite (~40 s, 25 puzzles per difficulty)
.\gradlew.bat :engine:test -PperDifficulty=100      # Phase 1 acceptance run
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :engine:generatePuzzlePack -Pcount=5000 -Pseed=1   # rewrites app/src/main/assets/puzzles.bin
.\gradlew.bat :engine:bench -Pn=40                  # difficulty calibration report
```

## How generation works (and where it differs from the plan)

1. Random valid grid from a canonical pattern plus symmetry transforms.
2. Random-walk cage partition: sizes weighted per target shape, no repeated digit in a cage, compact shapes preferred.
3. Solve with the logical solver. **If it stalls, repair the layout around the open cells** (split a cage, or move
   one boundary cell) and solve again, up to 40 times.
4. Accept only a logical `Unique`, then rate it by the hardest tier used.

The plan expected plain generate-and-reject to work. Calibration on 2026-09-25 showed it doesn't: most random
partitions really have 2+ solutions (verified by brute force), often a 4-cell "deadly rectangle". With the repair
step, ~98% of attempts produce a puzzle.

Each accepted puzzle is proven unique twice: by the sound logical solver, and in tests by an independent
backtracking counter (`engine/src/test/.../BacktrackingVerifier.kt`, never shipped).

### Difficulty tiers (calibrated, recorded in `Techniques.kt`)

| Tier | Techniques | Difficulty |
|---|---|---|
| 1 | Naked/hidden single, cage combinations, **Rule of 45 (one house)** | Easy |
| 2 | Cage–house elimination | Medium |
| 3 | Naked/hidden pairs & triples, **pointing / box-line** | Hard |
| 4 | Rule of 45 across 2–3 rows or columns | Expert |

Changes from the plan: Rule of 45 moved from tier 2 to tier 1, because 0 of 160 puzzles could be solved without it.
Pointing pairs moved from tier 4 to tier 3.

## Status

- [x] Phase 1 engine: techniques, solver, generator with repair, pack codec, pack tool, tests
- [x] Phase 2 first pass: board with dashed inset cages, persistent number pad, notes, auto-notes, highlighting,
      conflicts, 3 lives, 200-step undo, technique-naming hints, saved game
- [ ] Phase 2 remaining: render check against every cage shape in the pack, 60 fps on a low-end device, device testing
- [ ] Phase 3 shell (daily / streak / archive, lifted from Nonogram), real icon and palette
- [ ] Phase 4 ads + billing (re-verify entitlements in the new package)
- [ ] Phase 5 store + closed testing (needs its own 12 testers × 14 days — start recruiting now)
