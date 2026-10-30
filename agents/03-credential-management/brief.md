# Credential management

Status: PLANNED
Backlog ID: 03

## Goal
Users can find, view, edit, copy and delete credentials. Copied secrets disappear from the clipboard by themselves, and favourites work correctly.

## Scope
- In:
  - Detail screen (route `Detail/{id}`), opened by tapping a row:
    - Shows website, username, the masked password with a reveal toggle, and the note.
    - Copy buttons for the username and the password.
    - Edit, delete and favourite actions.
  - Edit: reuse the add form, pre-filled. Save upserts the same id and keeps `dataCreate`.
  - Delete from detail or a row: the item is removed and Home shows an "Undo" Snackbar. Undo re-inserts the same entity.
  - Row trailing action: copy the password without opening the detail screen.
  - Clipboard:
    - Mark the clip sensitive (`EXTRA_IS_SENSITIVE` on 33+, the literal key on 27–32).
    - Auto-clear it after 30 s, using a coroutine on the application `CoroutineScope` in `AppContainer`. Use `clearPrimaryClip()` on 28+ and an empty clip on 27.
    - Also clear it when the vault locks.
    - Show the app's own "copied, clears in 30 s" message only below API 33.
  - Search: a search field at the top of Home filters by website or username as the user types, via a DAO `LIKE` query. Remove the dead search block in `CredentialViewModel`.
  - Favourites:
    - Toggling uses `copy(isFav = !isFav)` with `val` fields. Remove the custom partial `equals`/`hashCode`.
    - The Favorite tab uses the DAO favourites query, so there are no empty placeholder items. It supports reveal, copy and opening the detail screen like Home.
  - Bottom-bar selection follows the back stack, using `currentBackStackEntryAsState`.
- Out: categories and tags, password history, trash or recycle bin, the generator (04), sort options beyond the existing date/website toggle.

## Acceptance criteria
- [ ] Tapping a row opens the detail screen with the correct website, username and note. The password is masked until the user taps reveal.
- [ ] Edit, change the username, Save: the detail and the list show the new username, the item count is unchanged, and the item's position under date sort is unchanged.
- [ ] Copy password, then paste into another app's field (for example the search field or the Chrome URL bar): the correct password is pasted. After 30 s, pasting yields nothing.
- [ ] Copy, then lock the vault (02): the clipboard is empty.
- [ ] Delete shows "Undo". Tapping Undo restores the item with the same data. Letting the Snackbar expire removes it permanently, and it is still gone after relaunch and unlock.
- [ ] Typing in search narrows the list by website or username (case-insensitive). Clearing the search shows all items.
- [ ] Favouriting an item shows it on the Favorite tab with no gaps between items. Unfavouriting removes it from that tab immediately.
- [ ] Pressing system Back from Favorite to Home moves the bottom-bar highlight to Home.

## Design notes
- Affected files/layers:
  - New `presentation/DetailScreen/`.
  - `AddScreen/AddCredentialScreen.kt` gets an edit mode.
  - `HomeScreen/CredentialScreen.kt`, `FavoriteCredentialScreen.kt`, `AppNavigation.kt`, `MainActivity.kt`.
  - `CredentialViewModel.kt`, `CredentialEvent.kt`: new events such as `UpdateCredential`, `RestoreCredential`, `CopyPassword`, `OnSearchTextChange`. Fix the event-name typos you touch.
  - `data/Credential.kt`, `data/CredentialDao.kt` (`search(query)`, `getById`), `data/CredentialRepository.kt`.
  - New `util/SecureClipboard.kt`.
- Data model / migration changes: none. `Credential` fields become `val`. The schema stays the same, so no version bump.
- Security considerations:
  - Decrypted values exist only in memory while unlocked, and the detail screen loads by id.
  - Clipboard clearing must not depend on the ViewModel scope.
  - Never log clipboard contents.

## Critical logic needing unit tests
- DAO search: matches on website and on username, is case-insensitive, and an empty query returns all items. Uses in-memory Room.
- Edit preserves `id` and `dataCreate`. Restore after delete re-inserts an identical row.
- The favourite toggle produces a new instance and leaves the original unchanged.
- `SecureClipboard` clear scheduling: clears after the timeout, and a newer copy restarts the timer. Use `kotlinx-coroutines-test` virtual time, with a fake clipboard wrapper.

## Dependencies
- 01 (repository, session). The clear-on-lock criterion needs 02's lock.

## Carried over from 01 verification (see agents/01-encrypted-vault/verify.md)
- [ ] Add/edit form fully visible: Save button reachable without dragging; keyboard (imePadding) does not cover fields
- [ ] Setup screen "Create vault" button not hidden by keyboard
- [ ] Icon buttons (favourite, sort) have contentDescription
- [ ] From 02 review round 2: `CredentialRepository.write` must rethrow `CancellationException` before catching `IllegalStateException`
- [ ] From 02 review round 2: `LockViewModel.turnOffBiometric` handles a failed header write (IOException) without crashing
- [ ] From 02 verify: add sheet save button is labelled "Added" (AddCredentialScreen.kt:151) — should read "Save"
