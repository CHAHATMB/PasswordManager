# Dev Log – Credential management

## Round 1
- Base commit: 8762249319711ca8e7804b3e5865b22a7805baf5
- Commits (review range `8762249..HEAD`):
  - 5ff1b5f fix: rethrow cancellation from credential writes instead of Locked
  - 01cd609 fix: keep running when turning biometrics off fails to write the header
  - 3188d8f refactor: make Credential immutable and use data class equality
  - 8b183e5 feat: search credentials by website or username from Home
  - d246700 fix: list favourites from the DAO query without placeholder gaps
  - ebb79ee feat: add a sensitive clipboard that clears itself and on vault lock
  - 2e85598 test: cover clipboard clear timing and ownership checks
  - 829ff09 feat: copy a password from its row through the secure clipboard
  - f488631 feat: open a credential detail screen from Home and Favorite rows
  - 29c6d59 fix: describe the favourite and sort icon buttons for accessibility
  - b89fb1f fix: keep the add form and setup button reachable above the keyboard
  - 4b14c72 fix: label the add form button Save
  - 1082357 feat: edit a credential in the pre-filled add form
  - f185b76 feat: offer Undo after deleting a credential
  - 53e10bd fix: highlight the bottom bar item of the current back stack entry
  - 7451f2f test: cover credential edit, restore after delete and favourite toggle
  - plus the `docs:` commit for this log
