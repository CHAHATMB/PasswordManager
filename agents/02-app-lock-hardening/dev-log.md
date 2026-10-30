# Dev Log – App lock and hardening

## Round 1
- Base commit: 14d09542a9734d39ce73edd21b0eb36b489664ed
- Commits (review range `14d0954..HEAD`):
  - 5b212db chore: add lifecycle-process and material3 1.2.1
  - b440880 feat: store an optional biometric-wrapped vault key in the header
  - 9612840 test: cover vault header round trip with biometric fields
  - 3c08b38 feat: lock the vault, wipe its keys and wrap VK with a Keystore key
  - 9ba13e6 test: cover vault lock and biometric wrap and unwrap
  - cae663c feat: auto-lock after a configurable timeout and on screen off
  - a4b0c0c test: cover auto-lock timeout decision and settings store
  - 0ad4159 feat: delay unlock after repeated wrong master passwords
  - 3acb652 test: cover the wrong-password delay
  - 939fe62 feat: add Settings screen and CryptoObject-bound biometric unlock
  - f337143 feat: add a Lock action to the Home top bar
  - 46dd532 feat: block screenshots and recents thumbnails of the app
  - 9943212 chore: enable R8 for release and strip Log calls
  - plus the `docs:` commit for this log
- Summary of changes:
  - Header (`data/vault/VaultHeader.kt`, `VaultHeaderStore.kt`): optional `BiometricWrappedKey(iv, wrappedKey)`, stored as prefs keys `bio_iv` and `wrapped_vk_bio`. Saving a header without it removes both keys. `withBiometric()` copies the header.
  - `VaultSession`:
    - The session now owns the SQLCipher raw key (`databaseKey`) and keeps a VK copy while unlocked. `openDatabase` takes the raw key.
    - `lock()` sets `Locked`, closes the DB, then zeroes both arrays. It does nothing unless unlocked.
    - `enableBiometric(cipher)`, `unlockWithBiometric(cipher)`, `disableBiometric()` and `biometricIv()`. New `deleteBiometricKey` lambda.
    - Setup, unlock and biometric unlock share `openVault()`, which keeps the key on success and zeroes it on failure. It runs under `NonCancellable`, as before.
  - `data/crypto/BiometricKeyStore.kt`: AES-256-GCM key `vault_biometric_key` with auth required, `AUTH_BIOMETRIC_STRONG` (API 30+) or validity −1 (API 27–29), invalidated by enrolment, and StrongBox with a TEE fallback on `StrongBoxUnavailableException`. `newEncryptCipher()` always replaces the key. `decryptCipher(iv)` throws `KeyPermanentlyInvalidatedException` when the key is invalidated or missing.
  - Auto-lock:
    - `data/settings/SettingsStore.kt` is the `AutoLockTimeout` enum (default `MINUTE_1`) in private prefs `settings`. Unknown values fall back to the default.
    - `AutoLockObserver.kt` holds the pure `shouldLock(stoppedAt, now, timeout)` (`now - stoppedAt >= timeout`) and the observer. `ProcessLifecycleOwner` records `elapsedRealtime` on `ON_STOP` and checks it on `ON_START`. A dynamic `ACTION_SCREEN_OFF` receiver calls `lock()`. It is started in `PasswordManagerApp.onCreate`.
  - Backoff (`data/vault/UnlockThrottle.kt`):
    - `unlockDelayMillis(n)`: 0 for n ≤ 2, otherwise `5 s × 2^(n−3)`, capped at 5 min.
    - `UnlockThrottle` is process-wide in `AppContainer`, so finishing the activity doesn't reset it. It resets on a successful unlock.
    - `LockViewModel` ignores `unlock()` while blocked and shows `retryInSeconds`. `UnlockScreen` shows "Too many wrong attempts. Try again in N s" and disables Unlock.
  - Biometric UI:
    - `util/BiometricPromptsUtils.kt` is rewritten. It uses `canAuthenticate(BIOMETRIC_STRONG)`, prompts are always `setAllowedAuthenticators(BIOMETRIC_STRONG)` and always get a `CryptoObject`, and the strings are fixed. Its `Log` calls are gone.
    - `MainActivity` creates one `BiometricPrompt` in `onCreate`. The Locked branch auto-prompts once per lock, after `withResumed`.
    - `KeyPermanentlyInvalidatedException` means: delete the key and blob, then show "Biometrics disabled because your fingerprints changed. Use your master password."
  - Settings (`presentation/SettingsScreen/`) is a NavHost route reached from a gear icon in the Home top bar. It has auto-lock radio buttons and a "Fingerprint unlock" switch. Turning the switch on shows the encrypt prompt, and it reads on only after the wrap is saved.
  - The Home top bar also has a Lock icon, which calls `VaultSession.lock()`.
  - Screenshot protection: `FLAG_SECURE` before `setContent`, `setRecentsScreenshotEnabled(false)` on API 33+, and `SecureFlagPolicy.SecureOn` on the add sheet. The app has no other dialogs.
  - Release build: `isMinifyEnabled = true`, `Log` stripping, and keep rules for SQLCipher and argon2kt.
