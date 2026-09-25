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
- [x] Phase 2 rendering: cage outlines proven closed for every cage in all 5,000 puzzles (`CageOutlinesTest`)
- [ ] Phase 2 remaining: 60 fps on a low-end device, device testing
- [x] Phase 3 shell and theming. Built and unit-tested. **Not yet checked on a device.**
- [x] Phase 4 ads, billing, hardening. Built, policy-tested, and the minified release builds. **SDK paths need a
      device and Play test tracks.**
- [~] Phase 5 store assets: icon, feature graphic, listing copy, privacy policy done (`store/`).
      **Screenshots need the device**; closed testing needs its own 12 testers × 14 days — start recruiting now

**236 tests, 0 failures** (engine 21, app 215).

## Since Phase 4

- **Cage outlines proven for the whole pack.** The outline geometry now lives in `game/CageOutlines.kt`, outside
  Compose, and `CageOutlinesTest` checks all 5,000 puzzles: each side between two cages gets exactly one segment,
  and every cage outline closes, with each segment end meeting exactly one other. I broke the concave-corner rule
  on purpose to confirm the test catches it (28 loose ends in the first puzzle), then restored it.
- **Screen reader.** With TalkBack on, each cell announces its position, its cage ("cage of 17, 2 cells") and what
  it holds ("7, wrong", "empty, notes 1 4 9"). Activating a cell selects it, and the number pad keys say "Place 7,
  3 left". Built only while touch exploration is on, so it costs nothing otherwise. Not yet heard on a device.
- **Hint wording.** Real hints read "can only use 39" and "can only use 3456789". Digits are now spoken ("3 and 9",
  "1, 4 or 6"), an exact combination is said as one ("can only be 1 and 2 here"), and a lone remainder says what it
  is. `HintTextTest` checks over 1,000 hints from real ladder puzzles: each names its cell, states its digit, never
  runs digits together, and reads as a sentence. It caught two more on its first run.
- **Store material** in `store/`: see `store/README.md`.

## Phase 4: monetization and hardening

Nonogram's monetization was ported unchanged, tests included:

- **`AdPolicy`**: an interstitial may only be considered when a results card is dismissed. Never mid-puzzle,
  never on launch, and only after 3 puzzles, at least 3 minutes apart, at most 4 per session, and never for
  ad-free players.
- **`RewardPolicy`**: when there's no ad to show (no fill or offline), the reward is granted anyway. Only
  closing the ad early withholds it.
- **`HintEconomy`**: 3 free hints a day, refilled at local midnight and not stacked. Bought hints are kept,
  and free ones are spent first.
- **`AdMobAdManager`, `PlayBillingManager`**: restore purchases on every foreground, acknowledge them straight
  away, and treat pending purchases as pending, not owned.

Wired into Killer:

| Where | What |
|---|---|
| Hint key | Shows how many hints are left. **The board is checked before the wallet**, so a wrong digit is pointed out for free and a solved board is never charged |
| Out of hints | Offers a video for one hint, or the 25-pack at its localized price. A pack bought while the offer is open pays for the waiting hint |
| Out of lives | **Watch a video, keep going** (one life back), or Start over |
| Results card | Done / Next level go through `AdPolicy`, the only interstitial placement |
| Settings → Store | Remove ads and 25 hints, each tappable only once Play returns a price. Shows "Payment pending" and "Ads removed" |

**Nonogram has no store UI.** Nothing in it calls `launchPurchase`, so neither of its products can be bought.
Killer has a store section on purpose, and Nonogram needs the same fix.

Entitlements belong to a package. Buying "remove ads" in Nonogram doesn't remove them here, so nothing reads
across apps. Both products must be created again in this app's Play Console listing.

### Hardening

- **Release build (R8)**: builds clean with the ported keep rules; the APK is **4.54 MB** (Nonogram's budget
  is 15 MB). `-PlocalReleaseCheck=true` signs with the debug key and allows test ads so the minified build can be
  run on a device. Without that flag, a release **fails** unless real AdMob IDs are in `local.properties`
  (`admob.appId`, `admob.unit.interstitial`, `admob.unit.rewarded`).
- **Orientation unlocked.** From targetSdk 36, Android ignores orientation locks on large screens, so the board
  now takes the largest square that fits the height left over. Before, it overflowed in landscape and split
  screen.
