# Dev Log – Branding: Vaultly

## Round 1
- Base commit: 915df119bbe44e90956ce28457e0ea17c2191714
- Branch: `scratch/STRY-08-branding` (review range `915df11..HEAD`)
- Commits:
  - 0d85c85 feat: rename the app to Vaultly
  - 660087b feat: add the shield-lock adaptive launcher icon
  - 907a222 feat: show a branded splash screen on cold start
  - ae74846 feat: welcome the user on the unlock and setup screens
  - plus the `docs:` commit for this log
- Summary of changes:
  - Name: `app_name` = Vaultly; Home top bar "Vaultly"; `SecureClipboard.CLIP_LABEL` = "Vaultly"; `BackupException.UnknownFormat` and `BackupSection` "not a Vaultly backup file"; `BACKUP_FILE_NAME` = `vaultly-backup.json`; biometric unlock prompt title "Unlock Vaultly".
  - Icon: `colors.xml` `brand_teal` = `#FF006A60` (07 light `primary`). `ic_launcher_background` = solid `brand_teal` shape. `ic_launcher_foreground` = white Material Symbols `shield_lock` (Rounded, fill 1, downloaded from `google/material-design-icons` `symbols/android/shield_lock/materialsymbolsrounded/shield_lock_fill1_24px.xml`), scaled 0.06 and centred: glyph ≈ 38×47 dp, farthest point ≈ 24 dp from centre (safe-zone radius 33 dp). Source + Apache 2.0 comment in the XML. `mipmap-anydpi` launcher/round/monochrome already pointed at these layers (unchanged). Legacy `mipmap-*dpi/*.webp` deleted.
  - Splash: `androidx.core:core-splashscreen:1.0.1`; `Theme.App.Starting` in `values/` and `values-night/themes.xml` (parent `Theme.SplashScreen.IconBackground`); manifest activity theme → `Theme.App.Starting`; `installSplashScreen()` before `super.onCreate()` in `MainActivity` (FLAG_SECURE still set in `onCreate` before `setContent`).
  - Welcome: `LockScreenLayout(headline, message, …)` replaces `title`; header = `ic_shield_lock` (primary) + headline (`headlineSmall`) + message (`bodyMedium`, `onSurfaceVariant`). Unlock: "Welcome back" + brief text. Setup: "Welcome to Vaultly" + brief text.
- Decisions / deviations from brief:
  - Splash background = `background_light` / `background_dark` (same as the window and Compose background), with the teal icon background circle (`windowSplashScreenIconBackgroundColor = brand_teal`) behind the white glyph. Splash reads as the launcher icon and hands over to the lock screen with no colour jump. `brand_teal` is still the single colour shared by the icon and splash.
  - Splash icon reuses `ic_launcher_foreground` (same 2/3 safe-zone rule as the icon-with-background splash spec).
  - Lock header uses a new 24 dp `drawable/ic_shield_lock.xml` (the same glyph) instead of `Icons.Rounded.Shield`, so it matches the launcher icon. The path data therefore exists twice (this file and the launcher foreground); a vector can't reference another, and an inset wrapper would not scale with the launcher's icon size.
  - Setup keeps a short "Use at least N characters." line under the header; the brief's message replaces the old recovery warning.
  - Biometric titles never contained the old name; only the unlock title was changed to "Unlock Vaultly". The enable prompt stays "Turn on fingerprint unlock".
  - Favorites and Settings top bars keep their tab names ("Favorites", "Settings"); only Home showed the app name.
  - Internal style name `Theme.PasswordManager` and "Google Password Manager" in the CSV import copy stay (not our name / not user-visible).
- Unit tests added: none (no new business logic). `SecureClipboardTest` asserts against the `CLIP_LABEL` constant, so it needed no change.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (135 tests, 0 failures).
- Notes for reviewer/verifier:
  - Not run on the emulator. Check the icon in circle/squircle/square masks and themed icons (monochrome = same foreground).
  - Splash follows the system night mode, not the in-app theme setting (same caveat as 07's window background).
  - Upgrade install keeps data: `applicationId`, DB name, vault AAD and backup `format` are untouched. Old clipboard clips (label "Password Manager") aren't recognised as ours by the new build.
  - `grep -rn "Password Manager" app/src/main` only hits the Google Password Manager references.

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
