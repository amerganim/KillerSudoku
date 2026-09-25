# Play Store listing copy

Killer build plan §8: target *killer sudoku* (and sumdoku, sum sudoku), never plain
"sudoku" — the field is hopeless and the traffic would not convert. State the
differentiators plainly: no forced ads, fully offline, 5,000 puzzles, every puzzle
solvable by logic with no guessing, and hints that name the technique.

Nonogram's listing rules carry over: keywords only where they would appear anyway, no
emoji, no exclamation marks, and **every number is read out of the code**. A claim the app
does not keep is worse than no claim.

---

## App name (30 characters max)

```
Killer Sudoku: Pure Logic
```

*25 characters.* Same shape as Nonogram's "Nonogram: Pure Logic", so the two read as one
family on the store, and the differentiator is in the name where it survives being seen
with no description attached.

Alternatives, in case the name is taken:

```
Killer Sudoku: Sum Logic
Killer Sudoku Sumdoku: Logic
```

---

## Short description (80 characters max)

```
Killer sudoku, always solvable by logic. Hints that teach you why. Offline.
```

*75 characters.*

---

## Full description (4,000 characters max)

```
Every puzzle in this app can be solved by logic alone. Not most of them. All of them.

Killer sudoku is sudoku with no starting digits. Instead, the grid is divided into dashed
cages, and each cage shows a total. Fill every row, column and box with 1 to 9 so that
every cage adds up - with no digit repeated inside a cage.

NEVER GUESS - CHECKED, NOT PROMISED
Every puzzle is solved by a logic engine before it ships, using only moves a person can
make. That proves two things: it can be finished by reasoning, and it has exactly one
answer. Then a second, independent search checks the one-answer part again. Anything
that fails is thrown away.

So when you are stuck, there is always a next step you can work out.

A HINT THAT TELLS YOU WHY
Most sudoku apps reveal a square and tell you nothing. This one names the technique and
shows you the reasoning:

"Rule of 45 on row 7 (innies): R7C3, R7C4, R7C5, R7C9 must add up to 27. That leaves
only 6 for R7C3."

Cage combinations, the rule of 45, pairs and triples, pointing - you learn the moves by
seeing them used on your own board. Three free hints a day. A wrong digit on the board is
pointed out for free before any hint is spent.

START AT LEVEL 1
116 numbered levels, from a warm-up of small cages to expert puzzles that need the rule of
45 across several rows at once. Nothing is locked: start wherever you like.

5,000 PUZZLES, EASY TO EXPERT
Beyond the levels, pick a difficulty and play a random one you have not solved yet.

A PUZZLE EVERY DAY
One daily puzzle, the same for everyone. Gentle early in the week, hardest on Saturday.
Build a streak; one missed day a month is forgiven automatically, and every past day
stays playable in the calendar.

BUILT FOR THINKING
Pencil marks, or let Auto notes keep them for you. The number pad stays under the board,
never on top of it. Highlights for the row, column, box and cage you are in. Clashes show
the moment they happen. Undo two hundred steps back. Three lives per puzzle.

PLAYS OFFLINE
No account, no sign-in, no connection needed. Your progress stays on your phone.

LIGHT AND DARK
Both designed from scratch, and checked for contrast. Screen reader support on the board.

---

Free, with ads. Never during a puzzle. At most one between puzzles, and never before your
third. One purchase removes them for good.
```

*About 2,400 characters.*

### Notes on the copy

- **The hint quote is real output** from `HintProvider` on ladder level 1, not written
  for the listing. `HintTextTest` holds every hint to the same form: it names its cell,
  states its digit, and reads as a sentence.
- **"A second, independent search checks the one-answer part again"** is literal:
  `BacktrackingVerifier` shares no code with the logic solver. It runs in the test suite
  (100 per difficulty in the acceptance run), not over the shipped pack at build time -
  the logic solver's own proof covers the pack, since every technique is sound.
- **Numbers come from the code**: 5,000 puzzles (the pack), 116 levels (12 + 30 + 30 + 24
  + 20 in `LevelLadder`), 3 free hints a day (`HintEconomy`), 1 streak freeze a month
  (`StreakCalculator`), 200 undo steps and 3 lives (`GameSession`). If any change, this
  file is wrong.
- **The ad line is stricter in the code than on the page**, which is the right way round:
  `AdPolicy` also caps a session at four and keeps three minutes between them.
- **Removing ads does not remove optional reward videos.** The copy says "removes them"
  about the ads *between puzzles*; the videos a player chooses to watch for a hint or a
  life stay available, as in Nonogram. The privacy policy says the same.

---

## Keywords the copy covers naturally

killer sudoku · sumdoku · sum sudoku · cage · rule of 45 · logic puzzle · daily puzzle ·
offline puzzle · no guessing

(Build plan §8 lists "samunamupure" as well. It is the Japanese name and nobody searches
for it in English - leave it out of the copy rather than stuff it in.)

---

## Categorisation

- **Category:** Games → Puzzle
- **Tags:** Logic, Brain games, Casual
- **Contains ads:** Yes — must be declared
- **In-app purchases:** Yes — `remove_ads`, `hint_pack_25`
- **Content rating:** Everyone. No violence, no user-generated content, no social
  features, nothing shared between users.

---

## Screenshots — still to capture

Phone screenshots must come off a real device, as Nonogram's did. Shoot them to the
claims, not as a tour:

1. **A hint on screen**, naming its technique — what no competitor's screenshot shows.
2. **The Play tab** with Level 1 and "Start here".
3. **A board mid-solve** with pencil marks and a cage highlighted.
4. **The results card** after a solve.
5. **The Daily tab** with a streak and the calendar.
6. **Light theme**, so nobody assumes it is dark-only.
