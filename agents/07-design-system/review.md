# Review – Design system and modern UI

## Round 1
- Commit range reviewed: 165822f..1c9099f
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | presentation/HomeScreen/CredentialScreen.kt:84 | Right after unlock, Home shows "Your vault is empty" until the first DB emission arrives, because the `stateIn` initial value is an empty list (the developer flagged this). Before, the list was just blank. In a password manager this message can make the user think their data is gone. | Optional: add a "loaded" flag to `CredentialState`, or make the initial value nullable, and show the empty state only after the first emission. This is a small VM change, so it can wait for a later feature. |
| 2 | minor | ui/theme/Theme.kt:13 | `ui/theme` now imports `data.settings.ThemeMode`, so the theme layer depends on the data layer. It is harmless today. | Optional: `AppTheme(darkTheme: Boolean)`, with the mode resolved in `MainActivity`. Fine to leave as is. |
| 3 | minor | presentation/SettingsScreen/SettingsViewModel.kt:66,79 | `themeMode` is copied into `SettingsState`, but `SettingsStore` already exposes it as a `StateFlow`. The copy follows the existing `autoLockTimeout` pattern, and both write paths keep the two in sync. | Optional: read `settingsStore.themeMode` directly in the screen. Not required. |
| 4 | minor | components/EmptyState.kt:31, ItemRow.kt:52, LockScreenLayout.kt:53 | Icon/avatar sizes (48/40/64 dp) are literals outside `ui/theme`. They are allowed under the brief's grep criteria, which only cover colours and sp. | Optional: an `IconSize` token object if more of these appear. |

### Checklist
- [x] Correctness / no obvious bugs. Theme mode reaches the Activity and Settings through the single `AppContainer.settingsStore`, so a change re-themes the whole app immediately, including Setup and Unlock. Unknown stored values fall back to SYSTEM. Dynamic colour is gone. Status/nav bar icon contrast is fixed (the old code had it inverted). Bottom bar and FAB are still hidden on Edit and while the keyboard is open, and the FAB shows only on Passwords and Favorites. The delete → Undo snackbar path, search, sort, favourite, copy, the generator and the strength meter are wired the same as before. `windowLightNavigationBar` is OK with minSdk 27, and `statusBarColor` is OK with targetSdk 34.
- [x] Simple, no over-engineering, matches codebase style. Components are thin wrappers that are actually used. `CredentialList` is shared by Home and Favorites. The unused `TextFireldComponent.kt`, `BottomNavCustomShape` and `MyBottomAppBarWithFab` are deleted. `grep "Color(0x"` and `.sp` outside `ui/theme` both return nothing. The only colour outside the theme is `Color.Transparent` in `ItemRow`.
- [x] Security. Nothing is logged. Activity FLAG_SECURE is unchanged. The add and generator sheets plus all backup dialogs, including the new `ConfirmDialog`, pass `SecureOn`. The new `DropdownMenu` popup uses the default `SecureFlagPolicy.Inherit`, so it takes FLAG_SECURE from the activity window. Passwords stay masked by default, and the row's reveal state is still keyed by credential id.
- [x] Critical business logic has unit tests; no needless tests. `SettingsStoreTest` covers default, round trip for every mode (same store and a new store) and the unknown-value fallback, exactly as the brief asks. No needless tests.
- [x] Commit messages follow convention and are concise. All Conventional Commits, one logical change each, no bodies. Code comments are sparse and explain the why (insets, window background sync, FLAG_SECURE).
- [x] Build + unit tests pass. `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, 135 tests, 0 failures.

### Deviation: Home row actions moved into a "More actions" menu
Accepted. The brief requires an `ItemRow` with a monogram and a "trailing slot". It does not require four inline buttons, and four 48 dp buttons plus a 40 dp avatar would squeeze the title at phone width. The two most frequent actions, Copy and Favourite, stay inline. Show/Hide password and Delete are still available from the row's overflow menu, and both remain on the Detail screen. Putting Delete behind a menu also makes accidental deletes less likely, and Undo is kept. The cost is one extra tap to reveal a password from the list, which is reasonable. The verifier should run the regression flows (reveal, delete + Undo) through the menu.
