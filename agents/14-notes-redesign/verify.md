# Verify – Notes redesign

- Build commit: b4ada95 (last feature 14 code commit 4651b03). Unmodified debug APK installed with `adb install -r`, then `pm clear` for a fresh install (the old vault's password was unknown). New vault, master password `correcthorse1`.
- Device: AVD `Pixel_9a`, API 36, emulator-5554, device PIN 1234.
- Result: **PASS** (1 minor issue)
- Unit tests: `SettingsStoreTest` (9), `NotesViewModelTest` (15) → 0 failures.
- Proof: PNG screenshots were not possible. FLAG_SECURE makes `screencap` black, and the temporary FLAG_SECURE-off edit used by earlier verifiers was blocked by the tool permission policy. The `uiautomator` dumps in `screenshots/14-notes-redesign/*.xml` are the proof instead.

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Editor: borderless title + body, no heading | Notes → + | PASS: placeholders "Title" and "Note", empty top bar with Back and Save only | – |
| 2 | New note: body has focus, keyboard opens, title follows the body live, saved as-is | + → type `Wifi router admin`, Enter, second line → Save | PASS: body focused, `mInputShown=true`. Title shows `Wifi router admin · 31 Oct 2026` while typing. The card shows the same title after Save | 01-grid-default.xml |
| 3 | Title editable in place; then stops following; clearing resumes | New note `Gym locker code 4512` → tap title, append ` edited` → edit body → title unchanged. Select all + delete in title → edit body | PASS: the title stayed `… edited` after body edits. After clearing, it followed the body again (`Gym locker code 4512 mo… · 31 Oct 2026`) | – |
| 4 | Existing note never re-derives | Open Wifi note → replace body with `Completely different` → Back | PASS: title still `Wifi router admin · 31 Oct 2026` | – |
| 5 | Empty new note discarded on Back | + → Back | PASS: still 2 notes | – |
| 6 | Grid default, 2 columns, title/preview/date bottom-right, no avatar | Fresh install → Notes | PASS: toggle reads "List view". Cards at x 84–488 and 593–996, date right-aligned at the card bottom. No monogram or avatar node | 01-grid-default.xml |
| 7 | List toggle: full-width cards, 2 preview lines, icon describes the next layout | Tap toggle | PASS: cards span x 84–996, toggle reads "Grid view". Toggling back gives "List view" | 02-list-view-after-relaunch.xml |
| 8 | Layout survives relaunch and lock/unlock | List view → force-stop → relaunch → unlock → Notes | PASS: still list | 02-list-view-after-relaunch.xml |
| 9 | Search hidden by default; magnifier reveals field with focus; back arrow + clear; filter and "No results" | Tap Search → `gym` → Clear → `zzz` → system Back; reopen → `wifi` → back arrow → reopen | PASS: field focused, keyboard shown, back arrow "Close search" on the left, "Clear search" X on the right. `gym` → 1 card. Clear → 2 cards. `zzz` → "No results". Back and the arrow both hide the row and restore the full list. On reopening, the field is empty | – |
| 10 | Lock while search open: nothing visible; no note text after unlock | Search `wifi` open → power off/on → PIN → app | PASS: the Unlock screen has no note nodes. After unlock, no editor text remains. See Issue 1 for the search query | 03-search-query-kept-after-lock.xml |

## Issues found
1. **Minor (privacy/UX):** the search query survives a lock. Repro: Notes → Search → type `wifi` → power off/on → unlock → Notes tab. The search row is still open with `wifi`, and the list is filtered. The query lives in `NotesViewModel._searchText`, which the lock observer doesn't reset (only `editor` and Undo). That part dates from 11. Feature 14's `rememberSaveable(searchText.isNotEmpty())` now also reopens the row. The query isn't note text, but it can reveal what was searched. Suggest also clearing `_searchText` on lock.
- Note: relative dates read "0 min. ago" for fresh notes (existing `DateUtils` behaviour, kept per the brief).
