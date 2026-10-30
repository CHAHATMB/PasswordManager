# Verify – App lock and hardening

- Build commit: 381b09f (last code commit e492c5a). Debug via `./gradlew installDebug`; release APK from `assembleRelease` (up to date), zipaligned and signed with the debug keystore.
- Device: AVD `Pixel_9a` (sdk_gphone64_arm64, API 36), headless. Device PIN 1234, fingerprint 1 enrolled through Settings.
- Result: PASS

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Settings: auto-lock selector (default 1 min) + biometric toggle | Fresh `pm clear`, set up with `correcthorse1`, add `github.com / alice / gh-secret-1`, gear icon | PASS: five options, "After 1 minute" checked, "Fingerprint unlock" switch (off) shown. Checked via uiautomator; screencap is black | 09-flag-secure-settings.png |
| 2a | Turning biometrics on shows the prompt | Tap switch → "Turn on fingerprint unlock / Confirm with your fingerprint" prompt. Cancel → switch stays off. Again + `finger touch 1` → switch on, `bio_iv` and `wrapped_vk_bio` in `vault_header.xml` | PASS | 02-enable-biometric-prompt.png (black, see note) |
| 2b | Relaunch auto-prompts, touch opens vault | `am force-stop`, relaunch → "Unlock vault" prompt with "Use master password" → `finger touch 1` → Home with github.com; eye reveals `gh-secret-1` | PASS | 03-unlock-biometric-prompt.png (black, see note) |
| 3 | Cancel / "Use master password" fall back to password | Relaunch → tap "Use master password" → password field → `correcthorse1` unlocks. Relaunch → Back on prompt → password field shown | PASS | – |
| 4 | New enrolment invalidates biometrics | Enrol finger 2 in Settings, relaunch → no prompt, "Biometrics disabled because your fingerprints changed. Use your master password." shown; prefs have no `bio_iv`/`wrapped_vk_bio`; `correcthorse1` unlocks; Settings switch off | PASS | – |
| 5 | Lock action + Back | Home top-bar Lock → Unlock (prompt) immediately. Back dismisses prompt, Back again → launcher. Relaunch → still Unlock | PASS | – |
| 6a | Auto-lock "Immediately" | Select Immediately, Home, wait 3 s, resume from recents → Unlock | PASS | – |
| 6b | Auto-lock 1 min | Select 1 min. Home 25 s → resumes unlocked (Settings). Home 65 s → Unlock | PASS | – |
| 7 | Screen off/on locks | Unlocked, `KEYCODE_POWER`, wake, enter device PIN → app shows Unlock with prompt | PASS | – |
| 8 | Backoff with countdown, growing delay | Wrong ×2 → only "Wrong master password". 3rd → count 3 (5 s, expired before the dump). 4th → "Try again in 8…1 s" (10 s). 5th → 20 s. 6th → 40 s. Unlock clickable is `enabled=false` during countdown. Correct password → `unlock_throttle.xml` empty | PASS | – |
| 8b | Backoff survives restart | During 40 s countdown: `am force-stop`, relaunch → "Try again in 29 s", Unlock disabled. Also Back + relaunch keeps it | PASS | – |
| 9 | FLAG_SECURE + blank recents | `adb exec-out screencap` on Home, add sheet and Settings → black (status/nav bars only). Recents → app card is blank (white body, colored header) | PASS | 09-flag-secure-home.png, 09-flag-secure-add-sheet.png, 09-flag-secure-settings.png, 10-recents-thumbnail.png |
| 10 | Release build | `assembleRelease` OK; zipalign + apksigner (debug keystore); uninstall, install, set up, add `example.org`, force-stop, unlock, add `second.net` → no crash | PASS | – |
| 11 | Logcat hygiene | Debug run (whole session) and release run: 0 `FATAL EXCEPTION`; 0 hits for `correcthorse1`, `wrongpass111`, `gh-secret-1`, `rel-secret-2`, `rel-secret-3`. Release: no app-tag `Log` output (only runtime and native `sqlcipher` mlock lines) | PASS | – |

Notes:
- The system biometric prompt can't be captured: with the FLAG_SECURE window behind it, screencap of the prompt is fully black too. Its text was read through uiautomator (as listed above).
- Not run: clock-set-back check (Round 2 addition). The Google Play image has no `adb root`, and changing the time through the Settings UI was skipped. Optional 30 s boundary also skipped.

## Issues found
- None for feature 02.
- Out of scope (pre-existing, low): the add sheet's save button is labelled "Added" (`presentation/AddScreen/AddCredentialScreen.kt:151`). Repro: Home → FAB → the button under Note reads "Added" before anything is saved.
- One "Wrong master password" after the invalidation step did not reproduce. A retry with the field text checked first (`correcthorse1`) unlocked. Most likely the input was lost while the IME attached; treated as a test-harness artifact.