- Decisions / deviations from brief:
  - The biometric "enabled" flag is not in `SettingsStore`. It is derived from the header blob, so there is a single source of truth and they can't disagree after invalidation.
  - The header fields are one nullable `BiometricWrappedKey`, not two independent fields, so "IV without blob" can't be represented. If only half the blob is present in prefs, it loads as "biometrics off", not as `CorruptHeader`, so the master password always works.
  - The R8 rule lists the `Log` methods (`v/d/i/w/e/wtf/println/isLoggable`) instead of `{ *; }`. R8 warns that `{ *; }` also matches `java.lang.Object` methods.
  - `androidx.biometric` stays at 1.1.0, because 1.2.x and 1.4.x are still alpha/beta (Google Maven, checked today).
  - `material3` is pinned to 1.2.1 over the 2023.08 BOM (1.1.1), because 1.1.1 has no `ModalBottomSheetProperties(securePolicy)`. Compose UI was already 1.6.1 through `material-icons-extended`.
  - Prompt errors (cancel, negative button, lockout, no biometrics) have no handler, because the password field is always on screen behind the prompt. There is no separate "use fingerprint" button: the prompt appears once per lock.
  - Prompt routing: the single callback checks the activity field `isEnablingBiometric` *and* the vault state. An enable result goes through only while unlocked, and an unlock result only while locked. A rotation mid-prompt loses the field, so the result is ignored and the user retries.
  - The Settings toggle also shows while biometrics are enabled but `canAuthenticate` fails (for example, all fingerprints were removed), so the user can still turn it off.
  - `newEncryptCipher()` always deletes and recreates the key, so a stale or invalidated key never blocks enabling.
  - Backoff is enforced in `LockViewModel` (UI), not inside `VaultSession`. Argon2 remains the real brute-force cost.
  - Process slip: `3c08b38` alone does not compile its unit tests (the existing `VaultSessionTest` misses the new `deleteBiometricKey` parameter until `9ba13e6`). Main code compiles at every commit, and every other code commit compiles with its tests (checked through `git archive` exports). Not amended, per the rules.
- Unit tests added (21 new, 60 total):
  - `AutoLockTest` (4): Immediately at 0 ms; every timeout is unlocked 1 ms before its boundary and locked at and after it; 1 min stays unlocked at 30 s and locks at 61 s.
  - `data/vault/UnlockThrottleTest` (4): 1–2 failures give 0; the 3rd gives 5 s; 4/5/8 give 10/20/160 s; 9 and 1000 cap at 5 min (no overflow).
  - `data/settings/SettingsStoreTest` (3, Robolectric): the default is 1 min; every value survives a new store instance; an unknown value falls back to the default.
  - `data/vault/VaultHeaderStoreTest` (+3, plus a null-biometric assert in the existing round trip): biometric round trip; saving without biometrics removes both prefs keys; a half blob loads as disabled.
  - `data/vault/VaultSessionTest` (+7): lock closes the DB and zeroes the SQLCipher key array; lock empties both repository flows; lock while not unlocked is a no-op; enable, lock and biometric unlock give the same DB key as setup; the wrong biometric key gives `AEADBadTagException` and stays Locked; enable while locked throws; disable removes the blob, calls `deleteBiometricKey`, and the password still unlocks. Biometric tests use a plain JCA AES-GCM key in place of the Keystore.
  - `LockViewModelTest`/`CredentialRepositoryTest`: only constructor updates.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (60 tests, 0 failures). `./gradlew assembleRelease` → PASS with no R8 warnings (`app-release-unsigned.apk`).
