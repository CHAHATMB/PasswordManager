# App lock and hardening

Status: PLANNED
Backlog ID: 02

## Goal
The vault locks itself when the user leaves the app and can be unlocked with a fingerprint. Screens can't be captured, and repeated wrong passwords are slowed down.

## Scope
- In:
  - Biometric unlock bound to a CryptoObject. This uses a Keystore AES-GCM key: auth required, `BIOMETRIC_STRONG`, invalidated by new enrolment, StrongBox with a fallback. The key wraps VK into `wrappedVK_bio` plus an IV in the vault header.
    - The user enables it from Settings while the vault is unlocked, which triggers an encrypt-cipher prompt.
    - When it's enabled, the Unlock screen shows the prompt automatically, with "Use master password" as the negative button.
  - Handle `KeyPermanentlyInvalidatedException`: delete the key and the blob, turn biometrics off, and show a message asking for the master password. Lockout, cancel and negative-button errors fall back to the password field.
  - Manual "Lock" action in the Home top bar.
  - Auto-lock:
    - `ProcessLifecycleOwner` records `elapsedRealtime` on `ON_STOP`. On `ON_START`, lock if the timeout has passed.
    - Timeout options: Immediately, 30 s, 1 min (default), 5 min, 15 min.
    - Also lock on `ACTION_SCREEN_OFF`.
  - Lock means: zero VK, close the DB, clear decrypted UI state, and navigate to Unlock with the back stack cleared.
  - Failed-attempt delay: from the 3rd consecutive wrong password onward, disable Unlock for `5 s × 2^(n−3)`, capped at 5 min. Keep the count in memory and reset it on success.
  - `FLAG_SECURE` on `MainActivity` before `setContent`. `SecureFlagPolicy.SecureOn` on the `ModalBottomSheet` and on any dialogs. `setRecentsScreenshotEnabled(false)` on API 33+.
  - Settings screen, reached from a Home top-bar icon: auto-lock timeout and the biometric unlock toggle (only shown if `canAuthenticate(BIOMETRIC_STRONG)` succeeds). Store the settings in SharedPreferences (D5).
  - Fix the biometric prompt strings. Move the prompt to the `BIOMETRIC_STRONG` + CryptoObject API, and drop the deprecated `canAuthenticate()`. Upgrade `androidx.biometric` only if a newer *stable* release exists.
  - Release: `isMinifyEnabled = true`, R8 rule `-assumenosideeffects class android.util.Log { *; }`, and keep rules as needed for SQLCipher and argon2kt.
- Out:
  - Requiring the master password after a reboot or after N days.
  - Wiping the vault after N attempts.
  - A screenshot-protection toggle (it is always on).
  - Device-credential (PIN) unlock.
  - Root or integrity checks.

## Acceptance criteria
- [ ] Settings shows an auto-lock selector (default 1 min). On an emulator with an enrolled fingerprint, it also shows a biometric toggle.
- [ ] Turning on biometrics shows a fingerprint prompt (`adb -e emu finger touch 1`). After a relaunch, the Unlock screen shows the prompt automatically, and a successful touch opens the vault with its credentials.
- [ ] Cancelling the prompt or tapping "Use master password" shows the password field, and the master password still works.
- [ ] Enrolling a new fingerprint and relaunching shows a "biometrics disabled, use master password" message. The master password works, and the biometric toggle is off.
- [ ] The top-bar Lock action returns to Unlock immediately. System Back from Unlock does not reveal the vault.
- [ ] Auto-lock "Immediately": Home, then back to the app, shows Unlock. With 1 min: back within 30 s stays unlocked, and back after more than 60 s shows Unlock.
- [ ] Screen off and on while unlocked shows Unlock.
- [ ] Three wrong passwords in a row disable Unlock with a visible countdown. Later failures lengthen the delay.
- [ ] `adb shell screencap` (or the emulator screenshot) of the vault, the add sheet and Settings is black or blocked. The recents thumbnail is blank.
- [ ] `./gradlew assembleRelease` succeeds. A release APK signed locally with the debug keystore (apksigner; no build-config change) installs, sets up, unlocks and adds a credential without crashing.

## Design notes
- Affected files/layers:
  - `data/vault/VaultSession.kt`: lock, biometric wrap and unwrap.
  - New `data/crypto/BiometricKeyStore.kt`: the Keystore key and cipher init.
  - `util/BiometricPromptsUtils.kt`.
  - New `data/settings/SettingsStore.kt`.
  - New `AutoLockObserver`, or the same logic in `AppContainer`.
  - `presentation/LockScreen/*`.
  - New `presentation/SettingsScreen/`.
  - `MainActivity.kt`, `AppNavigation.kt`, `app/build.gradle.kts` (`lifecycle-process`), `proguard-rules.pro`.
- Data model / migration changes: add the optional fields `bioIv` and `wrappedVkBio` to the vault header. There are no DB schema changes.
- Security considerations:
  - Never call biometric auth without a CryptoObject.
  - Decide the timeout using `elapsedRealtime`, not wall-clock time.
  - Zero the unwrapped VK copies.
  - Clear decrypted lists from ViewModel state on lock.
- Keep the timeout decision a pure function: `shouldLock(stoppedAt, now, timeout)`.

## Critical logic needing unit tests
- `shouldLock` covers all timeout options, including the boundary values and "Immediately".
- The failed-attempt delay function covers attempts 1–2 (no delay), 3, 4 and the cap.
- `VaultSession.lock()` changes the state to `Locked`, closes the DB and empties the repository flows.
- The header round trip with and without the biometric fields. Disabling biometrics removes them.
- `SettingsStore` stores and reads back the timeout, and the default is 1 min.

## Dependencies
- 01 (VaultSession, header, AppContainer).