- Summary of changes:
  - Data:
    - `Credential`: all fields `val`; the partial `equals`/`hashCode` is gone, so data class equality covers every field.
    - `CredentialDao`: `search(pattern, sortByWebsite)`, `getFavorites(sortByWebsite)` and `getById(id): Flow<Credential?>`. They replace `getOrderedByWebsite`/`getOrderedByDateCreated`/`getFavoriteCredential`. Both list queries share `ORDER BY CASE WHEN :sortByWebsite THEN website END, dataCreate`.
    - `CredentialRepository`: `search(query, sortByWebsite)`, `getFavorites`, `getById` (null while locked). `whileUnlocked` is now generic with a locked value. `likePattern(query)` trims and escapes `\ % _`, so typed wildcards match literally (`ESCAPE '\'`).
    - `write` rethrows `CancellationException` before the `IllegalStateException` catch (02 review R2 #1). It is `@VisibleForTesting internal` so the mapping can be tested directly.
  - Clipboard (`util/SecureClipboard.kt`):
    - `ClipboardAccess` (label / set / clear) with the Android `SystemClipboard`: `EXTRA_IS_SENSITIVE` on 33+, the literal `android.content.extra.IS_SENSITIVE` key on 27–32; `clearPrimaryClip()` on 28+, an empty clip on 27.
    - `SecureClipboard.copy(text)` sets clip label `Password Manager` and (re)starts a 30 s timer on `AppContainer.applicationScope` (`SupervisorJob + Dispatchers.Main.immediate`), not the ViewModel scope. `clearNow()` clears only a copy whose timer is still pending.
    - `AppContainer` collects `vaultSession.state` and calls `clearNow()` on any non-Unlocked state.
    - Username and password copies both go through it. Below API 33 a Toast says "Password copied, clears in 30 s" (`presentation/components/CopiedMessage.kt`); 33+ relies on the system confirmation.
  - UI:
    - Home: search field under the top bar (clear button), filters as you type through the DAO. Row: tap opens the detail screen; new copy-password icon.
    - Detail (`presentation/DetailScreen/CredentialDetailScreen.kt`, route `DETAIL/{credentialId}`): loads by id through `getById`; website, username + copy, masked password + reveal + copy, note (hidden when blank); top bar Back, favourite, Edit, Delete.
    - Edit (route `EDIT/{credentialId}`): `AddCredentialScreen(editing = credential)` pre-fills, titles "Edit", and sends `UpdateCredential(editing.copy(...))`, so `id`, `dataCreate` and `isFav` are kept.
    - Delete (row or detail) → `deletedCredentials` channel → Snackbar "Deleted <website>" / "Undo" in the shell Scaffold → `RestoreCredential` upserts the same entity.
    - Favorite tab uses `CredentialViewModel.favorites` (DAO query, keyed items), so no empty placeholders; it shares `CredentialItem` (reveal, copy, open detail).
    - Bottom bar selection comes from `currentBackStackEntryAsState()`; `selectedItemIndex` is removed.
    - `CredentialViewModel`: the dead countries/search block and `OnToogleSearch` are removed; `SortCredentail` → `SortCredential`; new events `UpdateCredential`, `RestoreCredential`, `CopyUsername`, `CopyPassword`.
  - Carried over:
    - Add sheet: `skipPartiallyExpanded = true`; form column scrolls and has `imePadding()`. Activity: `windowSoftInputMode="adjustResize"`. `LockScreenLayout` (Setup/Unlock) scrolls.
    - Favourite/sort icon buttons have content descriptions. Add button reads "Save".
    - `LockViewModel.turnOffBiometric` catches `IOException` from `disableBiometric()`.
- Decisions / deviations from brief:
  - DAO: one `search(pattern, sortByWebsite)` instead of `search(query)` plus the two old ordered queries, so search and the sort toggle combine in SQL. Favourites follow the same sort toggle (the Favorite tab keeps its sort button).
  - LIKE wildcards are escaped (not in the brief): otherwise `_`/`%` in a query match any character.
  - "Only if the clip is still ours": checked by clip label. On Android 10+ an app in the background cannot read the clipboard (`primaryClipDescription` is null), so an unreadable clipboard counts as ours and is cleared. That can wipe another app's newer clip copied within those 30 s; leaving a password behind was judged worse. A readable clip with another label is never touched.
  - Lock clears only a copy whose 30 s timer is still pending, so cold start (initial `Locked`) never clears the user's own clipboard.
  - Username copies also use the sensitive, auto-clearing clipboard (one path, no extra code).
  - Undo uses `collectLatest`: a second delete replaces the first Snackbar, and the first delete can no longer be undone.
  - The Undo Snackbar lives in the shell Scaffold, so it also shows on the Favorite tab and after deleting from detail (detail pops back to the previous tab).
  - Bottom bar: on Detail, Edit and Settings neither item is highlighted (selection = exact route match).
  - Edit is a NavHost route, not the bottom sheet, so it can be reached from the detail screen and returns there on Save.
  - The favourite-toggle test is a repository test (persisted toggle plus the original instance unchanged), not a test of `copy` alone; the run's rules restrict tests to repository/search/clipboard/cancellation logic.
  - Keyboard fix relies on `adjustResize` (window resizes; `imePadding()` is a no-op in the main window because it is not edge-to-edge). In the add sheet, M3 1.2.1 applies `imePadding()` itself on 33+; the content's own `imePadding()` is for 30–32. Not verified on a device.
- Unit tests added (15 new, 81 total):
  - `CredentialRepositoryTest` (+5): a mid-write lock turns `IllegalStateException` into `VaultException.Locked` (previously untested); `CancellationException` during a locked write is rethrown, not mapped; edit keeps id, `dataCreate`, count and date-sort position; delete + restore gives an identical row (`getById` null in between); favourite toggle leaves the original unchanged, shows in favourites, and unfavouriting empties them.
  - `CredentialDaoTest` (+4, 1 extended): search matches website or username case-insensitively; blank/whitespace returns all; `_`, `%` and `\` match literally; `getById` emits the row, then null after delete; favourites are filtered and follow both sort orders.
  - `util/SecureClipboardTest` (6, virtual time, fake clipboard): not cleared at 29 999 ms, cleared at 30 000 ms; a newer copy restarts the timer; another app's readable clip is left alone; an unreadable clipboard is cleared; `clearNow()` clears a pending copy and cancels its timer; `clearNow()` with nothing pending does nothing.
  - Existing DAO/repository/session tests moved to the new `search`/`getFavorites` API.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (81 tests, 0 failures). Main code compiled before every commit; test sources changed only in 5ff1b5f, 8b183e5, 2e85598 and 7451f2f, where the full unit tests were run, and no other commit changes an API the tests use.
- Notes for reviewer/verifier:
  - Not run on a device (no emulator attached). Keyboard/sheet behaviour, the sensitive-clip preview and the clear timing are unverified at runtime.
  - Known edge cases (not fixed):
    - The deleted credential sits in the ViewModel channel until the shell collects it (immediately in practice). If the vault locked in that instant, Undo would be offered after the next unlock. A lock while the Snackbar is showing disposes the shell and drops it.
    - With `adjustResize`, the custom bottom bar and FAB ride above the keyboard while the Home search field has focus.
    - The search field value round-trips through a `StateFlow`; fast typing has been fine with this pattern, but watch for cursor jumps.
    - Double-tapping Delete on detail sends two delete events; the second is a no-op in Room but emits a second Snackbar for the same item (the first is replaced).
  - Manual test flow (package `com.example.passwordmanager`; ideally one API 33+ and one API 28–32 emulator; set auto-lock to 15 min in Settings first so it doesn't interfere):
    1. `adb uninstall com.example.passwordmanager`, `./gradlew installDebug`, launch. Setup: tap the confirm field → the keyboard opens and "Create vault" is visible above it, or reachable by scrolling, without dismissing the keyboard. Create with `correcthorse1`.
    2. FAB → the sheet opens fully expanded and "Save" (not "Added") is visible. Tap Password → the field stays visible above the keyboard and Save is reachable by scrolling. Add, in this order: `github.com / alice / gh-secret-1 / note "work"`, `gitlab.com / bob / gl-secret-2`, `example.org / GitFan / ex-secret-3`.
    3. TalkBack or `adb shell uiautomator dump` + `cat`: the sort button reads "Sort by date or website", row hearts read "Add to favourites" / "Remove from favourites".
    4. Detail: tap the `github.com` row (not an icon) → the title and Website show `github.com`, Username `alice`, Note `work`, Password `••••••••`. Tap the eye → `gh-secret-1`; tap again → masked.
    5. Edit: on detail tap Edit → form titled "Edit" pre-filled with all four values. Change the username to `alice2`, Save → back on detail showing `alice2`. Back → Home still has 3 rows in the same date order (github, gitlab, example) with `alice2`. Toggle sort twice and recheck.
    6. Copy (API 33+): on the `github.com` row tap the copy icon → system "Copied" toast with the content hidden in the clipboard preview (sensitive). Paste into Home search (long-press → Paste) → `gh-secret-1`; clear the search. Start a timer, wait 31 s, long-press the search field → no Paste option, or pasting yields nothing. Also check with Chrome: copy, switch to Chrome, paste into the URL bar within 30 s → correct; stay in Chrome, wait 31 s, paste → empty.
    7. Copy (API 28–32): same as 6, plus a Toast "Password copied, clears in 30 s". On detail, copy username → "Username copied, clears in 30 s", and pasting gives `alice2`. On API 27, after 30 s the clipboard holds an empty text clip.
    8. Ownership: copy a password, then within 30 s copy some text inside the app's search field (select → Copy) and stay in the app → after 30 s that text is still pasteable.
    9. Clear on lock: copy a password, tap the Lock icon immediately → unlock → paste into search → nothing. Repeat with screen off (`adb shell input keyevent KEYCODE_POWER` twice) within 30 s.
    10. Cold start: copy some text in another app, force-stop ours, launch it → that text is still in the clipboard (no clear without our own pending copy).
    11. Delete + Undo: on Home tap a row's delete → the row disappears and a Snackbar "Deleted gitlab.com" with "Undo" appears. Tap Undo → the row returns with the same username/password/note and the same date position. Delete again and let the Snackbar expire (~4 s) → gone. Force-stop, relaunch, unlock → still gone. From detail: open `example.org` → Delete → back on Home with the Undo Snackbar; Undo restores it.
    12. Search (rows left: github.com/alice2, example.org/GitFan): type `HUB` → only github.com; `gitf` → only example.org (username match); `GIT` → both; `_` → no rows (literal). Tap X → both rows.
    13. Favourites: heart `example.org` and `github.com` on Home → Favorite tab shows exactly those two with no blank gaps; reveal, copy and tap-to-detail work there. Unheart one on the Favorite tab → it disappears immediately. Delete one from the Favorite tab → Undo Snackbar shows there too.
    14. Bottom bar: Home → Favorite (highlight moves) → system Back → Home is shown and highlighted.
    15. `adb logcat` during 4–13: no `gh-secret`, `gl-secret`, `ex-secret`, `correcthorse` or clipboard contents.

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
