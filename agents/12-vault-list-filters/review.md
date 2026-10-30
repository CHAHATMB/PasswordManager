# Review – Vault list filters

## Round 1
- Commit range reviewed: 4a93f64..1c0242a
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | commits f87a44d / c332b25 | `FavoriteCredentialScreen.kt` deletion landed in the `test:` commit f87a44d, which doesn't compile on its own. History must not be rewritten. | Orchestrator cherry-picks f87a44d and c332b25 together. No code change. |
| 2 | minor | commit 2439c4b | One commit holds two changes: the eye button with auto-hide, and moving the favourite toggle into the menu. They touch the same row, so this is acceptable. | None. Keep one change per commit next time. |
| 3 | minor | `presentation/AddScreen/AddCredentialScreen.kt:136`, `CredentialViewModel.kt:106` | Saving the edit screen without changing anything still sets `updatedAt`, so the row moves to the top of "Recently modified". The brief allows this ("editing moves it to the top"). | Optional: skip the stamp when the edited credential equals the original. Not required. |
| 4 | minor | `data/CredentialSort.kt`, `data/CredentialDao.kt:117` | The SQL matches enum constant names as string literals, so a rename silently breaks sorting. The KDoc warns about this, and the DAO tests would catch it. | None. |

### Checked, no issues
- DAO: `(:favoritesOnly = 0 OR isFav = 1)` has the OR in brackets correctly. MODIFIED uses `MAX(updatedAt, dataCreate) DESC, dataCreate DESC`, so legacy rows (`updatedAt = 0`) fall back to the date added. Tests cover both cases and the tie-break.
- `updatedAt` is set only by Save (`= dataCreate = clock`) and Update. Favourite (list and detail) and Restore leave it unchanged. Import relies on the Kotlin default of 0.
- Migration: `AutoMigration(3, 4)`. `4.json` is committed with `updatedAt INTEGER NOT NULL DEFAULT 0` on Credential only. The migration test opens v3 and checks `updatedAt = 0`, plus the credential and note data.
- Favourites tab removed everywhere: `NavigationItem.Favorite`, `Screen.FAVORITE`, the route, the nav bar, `FAB_ROUTES`, the screen file, `viewModel.favorites`, and the repo/DAO `getFavorites`. Nothing references them any more. Now that `CredentialList` has no other user, it is private.
- Reveal state is `remember(credential.id)` in the row, with a keyed `LaunchedEffect` that hides it after 30 s. `VaultShell` leaves composition on lock, so the state is dropped. Nothing about the reveal reaches the ViewModel or saved state, and nothing is logged.
- Search row, sort menu (with a check on the selected option) and empty states match the brief. The top-bar sort action is removed.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise (see #1, #2)
- [x] Build + unit tests pass (changed classes: CredentialDaoTest, CredentialDatabaseMigrationTest, CredentialRepositoryTest, CredentialViewModelTest, VaultSessionTest – 46 tests, 0 failures)
