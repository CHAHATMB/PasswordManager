# Vault list filters

Status: IN_REVIEW
Backlog ID: 12

## Goal
The bottom bar loses the Favourites tab. On the Passwords list, a narrower search field sits next to a favourites filter and a sort menu that includes "Recently modified". Each row gets an eye icon that shows the password in place, and the favourite toggle moves into the row's overflow menu.

## Scope
- In:
  - Remove the Favourites tab: `NavigationItem.Favorite`, `Screen.FAVORITE`, its `composable`, its entries in the nav-bar list and `FAB_ROUTES` (`MainActivity.kt`), `FavoriteCredentialScreen.kt`, `CredentialViewModel.favorites`, `CredentialRepository.getFavorites`, `CredentialDao.getFavorites`.
  - Search row (`CredentialScreen.kt`): `Row` holding `SearchField` (`Modifier.weight(1f)`) and two `IconButton`s:
    - Favourites filter toggle: `FavoriteBorder` when off, filled `Favorite` tinted `primary` when on. Content description "Show only favourites" / "Show all passwords".
    - Sort menu (`Icons.AutoMirrored.Rounded.Sort`) opening a `DropdownMenu` with three options, the selected one checked: "Date added" (default, current order), "Name (A–Z)", "Recently modified". It replaces the top-bar Sort action, so the top bar keeps only Settings and Lock.
  - Data: new `updatedAt: Long` column on `Credential` (`@ColumnInfo(defaultValue = "0")`), DB v3 → v4 through `@AutoMigration(from = 3, to = 4)` (D11). Commit `app/schemas/.../4.json`.
  - `CredentialViewModel`: replace `isSortedByWebsite` with `sort: MutableStateFlow<CredentialSort>` (enum `CREATED`, `WEBSITE`, `MODIFIED`) and `favoritesOnly: MutableStateFlow<Boolean>`. Expose both to the UI (in `CredentialState` or as separate flows) and combine them with `searchText` into one `repository.search(query, favoritesOnly, sort)`.
    - Events: replace `SortCredential` with `SetSort(sort)`, add `ToggleFavoritesOnly`.
    - `UpdateCredential` sets `updatedAt = clock.millis()`. `SaveCredential` sets `updatedAt` equal to `dataCreate`. `FavoriteCredential` and `RestoreCredential` leave `updatedAt` unchanged. Inject `clock: Clock = Clock.systemDefaultZone()` as `NotesViewModel` does, and use it for `dataCreate` too.
  - DAO: one `search(pattern, favoritesOnly, sort)` query: `... AND (:favoritesOnly = 0 OR isFav = 1)`, with `ORDER BY` extended to three modes. "Recently modified" orders by `MAX(updatedAt, dataCreate) DESC`, then `dataCreate DESC`. Keep the existing `CASE` approach and its comment.
  - Row (`CredentialItem`): the trailing actions become Copy, Eye (`Visibility` / `VisibilityOff`, "Show password" / "Hide password") and overflow. The overflow holds "Add to favourites" / "Remove from favourites" (with heart icon) and "Delete". Remove "Show password" from the menu.
  - Reveal auto-hides after `PASSWORD_REVEAL_TIMEOUT_MS = 30_000` (`LaunchedEffect(isPasswordRevealed)` + `delay`).
  - Empty states: favourites filter on with no favourites: "No favourites yet" / "Add one from a password's ⋮ menu." Search/filter with no hits: the existing "No results" state.
- Out:
  - A favourite indicator on the row (the filter shows favourites). Persisting filter or sort across app restarts. Filter chips. A "modified in last N days" time-window filter. `updatedAt` in the backup format (import sets `updatedAt = 0`, which sorts by `dataCreate`). Changes to the detail screen (its favourite toggle stays). Notes/Authenticator lists.

