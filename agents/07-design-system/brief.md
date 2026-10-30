# Design system and modern UI

Status: DONE
Backlog ID: 07

## Goal
A simple, calm, "secure-feeling" teal UI in light and dark mode, driven by one set of design tokens so a future redesign only touches `ui/theme/`. All existing screens are restyled with these tokens.

## Scope
- In:
  - Tokens in `ui/theme/` (see `research/v2-features.md` § Design system, D6):
    - `Color.kt`: full M3 light + dark schemes from teal seed `#00796B` (Material Theme Builder, static). Remove the template purple colours and `Neutral`.
    - `ExtendedColors` (`success`, `onSuccess`, `warning`, `onWarning`) for light/dark, via `staticCompositionLocalOf`, read as `MaterialTheme.extendedColors`.
    - `Type.kt`: full `Typography` scale with Poppins at explicit weights (fix the missing `weight =` bug); delete unused font files (black, thin, extralight, extrabold, light if unused) and the empty `res/font/dd.xml`.
    - `Shape.kt` (M3 `Shapes`), `Spacing.kt` (`object Spacing`).
    - `Theme.kt`: `AppTheme(themeMode)` replaces `PasswordManagerTheme`; no dynamic colour; status/navigation bar colours and icon contrast correct in both modes.
  - Theme setting: `ThemeMode { SYSTEM, LIGHT, DARK }` in `SettingsStore` (default SYSTEM), a selector in Settings, applied immediately (also on lock/setup screens).
  - `values/themes.xml` + `values-night/themes.xml`: window background = theme background (no white flash in dark).
  - Shell: standard M3 `NavigationBar` (Passwords, Favorites) + Scaffold FAB (add credential sheet, shown on Passwords/Favorites). Delete `BottomNavCustomShape` and `MyBottomAppBarWithFab`. Keep the existing hide-on-Edit / hide-while-keyboard-open behaviour.
  - Shared components in `presentation/components/`: `AppTopBar`, `AppTextField` (replaces `TextFireldComponent.kt`), `SearchField`, `ItemRow` (monogram avatar, title, subtitle, trailing slot), `EmptyState`, `SectionHeader`, `ConfirmDialog` (secure). Restyle `PasswordTextField`, `PasswordStrengthIndicator` (Weak = error, Fair = warning, Good/Strong = success), `GeneratorSheet`, `CopiedMessage`.
  - Restyle every screen with tokens/components: Setup, Unlock (`LockScreenLayout`), Home list, Favorites, Add/Edit sheet, Detail, Settings + Backup section/dialogs. Empty states for empty list and no search results.
- Out:
  - Renaming to "Vaultly", icon, splash, welcome copy (08).
  - New features or behaviour changes; moving strings to `strings.xml`; dynamic colour; animations beyond M3 defaults; tablet layouts.

## Acceptance criteria
- [ ] Light mode (system light): all screens listed above use teal primary, consistent spacing/shapes, no purple anywhere. Screenshot each.
- [ ] Dark mode (system dark): same screens readable, no white flash on cold start, status bar icons visible. Screenshot each.
- [ ] Settings → Theme: Light / Dark / System switches the whole app immediately, persists after force-stop + relaunch, and applies on the Unlock screen.
- [ ] Android 12+ emulator with a non-teal wallpaper: app colours stay teal (dynamic colour off).
- [ ] Bottom bar: standard M3 bar with Passwords and Favorites; FAB opens the add-credential sheet; bar/FAB hidden on Edit and while the keyboard is open.
- [ ] Empty vault shows the empty state; a search with no hits shows "no results".
- [ ] Regression: setup, unlock (password + biometric), add, edit, delete + Undo, copy, search, sort, favourite, generator, strength meter, auto-lock, export/import all still work.
- [ ] `grep -rn "Color(0x" app/src/main/java --include=*.kt | grep -v ui/theme` returns nothing; no `.sp` literals outside `ui/theme`.
- [ ] Dialogs/sheets still block screenshots (FLAG_SECURE): try a screenshot with the generator sheet and a backup dialog open.

## Design notes
- Affected files/layers: `ui/theme/*` (rewrite), `ui/theme/shapes/BottomNavCustomShape.kt` (delete), `MainActivity.kt` (shell, theme mode), `presentation/**` screens and components, `data/settings/SettingsStore.kt` (+ `ThemeMode`), `SettingsViewModel`/`SettingsScreen`, `res/values*/themes.xml`, `res/values/colors.xml` (remove unused template colours), `res/font/`.
- Theme mode must be observable before unlock: expose it from `SettingsStore`/a small holder in `AppContainer` as `StateFlow<ThemeMode>` and collect it in `MainActivity.setContent`.
- `material3:1.2.1` is enough (`TopAppBar`, `NavigationBar`, `ListItem`, `SegmentedButton` for the theme selector). Do not bump the BOM/Kotlin.
- Keep routes/ViewModels unchanged; this is a presentation-layer change.
- Data model / migration changes: none.
- Security considerations: every new dialog/sheet passes `SecureFlagPolicy.SecureOn`; masked passwords stay masked by default; theme pref is non-sensitive (plain prefs).

## Critical logic needing unit tests
- `SettingsStore` theme mode: default SYSTEM, round trip, unknown stored value falls back to SYSTEM (extend `SettingsStoreTest`).
- No UI tests.

## Dependencies
- Features 01–05 (DONE). Must land before 08–11 so new screens use the tokens.
