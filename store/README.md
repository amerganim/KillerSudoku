# Store assets

What Play needs for the listing, following Nonogram's `store/` folder. These are files
for you to upload; nothing here uploads anything.

| File | What it is | Play requirement | State |
|---|---|---|---|
| `icon-512.png` | Listing icon | 512×512, 32-bit PNG, no transparency | Done |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500 | Done |
| `listing.md` | Name, short and full description | 30 / 80 / 4,000 characters | Done (25 / 75 / ~2,400) |
| `privacy-policy.html` | Privacy policy | Required, because the app serves ads | Needs your contact email |
| `screenshots/` | Phone screenshots | 2–8 | **Needs the device** |

## Regenerating the graphics

```powershell
powershell -File store\render.ps1
```

This rebuilds `icon-512.png` from the launcher vector
(`app/src/main/res/drawable/ic_launcher_foreground.xml`) every time, so the icon on the
listing and the icon on the phone cannot drift apart. It uses headless Microsoft Edge as
the renderer and loads Fredoka and Outfit from Google Fonts, so run it online.

The palette at the top of `render.ps1` is copied from `app/.../ui/theme/Color.kt`. There
is no way to share constants across that boundary, so if the theme changes, change it
there too.

**The feature graphic's board is honest.** It shows one 3×3 box with digits 8 1 6 / 3 5 7 /
4 9 2, and every cage sum on it is the real total of those digits (11, 7, 18, 9). The
outlines use the same corner rules as the app (`game/CageOutlines.kt`), including the
concave corner of the L-shaped cage of 18.

## The icon

Two cells in a dashed cage of 17 holding 8 and 9: the only combination a two-cell 17
allows, and the first thing How to play teaches. It was checked at launcher sizes down to
48px against the real 72dp adaptive viewport (`docs/launcher-icon-preview.png`). The first
draft had the sum label colliding with the 8 and an empty second row of cells, and both
were fixed.

## Before you upload

- [ ] **Put a contact email in `privacy-policy.html`.** It is marked `TODO`, and Play
      requires a working address.
- [ ] Publish the policy somewhere public. GitHub Pages works: enable Pages on
      `amerganim/KillerSudoku` and serve `store/privacy-policy.html`.
- [ ] Confirm "Killer Sudoku: Pure Logic" is free on Play (names must be unique).
- [ ] Declare **contains ads** and complete the Data Safety form. It must agree with the
      policy: AdMob collects device identifiers; the app itself collects nothing.
- [ ] Content rating questionnaire: Everyone.
- [ ] Capture the six screenshots listed at the end of `listing.md` from a real device.
- [ ] Check TalkBack on the device before the listing claims screen-reader support. The
      board's accessibility nodes are built and unit-tested, but they have not been heard
      on a phone yet.

## Not here on purpose

- **A promo video** — optional, and not worth it before there is any signal the listing
  converts.
- **Tablet screenshots** — only needed if you publish for tablets. The layout now copes
  with them, but launch is phone-first.
- **Localised listings** — English first.
