# Branding: Vaultly

Status: PLANNED
Backlog ID: 08

## Goal
The app is called "Vaultly", has a teal shield-lock launcher icon and splash screen, and greets the user with a friendly, reassuring message on the unlock and setup screens.

## Scope
- In:
  - Name: `strings.xml app_name = Vaultly` (launcher label + activity label); "Password Manager" → "Vaultly" in top-bar titles, `SecureClipboard.CLIP_LABEL`, biometric prompt titles, backup messages (`BackupSection`, `BackupException.UnknownFormat`), default backup file name `vaultly-backup.json`.
  - Launcher icon (D7): Material Symbols **`shield_lock`** (Rounded, filled), source https://fonts.google.com/icons?selected=Material+Symbols+Rounded:shield_lock , **Apache License 2.0** (https://github.com/google/material-design-icons/blob/master/LICENSE). Adaptive icon: `ic_launcher_background` = solid brand teal, `ic_launcher_foreground` = white glyph inside the 66 dp safe zone of the 108 dp canvas, `monochrome` = same glyph. `ic_launcher_round` uses the same layers. Delete legacy `mipmap-*dpi/ic_launcher*.webp`. Add a one-line attribution comment in the foreground XML (source + licence).
  - Splash: `androidx.core:core-splashscreen:1.0.1`; `Theme.App.Starting` (parent `Theme.SplashScreen`) with brand background (light/dark variants), the shield glyph as `windowSplashScreenAnimatedIcon`, `postSplashScreenTheme = @style/Theme.PasswordManager`; manifest activity theme → `Theme.App.Starting`; `installSplashScreen()` before `super.onCreate()`.
  - Welcome copy (in `LockScreenLayout` header: shield icon + headline + supporting text, styled with 07 tokens):
    - Unlock: headline "Welcome back", text "Your vault is locked and encrypted on this device. Enter your master password to continue."
    - Setup: headline "Welcome to Vaultly", text "Create a master password. It encrypts everything in your vault and can't be recovered, so keep it somewhere safe."
- Out:
  - Changing `applicationId`, package, `rootProject.name`, DB file name, vault AAD (`passwordmanager-vault-v*`), backup `format = "pm-export"` (all would break existing data — D7).
  - Animated (AVD) splash icon, Play Store assets, localisation.

## Acceptance criteria
- [ ] Launcher shows "Vaultly" with the teal shield-lock icon; it is not clipped in circle, squircle and square masks (Pixel launcher icon shape setting or different emulators).
- [ ] Android 13+: Themed icons on → monochrome shield shown.
- [ ] Cold start shows the splash (teal shield, brand background) then the setup/unlock screen; dark-mode splash background matches dark theme. No white flash.
- [ ] Unlock screen shows "Welcome back" + the supporting text; setup screen shows "Welcome to Vaultly" + warning text; both in light and dark.
- [ ] Top bars say "Vaultly"; no "Password Manager" / "PasswordManager" visible anywhere in the UI (check Home, Favorites, Settings, backup dialogs, biometric prompt).
- [ ] Upgrade install over the 07 build: existing vault still unlocks, credentials intact, an export made before the upgrade still imports.
- [ ] Copy a password → it is still auto-cleared after 30 s (new clip label).

## Design notes
- Affected files/layers: `res/values/strings.xml`, `res/values(-night)/themes.xml` (+ splash theme, colours), `res/drawable/ic_launcher_*.xml`, `res/mipmap-anydpi/*`, delete `res/mipmap-*dpi/`, `AndroidManifest.xml`, `MainActivity.kt`, `app/build.gradle.kts`, `LockScreenLayout`/`UnlockScreen`/`SetupScreen`, `SecureClipboard`, `BiometricPromptUtils`, backup UI strings.
- Brand teal for icon/splash = a colour resource matching the 07 `primary` (light) so XML and Compose agree; keep the value defined once in `colors.xml` and referenced by the splash theme and icon background.
- Changing `CLIP_LABEL` is safe: `isOwnClip` compares against the current constant and clips do not survive reboots; a clip copied by the old build just isn't recognised (cleared anyway if unreadable).
- Data model / migration changes: none.
- Security considerations: splash must not delay FLAG_SECURE (`setFlags` stays in `onCreate` before `setContent`); no secrets on splash/lock screens.

## Critical logic needing unit tests
- None beyond updating `SecureClipboardTest` if it asserts the label literal. No UI tests.

## Dependencies
- 07 (tokens, `LockScreenLayout` restyle).
