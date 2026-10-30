# Verify – Credential management

- Build commit: b008e2f (last code commit 6f20ee0). Debug via `./gradlew installDebug`, with FLAG_SECURE (activity) and the add sheet's `SecureFlagPolicy.SecureOn` turned off in a local, uncommitted edit for screenshots only; restored with `git checkout` afterwards.
- Device: AVD `Pixel_9a` (sdk_gphone64_arm64, API 36), headless. Fresh `pm clear`, master password `correcthorse1`, auto-lock 15 min.
- Result: PASS

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Detail shows correct data, password masked until reveal | Add `github.com/alice/gh-secret-1/work`, `gitlab.com/bob/gl-secret-2`, `example.org/GitFan/ex-secret-3`. Tap the github row → Website, Username `alice`, Note `work`, `••••••••`. Eye → `gh-secret-1`, again → masked | PASS | 07-detail-masked.png, 08-detail-revealed.png |
| 2 | Edit keeps count and date position | Detail → Edit (titled "Edit", all 4 fields pre-filled) → username `alice2` → Save → detail shows `alice2`. Back → 3 rows, order github, gitlab, example. Sort toggle twice → website order, then the same date order | PASS | 09-edit-prefilled.png, 10-detail-after-edit.png, 13-home-after-edit.png |
| 3 | Copy, paste, empty after 30 s | Row copy icon → system clipboard preview shows `••••••` (sensitive). Paste (KEYCODE_PASTE) into Home search → `gh-secret-1`. Paste again at 33 s → nothing. Detail copy username → paste `alice2`; detail copy password → `gh-secret-1`. Favorite-tab copy → `ex-secret-3` | PASS | 14-row-copy-system-toast.png, 15-paste-into-search.png, 16-paste-after-30s-empty.png, 18-detail-copy-username.png |
| 4 | Copy, then lock → clipboard empty | Copy gitlab (paste check gives `gl-secret-2`), copy again, top-bar Lock → unlock → paste → nothing. Repeat with screen off/on + device PIN, 27 s after copy → nothing | PASS | 17-paste-after-lock-empty.png |
| 5 | Delete → Undo restores; expiry removes permanently; gone after relaunch | Row delete gitlab → "Deleted gitlab.com / Undo"; left to expire → gone. Row delete example → Undo → back in position 2 with `GitFan/ex-secret-3`. Detail double-tap Delete → Home stays visible, one Snackbar; Undo restores. Delete example + Lock at once → unlock → no Undo Snackbar. Force-stop, relaunch, unlock → only github.com left | PASS | 19-delete-undo-snackbar.png, 20-undo-restored-detail.png, 21-detail-double-delete.png, 22-no-undo-after-lock.png |
| 6 | Search narrows by website or username, case-insensitive; clear restores | `HUB` → github; `gitf` → example (username); `GIT` → all three; `bob` → gitlab; `_` → none; Clear search (X) → all three | PASS (see issue 1) | 23-search-HUB.png, 24-search-GIT.png |
| 7 | Favourites tab, no gaps, unfavourite immediate | Heart example and github → Favorite shows exactly those 2, same row spacing as Home. Reveal, copy, tap-to-detail work. Unheart github → gone at once. Delete example there → Snackbar on the Favorite tab, Undo restores | PASS | 26-favorite-tab.png, 27-favorite-after-unfav.png, 28-favorite-delete-undo.png |
| 8 | System Back from Favorite highlights Home | Home → Favorite (highlight moves) → Back → Home shown and highlighted | PASS | 29-back-to-home-highlight.png |
| C1 | Add form: Save visible, labelled "Save", keyboard doesn't cover fields | FAB → sheet fully expanded, button "Save". Password focused with the keyboard up → all 4 fields and Save visible above the keyboard | PASS | 03-add-sheet-save.png, 03-add-sheet-keyboard.png |
| C2 | Setup "Create vault" not hidden by the keyboard; Setup/Unlock centred | Setup and Unlock content vertically centred with the keyboard closed. Confirm field focused → "Create vault" visible above the keyboard | PASS | 01-setup-centred.png, 02-setup-keyboard.png, 05-unlock-centred.png |
| C3 | Icon buttons have contentDescription | uiautomator: "Sort by date or website", "Settings", "Lock", "Copy password", "Show/Hide password", "Add to/Remove from favourites", "Delete", "Copy username", "Edit", "Back", "Clear search" | PASS | – |
| 9 | Logcat hygiene | Whole session (85k lines): 0 `FATAL EXCEPTION`, empty crash buffer; 0 hits for `gh-secret`, `gl-secret`, `ex-secret`, `correcthorse`, `alice2` | PASS | – |

Notes:
- `adb shell cmd clipboard` is not available on this image, so the clipboard was checked by pasting into the Home search field (as the brief allows).
- Not run: API 28–32 Toast (only an API 36 AVD was available), the Chrome paste, the ownership check (dev-log step 8) and the cold-start check (step 10).
- 11-edit-keyboard-save-hidden.png, 12-edit-keyboard-scrolled.png and 25-search-scroll-anchor.png show issues 1 and 2.

## Issues found
1. Low (cosmetic): Home rows draw through the search field once the list is scrolled. Repro: with 3 items, type `gitf` in search (one result), then replace it with `GIT` → the list keeps example.org anchored, so the github row scrolls up under the search field and its text overlaps the field (25-search-scroll-anchor.png). Cause: `CredentialScreen` gives the Scaffold padding to the `LazyColumn` as `contentPadding`, so rows scroll under the top bar, and the `OutlinedTextField` has no background. The same overlap shows on any scroll with enough rows. Filtering itself is correct.
2. Low: in the Edit route with the keyboard open, Save is pushed off-screen and the custom bottom bar and FAB ride above the keyboard (11-edit-keyboard-save-hidden.png). Save is reachable by scrolling the form (12-edit-keyboard-scrolled.png), so this meets the brief's "reachable" wording. It matches the known `adjustResize` limitation in dev-log.md. The add sheet is not affected.