- Notes for reviewer/verifier:
  - Not run on a device: no emulator is attached, and the host has about 5 GB free. Biometric Keystore behaviour, auto-lock timing, FLAG_SECURE and the minified release are unverified at runtime. Check the release build first, because R8 plus native JNI is the most likely crash.
  - Known edge cases (not fixed):
    - A manual or auto lock while a credential write or Room query is in flight closes the DB under it, which could throw in `viewModelScope`.
    - A screen-off lock *during* an Argon2 unlock is a no-op (the state is still Locked), so the vault opens when the KDF finishes.
    - `ProcessLifecycleOwner` dispatches `ON_STOP` about 700 ms after the last activity stops. With "Immediately", returning within 700 ms doesn't lock.
  - The VK copy kept in `VaultSession` while unlocked adds no exposure: SQLCipher already holds the hex form of VK for the DB's lifetime. Both are zeroed on lock.
  - Manual test flow (package `com.example.passwordmanager`, emulator API 30+ with a lock-screen PIN):
    1. Fingerprint setup: Settings → Security → Pixel Imprint / Fingerprint → set a PIN → while "touch the sensor" is shown, run `adb -e emu finger touch 1` repeatedly until enrolment completes.
    2. `adb uninstall com.example.passwordmanager`, `./gradlew installDebug`, launch, and set up with `correcthorse1`. Add `github.com / alice / gh-secret-1`.
    3. Home top bar: gear → Settings shows five auto-lock options (Immediately, 30 s, 1 min, 5 min, 15 min) with "After 1 minute" selected and a "Fingerprint unlock" switch. On an AVD without enrolment, the switch is absent.
    4. Turn the switch on → "Turn on fingerprint unlock" prompt → `adb -e emu finger touch 1` → the switch stays on. Cancel instead → the switch stays off.
    5. `adb shell am force-stop com.example.passwordmanager`, relaunch → the Unlock screen shows the "Unlock vault" prompt with "Use master password" → `adb -e emu finger touch 1` → Home with `github.com`, and the eye reveals `gh-secret-1`.
    6. Relaunch again → tap "Use master password" → the password field is visible and `correcthorse1` unlocks. Repeat with Back on the prompt (cancel).
    7. Invalidation: while locked or unlocked, enrol a second fingerprint in Settings (`adb -e emu finger touch 2`). Force-stop and relaunch → no prompt, and the message "Biometrics disabled because your fingerprints changed. Use your master password." appears. `correcthorse1` → unlock works → Settings shows the switch off. `adb shell run-as com.example.passwordmanager cat shared_prefs/vault_header.xml` has no `bio_iv` or `wrapped_vk_bio`.
    8. Lock action: Home top bar Lock icon → Unlock immediately. Press system Back → the app closes. Relaunch → still Unlock, no vault content.
    9. Auto-lock timing (no fingerprint needed; tap "Use master password" when prompted):
       - Settings → "Immediately". Press Home, wait at least 2 s (ON_STOP is delayed about 0.7 s), reopen from recents → Unlock.
       - Settings → "After 1 minute". Press Home, reopen within 30 s → still Home. Press Home, wait 65 s (time it with `date` or `adb shell sleep 65`), reopen → Unlock.
       - Optional: "After 30 seconds" boundary at 25 s (stays) and 35 s (locks).
    10. Screen off: unlocked on Home, `adb shell input keyevent KEYCODE_POWER` (off), then again (on) and unlock the device → Unlock screen (the fingerprint prompt appears after the keyguard is gone).
    11. Backoff: enter a wrong password twice → only "Wrong master password". The 3rd wrong → "Too many wrong attempts. Try again in 5 s" counting down, and Unlock disabled. After it reaches 0, the 4th wrong → 10 s, then the 5th → 20 s. Force-stop resets the count (in memory, by design). Pressing Back and relaunching during a countdown keeps it. The correct password resets it.
    12. Screenshots: on Home, the add sheet (FAB) and Settings, run `adb exec-out screencap -p > shot.png` → black image (or use the emulator camera button → blocked). Open recents (`adb shell input keyevent KEYCODE_APP_SWITCH`) → blank or blank-styled thumbnail.
    13. Release:
        - `./gradlew assembleRelease`
        - `$ANDROID_HOME/build-tools/<ver>/zipalign -f 4 app/build/outputs/apk/release/app-release-unsigned.apk rel-aligned.apk`
        - `apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --ks-key-alias androiddebugkey --out rel.apk rel-aligned.apk`
        - `adb uninstall com.example.passwordmanager && adb install rel.apk` → set up, add a credential, force-stop, unlock, add another. Expect no crash.
    14. `adb logcat` during steps 2–11: no master password, credential passwords, keys, IVs or Base64 blobs. In the release build, expect no app `Log` output at all.

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
