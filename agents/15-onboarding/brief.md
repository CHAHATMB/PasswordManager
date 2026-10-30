# Onboarding and setup biometrics

Status: PLANNED
Backlog ID: 15

## Goal
On first launch, a new user sees four short screens about what Vaultly does (passwords, authenticator, notes) and how it keeps data safe, and then creates the master password. While creating it, they can turn on fingerprint unlock straight away.

## Scope
- In:
  - Onboarding pager, shown before `SetupScreen` only while `VaultState.NotSetUp` and the onboarding flag is not set:
    - Pages (illustration + headline + 1–2 lines of text):
      1. Passwords: "All your passwords in one place". Mention search, favourites and the built-in generator.
      2. Authenticator: "Your 2-step codes, built in". TOTP codes from QR or setup key, and import from Google Authenticator.
      3. Secure notes: "Private notes, encrypted too". For Wi-Fi keys, PINs and recovery codes.
      4. Security: "Locked down by design". See the copy rules below.
    - Controls: a page indicator (dots), "Skip" (pages 1–3), "Next" (pages 1–3) and "Get started" (page 4). Skip and Get started set the flag and open `SetupScreen`.
    - System Back on pages 2–4 goes to the previous page. On page 1 it keeps the default behaviour (the app closes).
  - Onboarding flag: `SettingsStore.isOnboardingDone` (Boolean, key `onboarding_done`, default false) in the existing `settings` prefs. It is not secret.
  - Setup biometrics: `SetupScreen` gets a "Unlock with fingerprint" switch, off by default. It is shown only if `BiometricPromptUtils.canAuthenticate(context)` (`BIOMETRIC_STRONG`, enrolled). After the vault is created and unlocked, the existing enable prompt runs once.
- Out:
  - Real screenshots, Lottie or animated illustrations, video.
  - Replaying onboarding from Settings, an "About" page.
  - A biometric enrolment shortcut (sending the user to system settings when no fingerprint is enrolled).
  - Any change to the biometric key, wrapping or header format.
  - Localisation.

## Acceptance criteria
- [ ] Fresh install (or clear data): the splash is followed by onboarding page 1. Swiping and "Next" move through 4 pages, and the dots follow the current page.
- [ ] "Skip" on any page from 1 to 3 opens the master-password setup. "Get started" on page 4 does the same.
- [ ] Back on page 3 goes to page 2. Back on page 1 closes the app.
- [ ] After Skip or Get started, kill and relaunch the app before creating a vault: setup appears directly, with no onboarding.
- [ ] Kill the app during onboarding and relaunch it: onboarding starts again.
- [ ] Upgrade install over the previous build with an existing vault: Unlock appears and onboarding is never shown.
- [ ] The security page states only the facts in the copy rules. It looks right in light and dark mode, in portrait, and in landscape (the content scrolls and nothing is clipped).
- [ ] Emulator without an enrolled fingerprint: setup has no fingerprint switch.
- [ ] With an enrolled fingerprint:
  - Switch on, then "Create vault": the vault opens and the fingerprint prompt appears. `adb -e emu finger touch 1` turns it on, so Settings shows the toggle on. After lock and relaunch, the Unlock prompt appears automatically and the touch opens the vault.
  - Switch on, then cancel the prompt: the vault stays open, biometric unlock is off in Settings, and no error is shown.
  - Switch off: no prompt after creation, and biometric unlock is off in Settings.
- [ ] A failed setup, such as mismatched passwords, shows no prompt. Fixing the error and creating the vault with the switch still on shows the prompt once.
- [ ] Onboarding and setup screens are blocked from screenshots (FLAG_SECURE is activity-wide, so there is nothing new to do).

## Design notes
- Illustrations: vector icon compositions, not screenshots. Each page is a large tonal circle (`primaryContainer`/`secondaryContainer`) holding a main Material `Icons.Rounded.*` glyph, with 1–2 small badge glyphs on top. Examples: Key + Password, Shield + Timer, StickyNote2 + Lock, `ic_shield_lock` + PhoneAndroid. They use only `MaterialTheme.colorScheme`, `Spacing` and `Shapes`.
  - Why not screenshots:
    - They go out of date with every UI change.
    - They would need light/dark (and density) variants as PNGs, which adds APK size.
    - They would show made-up vault data.
    - They don't adapt to the theme.
  - Vectors cost no APK size (the icons are already in `material-icons-extended`), follow the theme automatically and stay in sync with the design tokens.
