# Dev Log – Vault list filters

## Round 1
- Base commit: 4a93f64 (branch `scratch/STRY-12-vault-list-filters`)
- Commits (review range `4a93f64..HEAD`):
  - 72a780e feat: add Credential.updatedAt with a 3 to 4 auto-migration
  - f87a44d test: cover the 3 to 4 migration keeping credentials with updatedAt 0
  - c332b25 feat: replace the Favourites tab with a favourites filter and sort menu on Passwords
  - 19abd70 feat: stamp updatedAt on save and edit from an injected clock
  - 2439c4b feat: add an eye button with 30 s auto-hide and move the favourite toggle into the row menu
  - f8c5dd7 test: cover the favourites filter and the three sort orders in the credential search
  - 5b7ef2a test: cover updatedAt stamping, favourite toggle, sort and favourites filter in the view model
  - plus the `docs:` commit for this log
- Summary of changes:
  - `Credential.updatedAt` (`@ColumnInfo(defaultValue = "0")`, Kotlin default 0, so backup/CSV import code is unchanged). DB v4 via `AutoMigration(3, 4)`, `app/schemas/.../4.json` committed.
  - `data/CredentialSort.kt` (`CREATED`, `WEBSITE`, `MODIFIED`). `CredentialDao.search(pattern, favoritesOnly, sort)` with `(:favoritesOnly = 0 OR isFav = 1)` and a three-mode `CASE` ORDER BY; MODIFIED = `MAX(updatedAt, dataCreate) DESC, dataCreate DESC`. `getFavorites` removed from DAO and repository.
  - `CredentialViewModel`: `sort` and `favoritesOnly` StateFlows (exposed like `searchText`), combined into one `repository.search`. Events `SetSort(sort)` and `ToggleFavoritesOnly` replace `SortCredential`. Injected `Clock`: Save sets `dataCreate = updatedAt = now`, Update sets `updatedAt = now`; Favourite/Restore unchanged.
  - Favourites tab removed (`NavigationItem.Favorite`, `Screen.FAVORITE`, route, nav-bar entry, `FAB_ROUTES`, `FavoriteCredentialScreen.kt`, `viewModel.favorites`).
  - `CredentialScreen`: search row = `SearchField` (weight 1) + heart filter toggle + `SortMenu` dropdown (checked option). Top-bar sort action removed. "No favourites yet" empty state when the filter is on with a blank search. `CredentialList` now private.
  - Row: Copy, Eye (Visibility/VisibilityOff), ⋮ with "Add to/Remove from favourites" and "Delete". Reveal stays `remember(credential.id)`, auto-hides after `PASSWORD_REVEAL_TIMEOUT_MS = 30_000`.
- Decisions / deviations from brief:
  - `CredentialSort` lives in `data/`, not `presentation/`: the DAO and repository take it, and data must not import presentation. Sort labels are a private extension in `CredentialScreen.kt`.
  - Room 2.6.1 binds the enum query arg by name, so no `sort.name` String was needed. The enum's KDoc warns that the SQL matches constant names.
  - Filter on + non-blank search with no hits shows the existing "No results" state. Filter on with an empty vault shows "No favourites yet".
- Unit tests added:
  - `CredentialDaoTest`: favourites filter combined with pattern and sort (replaces the `getFavorites` test); MODIFIED order with legacy `updatedAt = 0` rows and an edited old row first; MODIFIED tie-break by newer `dataCreate`. CREATED/WEBSITE covered by the existing order test.
  - `CredentialRepositoryTest`: favourites-toggle test now uses `search(..., favoritesOnly = true, ...)`.
  - `CredentialViewModelTest` (fixed `Clock`): Save stamps both dates; Update stamps `updatedAt` and keeps `dataCreate`; Favourite keeps `updatedAt`; `SetSort`/`ToggleFavoritesOnly` change the emitted list (each step's order is distinct, so a stale emission can't pass).
  - `CredentialDatabaseMigrationTest`: v3 → v4 keeps a credential (`updatedAt = 0`) and a note; `CURRENT_VERSION` = 4.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (241 tests, 0 failures)
- Notes for reviewer/verifier:
  - Commit hygiene: the `FavoriteCredentialScreen.kt` deletion was staged early and landed in f87a44d (test commit) instead of c332b25. f87a44d alone doesn't compile (MainActivity still referenced the screen); cherry-pick f87a44d and c332b25 together. Left as is because history must not be rewritten.
  - A VM test that waits for an order which never arrives fails by the `runTest` timeout, not by an assertion.
  - Verify: upgrade over the 11 build (3→4), "Recently modified" after editing an older credential, favourite toggle not moving it, eye auto-hide after ~30 s, FLAG_SECURE while revealed.

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