- **Tested on the JVM**: `GameViewModelTest` covers the money rules (never charged for nothing, top-up, pack
  bought mid-offer, life for a video) and which completions move the streak.

### Still needs you

1. **AdMob**: create a Killer Sudoku app with one interstitial and one rewarded unit, and put the IDs in
   `local.properties`.
2. **Play Console**: create `remove_ads` (non-consumable, ~$2.99) and `hint_pack_25` (consumable, ~$0.99) for
   `com.ganim.killersudoku`, then upload to an internal test track and test buying, restoring after a
   reinstall, and a pending purchase.
3. **On the A15**: run `assembleRelease -PlocalReleaseCheck=true` and check that the database, pack and settings
   survive a force-stop in the minified build, then a 4,000-event monkey run. This is the same list Nonogram's
   Phase 6 verified.

## Phase 3: the shell, lifted from Nonogram

This follows Nonogram's shell as it is today, not its original Phase 3. Nonogram's first tester couldn't find where
the game started, so its shell was reworked: the app opens on a level ladder, and the archive tab was retired in
favour of free play. Killer Sudoku starts from that design.

| Piece | From Nonogram | What changed for Killer |
|---|---|---|
| `DailySelector`, `StreakCalculator` | Verbatim, tests too | Nothing. No-repeat walk, pinned hash, monthly freeze, backfill never repairs a streak |
| `DailySchedule` | Rewritten | One grid size, so the week is difficulty only: Mon–Tue easy, Wed/Thu/Sun medium, Fri hard, Sat expert |
| Room schema, DAOs, `ProgressRepository` | Daily/streak half verbatim | Board snapshot is Killer's: digits, notes, lives, clock and the last 50 undo steps (`SavedBoardCodec`) |
| `LevelLadder`, Play tab | Same rules: guided, not gated | Ramp is by difficulty. Warm-up = the 12 easy puzzles with the most (smaller) cages |
| Free play | Same idea ("a supply, not a catalogue") | Pick a difficulty and play a random unsolved puzzle. No browse grid: every killer is 9×9 |
| Settings | Same layout | Adds **Auto notes** (remembered) and **More puzzles**, a cross-promo list with Nonogram (plan §10) |
| Component kit, glyphs, typography | Verbatim | New glyphs: pencil, eraser, bulb, wand, book |

Killer-specific decisions:

- **Puzzle ids are content hashes** (FNV-1a over each pack record). Progress survives a pack regeneration and can
  never attach itself to a different puzzle.
- **Dailies never use ladder levels.** Otherwise Monday's daily could be level 9, which the player already solved.
  Tested over two years of dates against the real pack.
- **Replaying a solved puzzle can't un-solve it.** It stays complete and keeps its first completion time.
- **The clock stops when the app goes to the background.** Every move is written to Room straight away, and the
  save issued on pause is non-cancellable.

### Palette: Graphite & Tangerine

Nonogram owns indigo and gold, so Killer gets its own palette under the same rules: one colour per meaning (accent =
press, rose = costs you, mint = safe, sky = explained), dark authored separately rather than inverted, and colour
literals allowed only in `ui/theme/Color.kt` (`ThemePurityTest`).

`ThemeContrastTest` checks 90 pairs per theme. The board ones are measured **against the cell with each highlight
composited over it**, because a digit in the selected cell sits on the selection wash, not the bare cell. On its
first run this caught 12 real failures, for example a wrong digit on the selected cell at 2.9:1 in dark. The fix
was to make selection a tangerine **ring** and keep every wash light.

### Launcher icon

A vector adaptive icon with a themed (monochrome) layer for Android 13+. It shows two cells in a dashed cage of 17
holding 8 and 9: the only combination that sum allows, and the first thing How to play teaches.
[docs/launcher-icon-preview.png](docs/launcher-icon-preview.png) renders it at launcher sizes. The 512px store icon
is Phase 5.

### Not done in Phase 3

- **Device verification.** Nothing in this phase has run on a phone yet. The checks Nonogram did on the A15
  still need doing: state after force-stop, cold start, jank in the ladder, 200% font scale.
- **Animated How to play.** For now it's four illustrated cards. Nonogram's solve-along walkthrough
  (checked by a test against the real solver) is the model for a later pass.
- **`gameId` column.** The core spec suggests one for a future combined app. Skipped: each app has its own
  database, and a combined app would need a new one anyway.