- Pager: `androidx.compose.foundation.pager.HorizontalPager` + `rememberPagerState { 4 }` (Compose BOM 2023.08 → foundation 1.5, needs `@OptIn(ExperimentalFoundationApi::class)`). Accompanist is not used. The indicator is a simple `Row` of dots (selected dot = `primary` and wider, others = `outlineVariant`). Next and Back call `animateScrollToPage`.
- Affected files/layers:
  - `presentation/Onboarding/OnboardingScreen.kt` (new): pager, pages, indicator, buttons. The page content is a private list of `(icon, headline, text)`.
  - `data/settings/SettingsStore.kt`: `isOnboardingDone` get/set.
  - `MainActivity.kt`, `NotSetUp` branch: `var showOnboarding by rememberSaveable { mutableStateOf(!settingsStore.isOnboardingDone) }`. Show `OnboardingScreen(onFinish = { settingsStore.isOnboardingDone = true; showOnboarding = false })`, otherwise `SetupScreen`.
    - An existing vault is never `NotSetUp`, so upgrade users never see onboarding, and no migration or flag backfill is needed.
    - The flag is written on finish, not at install or at vault creation, so a kill mid-onboarding replays it.
  - `presentation/LockScreen/SetupScreen.kt`: new params `isBiometricAvailable: Boolean`, and `onSetup(password, confirmation, enableBiometric)`. A `rememberSaveable` switch row (reuse the Settings switch-row style if one exists, otherwise use `ListItem` + `Switch`) between the confirm field and "Create vault", disabled while `isWorking`. Supporting text: "You can change this later in Settings."
  - `presentation/LockScreen/LockViewModel.kt`:
    - `setup(password, confirmation, enableBiometric)` stores a one-shot `isBiometricEnrolmentDue = enableBiometric`, but only after `session.setup` succeeds. Validation errors and exceptions leave it false.
    - `takeBiometricEnrolmentRequest(): Boolean` returns the value and clears it, using the same one-shot pattern as `takeBiometricUnlockCipher`.
    - Clear the flag on lock as well.
  - `MainActivity.kt`, `Unlocked` branch: `LaunchedEffect(Unit) { lifecycle.withResumed { if (lockViewModel.takeBiometricEnrolmentRequest()) showEnablePrompt() } }`.
- Enrolment flow decision: it reuses the existing Settings path unchanged:
  - `showEnablePrompt()` calls `SettingsViewModel.newBiometricCipher()`, which runs `BiometricKeyStore.newEncryptCipher()`.
  - It then shows a CryptoObject prompt with `enablePromptInfo()`.
  - `onBiometricAuthenticated` then calls `settingsViewModel.enableBiometric(cipher)`, which runs `VaultSession.enableBiometric`. That wraps the in-memory VK, which exists right after `setup` because setup leaves the vault unlocked.
  - This adds no new key handling and no extra code path in `VaultSession`.
  - The prompt has to come after vault creation: VK does not exist before then, and the CryptoObject encrypt has to happen inside the authenticated prompt.
- Failure handling:
  - Cancel, negative button, lockout or an error leaves biometric unlock off, with no message. The user can turn it on in Settings.
  - Keystore failure: `newBiometricCipher()` returns null and nothing is shown. The vault is already created and open, and a failed enrolment must never block or undo setup.
  - A lock while the prompt is open is already handled: `onBiometricAuthenticated` checks for `Unlocked`.
- Rotation: `LockViewModel` survives it, so the request survives too. Losing an open prompt on rotation is accepted, as in Settings.
- Copy rules for the security page: claim only what the code does.
  - Claims that are true:
    - Everything stays on this device. Vaultly has no internet access (no `INTERNET` permission). The developer must check the merged manifest and drop this line if any library adds the permission.
    - The whole vault is encrypted with SQLCipher (AES-256).
    - The master password is turned into the key with Argon2id. The password itself is never stored and can't be recovered.
    - Vaultly locks itself automatically, and screenshots are blocked.
    - Fingerprint unlock is optional. Its key is kept in the Android Keystore.
    - Backups are encrypted with a password you choose.
  - Claims that must not be made: "military-grade", "zero-knowledge certified", "audited", "hardware-backed" or StrongBox as a guarantee (StrongBox is used only when available), cloud sync, breach monitoring.
- Data model / migration changes: one new Boolean pref in `settings`. No DB or vault-header changes.
- Security considerations:
  - The onboarding flag is not sensitive and doesn't affect any security decision. Whether a vault exists is still decided only by the header.
  - Biometric unlock stays opt-in (switch off by default) and still requires `BIOMETRIC_STRONG` + CryptoObject (D5). Device PIN is never offered.
  - The master password handling in `setup` is unchanged (char array zeroed after use).

## Critical logic needing unit tests
- `SettingsStoreTest`: `isOnboardingDone` defaults to false, and true persists across a new `SettingsStore` on the same prefs.
- `LockViewModelTest`:
  - Successful setup with `enableBiometric = true` → `takeBiometricEnrolmentRequest()` returns true once, then false.
  - With `enableBiometric = false` → false.
  - Too-short or mismatched password with `enableBiometric = true` → false, and a later successful setup → true.
- No UI tests (pager, illustrations).

## Dependencies
- 02 (biometric key store, enable prompt, `SettingsViewModel.enableBiometric`), 07 (tokens/components), 08 (setup copy, `ic_shield_lock`), 09/11 (authenticator and notes exist, so the pages describe real features).
