# Dev Log – Design system and modern UI

## Round 1
- Base commit: 165822f6cac3c26a2d5dea2ec210c877ed155426
- Commits (review range `165822f..HEAD`):
  - 22bb835 feat: persist an observable theme mode setting
  - 54db42d test: cover theme mode default, round trip and fallback
  - edf5e15 feat: add teal design tokens and AppTheme with light and dark schemes
  - 28de759 feat: add shared top bar, field, list row, empty state and dialog components
  - 89b24d7 feat: replace the notched bottom bar with an M3 navigation bar and FAB
  - d36a37a feat: restyle password field, strength meter and generator with theme tokens
  - 93f6502 feat: restyle the setup and unlock screens
  - 2d29996 feat: restyle the password lists and add empty and no-results states
  - ae72eb8 feat: restyle the detail and add/edit screens
  - ef76411 feat: restyle Settings and the backup section
  - 0fca193 feat: add a System/Light/Dark theme selector to Settings
  - plus the `docs:` commit for this log
- Summary of changes:
  - `data/settings/SettingsStore.kt`: `ThemeMode { SYSTEM, LIGHT, DARK }` (DEFAULT = SYSTEM), `themeMode: StateFlow<ThemeMode>` + `setThemeMode()`, key `theme_mode`, plain prefs. Unknown value → SYSTEM.
  - `ui/theme/`:
    - `Color.kt`: full `LightColors`/`DarkColors` (all M3 1.2 roles incl. surfaceContainer*), `ExtendedColors` (success/onSuccess/warning/onWarning) light+dark, `LocalExtendedColors` (`staticCompositionLocalOf`), `MaterialTheme.extendedColors`. Purple colours and `Neutral` removed.
    - `Type.kt`: Poppins Regular/Medium/SemiBold with explicit `FontWeight`s; full 15-style M3 scale.
    - `Shape.kt` (4/8/12/16/28 dp), `Spacing.kt` (`object Spacing` xs..xxl = 4/8/12/16/24/32 dp).
    - `Theme.kt`: `AppTheme(themeMode)` replaces `PasswordManagerTheme`; no dynamic colour; status + navigation bar = `surface`, light/dark icons set correctly (old code inverted them).
    - `shapes/BottomNavCustomShape.kt` deleted.
  - Resources: `values/themes.xml` + new `values-night/themes.xml` set window/status/nav bar background and light-bar flags; `colors.xml` now only `background_light`/`background_dark`. Deleted `font/dd.xml` and Poppins black/thin/extralight/extrabold/light/bold.
  - `MainActivity`: collects `container.settingsStore.themeMode` in `setContent` and wraps everything (Setup/Unlock too) in `AppTheme`. Shell: standard `NavigationBar` (Passwords, Favorites) + Scaffold `FloatingActionButton` (Passwords/Favorites only) opening the add sheet. Bar and FAB hidden on Edit and while the keyboard is open (same condition as before). `MyBottomAppBarWithFab` deleted.
  - `NavigationItem.Home`: title "Passwords", Key icons; Favorite title "Favorites". Routes unchanged.
  - New `presentation/components/`: `AppTopBar` (M3 `TopAppBar`), `AppTextField`, `SearchField`, `ItemRow` (monogram, title, subtitle, trailing slot), `EmptyState`, `SectionHeader`, `ConfirmDialog` (FLAG_SECURE). `TextFireldComponent.kt` deleted (was unused).
  - Restyled: `PasswordTextField` (built on `AppTextField`, param `placeholder` → `label`), `PasswordStrengthIndicator` (Weak = error, Fair = warning, Good/Strong = success), `GeneratorSheet`, Setup/Unlock (`LockScreenLayout` with shield icon), Home + Favorites (shared `CredentialList`, empty vault / no results / no favourites states), Detail, Add/Edit, Settings (+ Theme segmented selector), Backup section (CSV warning now uses `ConfirmDialog`).
- Decisions / deviations from brief:
  - Colours: the primary roles are the reference values from `v2-features.md`. The other roles come from the classic Theme Builder tonal palettes for seed `#00796B` (secondary chroma 16, tertiary hue +60 / chroma 24, neutral 4, neutral variant 8), generated with `materialyoucolor` (the scheme algorithm used by Material Theme Builder). Success/warning are the tone 40/100 and 80/20 pairs of `#2E7D32` / `#EF6C00` (the old strength colours), not harmonised.
  - `poppins_bold.ttf` deleted as well: no style uses Bold. `poppinsFontFamily` is now private.
  - Home list row: the four inline buttons don't fit next to a monogram at phone width. The row keeps Copy and Favourite and moves Show/Hide password and Delete into a "More actions" menu (`DropdownMenu`, inherits FLAG_SECURE from the activity window). Every action still exists; delete still offers Undo.
  - `CopiedMessage` is a system Toast; nothing to restyle.
  - Settings got a top bar but no back arrow (system back works, as before).
- Unit tests added: `SettingsStoreTest` — default SYSTEM, every mode round trip (same store's flow + new store), unknown value → SYSTEM.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (135 tests, 0 failures), no Kotlin warnings.
- Notes for reviewer/verifier:
  - Grep checks: `grep -rn "Color(0x" app/src/main/java --include=*.kt | grep -v ui/theme` and `.sp` outside `ui/theme` both return nothing. The only remaining dp literals outside `ui/theme` are icon/avatar sizes (40, 48, 64 dp).
  - The app is still not edge-to-edge. `AppTopBar` passes `windowInsets = WindowInsets(0)` on purpose.
  - Cold-start window colour follows the system night mode, not the in-app setting. If the app is set to Dark while the system is Light (or the other way round), the starting window briefly shows the system colour before Compose draws. Light/Dark that matches the system, and System mode, have no flash.
  - Home briefly shows the "vault is empty" state right after unlock, until the first DB emission arrives (the `stateIn` initial value is an empty list; VMs left unchanged per brief).
  - The system navigation bar is `surface`, and the M3 `NavigationBar` is `surfaceContainer`, so there is a faint tonal step below the tab bar.
  - FLAG_SECURE: the generator sheet and add sheet use `ModalBottomSheetDefaults.properties(SecureOn)`. Backup dialogs use `secureDialog` and `ConfirmDialog`.

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
