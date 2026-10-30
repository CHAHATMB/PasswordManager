# Verify – Branding: Vaultly

- Build commit: 0bf273d (last code commit f78a892). Debug APK installed with `adb install -r` over the 07 build already on the device (upgrade install). For screenshots, FLAG_SECURE (activity) and `SecureFlagPolicy.SecureOn` were switched off in a local edit that was never committed, then restored with `git checkout`. **The APK still on the emulator is that screenshot build.** It could not be replaced because the emulator froze (Issue #1). Reinstall the unmodified build with `./gradlew installDebug` once the emulator responds again.
- Device: AVD `Pixel_9a` (sdk_gphone64_arm64, API 36), emulator-5554.
- Scope cut partway by the user: only the flows whose code changed in 08, plus the 07 add-sheet fix. No broad regression pass and no extra light/dark coverage.
- Result: **INCOMPLETE (blocked)**. Everything that ran passed. The cold-start splash and setup-screen checks did not run because the emulator froze.

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Launcher shows "Vaultly" with the teal shield-lock icon, not clipped | App drawer → "Vaultly" label, white shield-lock on teal, circle mask | PASS for the circle mask. Squircle/square NOT RUN: this image has no icon-shape setting in Wallpaper & style and no shape overlays (`cmd overlay list`). Geometry check from review: the glyph's farthest point is ≈ 24 dp from the centre, inside the 33 dp safe zone, so it is not clipped by any standard mask | 01-launcher-vaultly-icon.png |
| 2 | Themed icons on → monochrome shield | Wallpaper & style → Home screen → Themed icons on, Vaultly placed on the home screen | PASS: monochrome shield tinted like the other themed icons (home screen and hotseat suggestion). The app drawer isn't themed on this launcher (true for all apps). Themed icons switched back off afterwards | 02-launcher-themed-icon-monochrome.png |
| 3 | Cold-start splash, light + dark, no white flash | `screenrecord` around `am start` after force-stop | NOT RUN: the emulator froze at this step (Issue #1) | – |
| 4 | Unlock welcome | Cold launch after the upgrade install | PASS: shield, "Welcome back" + "Your vault is locked and encrypted on this device. Enter your master password to continue." | 03-unlock-welcome-light.png |
| 4b | Setup welcome "Welcome to Vaultly" + warning | Needs `pm clear` | NOT RUN (blocked). Code check only: `SetupScreen.kt` headline "Welcome to Vaultly" | – |
| 5 | Top bars / UI say "Vaultly", no "Password Manager" | Home top bar "Vaultly". Import of a JSON file with a foreign `format` → "This is not a Vaultly backup file." | PASS. `grep` in `app/src/main`: "Password Manager" only appears in the Google Password Manager CSV import copy | – |
| 6 | Biometric prompt title | – | NOT RUN: no fingerprint enrolled and no device PIN on the AVD, so the app shows no biometric option. Code check: `BiometricPromptsUtils` title "Unlock Vaultly" | – |
| 7 | Default backup file name | Settings → Export encrypted backup → master password → export password → system picker | PASS: picker proposes `vaultly-backup.json`, saved (400 B, "Backup saved") | 04-export-file-name-vaultly-backup.png |
| 8 | Upgrade over the 07 build: vault unlocks, data intact, an old export still imports | `install -r` → unlock with the 07 master password → Home lists github.com (favourite). Import `passwords-export.json` (made by the 07 build) with its export password | PASS: "Imported: 2, Skipped as duplicate: 1, Invalid: 0" (the two rows deleted during 07 verification came back) | – |
| 9 | Copy → auto-cleared after 30 s (new clip label) | Copy github.com's password, paste into search at t≈23 s → `gh-secret-1`. Paste again at t≈38 s → nothing | PASS | – |
| 10 | 07 fix: add sheet survives a dark-mode switch | FAB → `draft.example.org` / `dana` / `draft-pass-99` → `cmd uimode night yes` → sheet still open with all three values (password revealed to confirm) and Strength: Good. `night no` → still kept | PASS | 05-add-sheet-kept-after-dark-switch.png |
| 11 | 07 fix: draft cleared after lock | Home with the draft sheet open → background for 65 s (auto-lock 1 min) → Unlock screen → unlock → Home with no sheet. FAB → empty fields | PASS | – |

## Issues found
1. **Blocker for verification (environment, not app):** the emulator froze again, like 07 Round 1. Repro: `am force-stop com.example.passwordmanager` while `screenrecord` was starting (cold-start splash check). After that, `adb shell echo`, `am force-stop` and `dumpsys` hung for over 3 minutes. `qemu-system-aarch64` (PID 34709) stayed at ~0 % CPU, and `adb devices` still listed `emulator-5554 device`. Per instructions the emulator was not restarted. Still to do: row 3 (splash light/dark, no white flash), row 4b (setup welcome, needs `pm clear` and then restoring the vault from `passwords-export.json`), and reinstalling the unmodified build.
- No app defects found in the rows that ran.
- Harness note: a Vaultly shortcut was added to the launcher home screen for the themed-icon check and is still there.

## Round 2
- Build commit: 09efb63 (09 build, with a temporary FLAG_SECURE-off edit for screenshots that was reverted with `git checkout` and never committed). Installed with `adb install -r` over the 08 screenshot build.
- Result: **INCOMPLETE (blocked)**. The emulator froze again (Issue #2) before the 08 checks ran. 09 was verified first so the upgrade install over the 08 vault could be checked before the `pm clear` that 4b needs.

| # | Acceptance criterion | Result |
|---|---|---|
| 3 | Cold-start splash, light + dark, no white flash | NOT RUN (Issue #2) |
| 4b | Setup welcome "Welcome to Vaultly" + warning | NOT RUN (Issue #2) |

### Issues found
2. **Blocker for verification (environment, not app):** the emulator froze during the 09 run. Repro: a stray key/tap left the app and opened Google Lens, which asked for camera permission (VirtualScene camera starting). After that, every `adb shell` call timed out for more than 3 minutes, `qemu-system-aarch64` (PID 34709) stayed at 0 % CPU, and `adb devices` still listed `emulator-5554 device`. The emulator was not restarted. **The APK on the device is the 09 screenshot build (FLAG_SECURE off).** Run `./gradlew installDebug` once it responds.
