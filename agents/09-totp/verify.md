# Verify – TOTP authenticator

- Build commit: 09efb63. Debug APK with a temporary FLAG_SECURE-off edit (`MainActivity`, `SecureCaptureActivity`, `SecureOn` → `SecureOff`) for screenshots. The edit was reverted with `git checkout` and never committed. Installed with `adb install -r` over the 08 build.
- Device: AVD `Pixel_9a` (sdk_gphone64_arm64, API 36), emulator-5554. Master password `correcthorse1`.
- Reference codes: `pyotp` in a scratch venv at the device's `date +%s` (no `oathtool` on the host).
- Result: **INCOMPLETE (blocked)**. Everything that ran passed. The emulator froze partway (Issue #1). Copy-cleared-on-lock, edit/delete, lock/relaunch, the camera scan, FLAG_SECURE checks and backup v2 export/import did not run.

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Upgrade over the 08 build (migration 1→2) | `install -r` over 08 → unlock | PASS: github.com, example.com, gitlab.com still listed. Authenticator tab empty | – |
| 2 | Tab + empty state | Bottom bar → Authenticator | PASS: third tab "Authenticator" (shield). "No authenticator accounts" + "Tap + to add one by scanning its QR code or entering its setup key." FAB → sheet with Scan QR code / Scan from image / Enter setup key | 01-tab-empty.png |
| 3 | Scan from image (ACME, 8 digits) | QR PNG of the brief's URI (python `qrcode`) → `adb push` to Downloads → FAB → Scan from image → acme.png | PASS: "Add this account?" ACME Co / bob / live 8-digit code → Save → listed. Code `51105994` = pyotp at the same second | 02-scan-image-confirm-acme.png |
| 4 | hotp / non-otpauth QR errors | Scan from image → `otpauth://hotp/x?secret=…&counter=1` and `https://example.com` QRs | PASS: "Account not added": "Counter-based (HOTP) codes are not supported." and "This QR code is not an authenticator code." | 03-scan-not-authenticator-error.png |
| 5 | Invalid key / empty account name | Enter setup key → key `ABC1!`, account empty → Save | PASS: inline "Enter the account name" and "Not a valid setup key: use letters A–Z and digits 2–7". Not saved | 04-manual-entry-inline-errors.png |
| 6 | Manual entry, lowercase spaced key | Example / alice@example.com / `jbsw y3dp ehpk 3pxp` → Save | PASS: listed with `334 349` = pyotp(`JBSWY3DPEHPK3PXP`) at the same second | – |
| 7 | SHA256 / 8 digits / 60 s | Advanced → SHA256, 8, period 60, key `KRSXG5CTMVRXEZLU` | PASS: `8275 3752`, "New code in 32 seconds" (> 30, so 60 s period) = pyotp sha256/8/60 | – |
| 8 | Countdown, boundary change, warning colour | Watched the list across a 30 s boundary | PASS: seconds tick down; all codes changed at the boundary; 60 s row kept its period; ring and number orange in the last 5 s | 05-list-codes-warning-countdown.png |
| 9 | Tap to copy, cleared after 30 s | Tap the Example card → paste into the clipboard editor → `417126` ("From Vaultly") = current code. Paste into Passwords search after ~40 s | PASS: code copied; empty after 30 s. The "Copied" message wasn't captured (dump too slow) | – |
| 10 | Clipboard cleared on lock | – | NOT RUN (Issue #1) | – |
| 11 | Edit / delete with confirm | – | NOT RUN (Issue #1) | – |
| 12 | Lock/relaunch keeps accounts; screenshots blocked on tab/scanner | – | NOT RUN (Issue #1). The FLAG_SECURE check needs the normal build | – |
| 13 | Camera scan + permission deny | – | NOT RUN. Skipped on purpose: the freeze followed a camera start (Issue #1), so VirtualScene looks risky on this host | – |
| 14 | Backup v2 export → wipe → import, and the 08 v1 backup | – | NOT RUN (Issue #1). `vaultly-backup.json` (08, v1) is still in Downloads | – |

## Issues found
1. **Blocker for verification (environment, not app):** the emulator froze again. A stray input after closing the system clipboard editor opened Google Lens, which asked for camera permission. After that, every `adb shell` timed out for more than 3 min, `qemu-system-aarch64` (PID 34709) stayed at 0 % CPU, and `adb devices` still showed `device`. The emulator was not restarted. **The APK on the device is the screenshot build (FLAG_SECURE off).** Run `./gradlew installDebug` once it responds, then run rows 10–14.
2. **Minor (UI):** a long code squeezes the title. "ACME Co" shows as "ACME …" next to an 8-digit code, and "Sha256Co" as "Sha256…". Consider letting the title wrap, or putting the code on its own line.
3. **Minor (security/UX, needs a decision):** the setup-key field shows the key in plain text while typing (no masking or reveal toggle), although the brief says secrets are never displayed in 09. Common authenticators also show it, so this is acceptable if intended.
4. **Cosmetic:** the countdown content description says "New code in 1 seconds" (no singular form).
- Harness note: `ui()`/`$(…)` calls used to return 30 s late because the watchdog `sleep` held the pipe open, so the first code comparison looked off by one period. The helper was fixed, and all the comparisons above were made after the fix.