## Acceptance criteria
- [ ] Bottom bar shows Passwords, Authenticator and Notes only. No Favourites tab, and Back never lands on a favourites screen.
- [ ] On the Passwords tab, the search field is narrower. A heart toggle and a sort icon sit to its right on the same line. The top bar has no sort icon.
- [ ] Heart toggle on: only favourites are listed, combined with the search text. Off: everything is listed. With no favourites: the "No favourites yet" state.
- [ ] Sort "Date added" shows oldest first (as today). "Name (A–Z)" is alphabetical by website. "Recently modified": editing an older credential moves it to the top. Toggling its favourite does not move it.
- [ ] Each row shows Copy, Eye and ⋮ (no heart). Eye shows the password in the row, and tapping again hides it. It also hides by itself after ~30 s, when the app locks, and after leaving the screen.
- [ ] ⋮ → "Add to favourites" marks the credential, and the label then reads "Remove from favourites". The detail screen's heart reflects the change.
- [ ] Screenshots of the list stay blocked while a password is shown (FLAG_SECURE unchanged).
- [ ] Upgrade install over the 11 build: credentials, authenticator accounts and notes are preserved (auto-migration 3→4). Existing credentials sort by date added under "Recently modified" until edited.

## Design notes
- Affected files/layers:
  - `data/Credential.kt`, `data/CredentialDao.kt`, `data/CredentialRepository.kt` and `data/CredentialDatabase.kt` (v4).
  - `presentation/CredentialViewModel.kt`, `CredentialState.kt` and `CredentialEvent.kt` (new `CredentialSort` enum lives here or in its own file in `presentation/`).
  - `presentation/HomeScreen/CredentialScreen.kt`, `presentation/AppNavigation.kt` and `MainActivity.kt`.
  - Delete `presentation/FavoriteCredentialScreen.kt`. `CredentialList` stays in `CredentialScreen.kt`, now private-able.
- UX decision: "next to the search bar" is read as icon buttons on the same line, not chips. Chips would add a second row, and the user wants a smaller header.
  - Favourites is a filter, so it gets a one-tap toggle with visible on/off state.
  - "Last modified" is an ordering, so it joins the existing sort, now a three-option menu instead of a blind two-way toggle.
- Why a new column: `Credential` has no modification time. `dataCreate` is kept on edit, so "Recently modified" can't be derived from it.
  - Default `0` plus `MAX(updatedAt, dataCreate)` avoids an `AutoMigrationSpec` backfill and needs no change to backup/CSV import.
  - The name `updatedAt` matches `Note`.
- Favourite toggle does not touch `updatedAt`: it isn't a content change and would make "Recently modified" noisy.
- Reveal state stays local `remember(credential.id)` in the row (as today). It is never in the ViewModel or saved state.
  - Composition disposal on lock, row scroll-off and navigation already hides it. The timer covers the "left open on screen" case.
- Sort/filter state lives in the ViewModel only and resets with the process. It is not sensitive.
- Room enum query param: pass `sort.name` as a String if Room's enum binding isn't available for query args.
- Data model / migration changes: `Credential.updatedAt` (INTEGER NOT NULL DEFAULT 0). DB v4 via `@AutoMigration(3, 4)`. No backup format change.
- Security considerations:
  - The revealed password only renders inside the existing FLAG_SECURE window. No logging, and no reveal state in the ViewModel, `SavedStateHandle` or the Bundle.
  - It auto-hides after 30 s and on lock.
  - Copy still goes through `SecureClipboard` (auto-clear unchanged).

## Critical logic needing unit tests
- `CredentialDao.search` (extend `CredentialDaoTest`, replace the `getFavorites` test):
  - `favoritesOnly` true/false combined with a search pattern.
  - Each sort mode gives the exact order: CREATED ascending; WEBSITE alphabetical; MODIFIED with a legacy row (`updatedAt = 0`) falling back to `dataCreate`, and an edited old row first.
- `CredentialRepository`: update the existing favourites-toggle test to use `search(..., favoritesOnly = true, ...)`.
- `CredentialViewModel` (extend `CredentialViewModelTest`, fixed `Clock`):
  - `UpdateCredential` sets `updatedAt` to clock time and keeps `dataCreate`.
  - `FavoriteCredential` flips `isFav` and keeps `updatedAt`.
  - `SaveCredential` sets `dataCreate == updatedAt == clock`.
  - `SetSort` / `ToggleFavoritesOnly` change the emitted list.
- `CredentialDatabaseMigrationTest`: 3 → 4 keeps an existing credential with `updatedAt = 0`.
- No UI tests.

## Dependencies
- 03 (favourites, search, delete/undo), 07 (`SearchField`, `ItemRow`, nav bar), 11 (DB v3 schema `3.json` must be on `main` before v4).
- Features 13/14/17 also touch `MainActivity.kt` / add screens. Merge order matters only for `MainActivity.kt` conflicts. No other DB changes are planned, so v4 is free.
