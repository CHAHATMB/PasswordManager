# Verify – Authenticator add flow

- Build commit: 5ab298d. Same screenshot build and vault as 12 (FLAG_SECURE-off patch, reverted afterwards).
- Device: AVD `Pixel_9a`, API 36, emulator-5554, gesture navigation.
- Result: **PASS** (1 cosmetic issue)
- Unit tests: none added or changed by this feature.
- Not tested: camera scan of an `otpauth`/transfer QR (the emulator camera shows a virtual scene with no QR); the import flows themselves are unchanged from 10.

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | + opens the scanner directly | Authenticator → + | PASS: `SecureCaptureActivity` with no sheet in between | 01-scanner-buttons.png |
| 2 | Buttons clear of the navigation bar | Scanner | PASS: both buttons sit above the gesture handle | 01-scanner-buttons.png |
| 3 | "Enter setup key manually" | Scanner → button → ManualCo / me / `JBSWY3DPEHPK3PXP` → Save | PASS: manual sheet opens (key masked); the account is added with a live code | – |
| 4 | "Select from gallery" | Scanner → button → `acme.png` | PASS: document picker opens; "Add this account?" ACME Co / bob → Save adds it | – |
| 5 | Back from scanner → list, no dialog | Scanner → Back | PASS | – |
| 6 | ⋮ items with 0 and ≥1 accounts | ⋮ before and after adding | PASS: "Import from Google Authenticator" only, then also "Export accounts" | – |
| 7 | Import how-to dialog | ⋮ → Import from Google Authenticator | PASS: "From image" and "Scan code" buttons shown | – |
| 8 | Camera denied ("Don't allow" twice) | `pm revoke` + `user-fixed` flag → + | PASS: "Account not added" with the camera message, "Select from gallery" (opens the picker) and "Enter setup key" (opens the manual sheet) | 02-camera-denied-dialog.png |

## Issues found
1. **Cosmetic:** the two scanner buttons have different heights and are not aligned. "Enter setup key manually" wraps onto two lines and is taller and lower than "Select from gallery" (see 01-scanner-buttons.png). Suggest equal-height buttons or the shorter label "Enter setup key".
- Environment: picking an image in DocumentsUI once froze the emulator (system ANR, then `qemu` at 0 % CPU). I killed and restarted the emulator, and the same step passed on retry. Not an app issue.
- Note: after a plain `pm revoke` (without `user-fixed`), + opened the scanner and CAMERA was granted again without a visible prompt. The cause is unclear (emulator auto-grant?). It needs a manual check on a real device.
