# Dev Log – Authenticator add flow

## Round 1
- Base commit: 28bee1d (branch `scratch/STRY-13-authenticator-add-flow`)
- Commits (review range `28bee1d..HEAD`):
  - c5e3a8e feat: add gallery and setup-key buttons to the QR scanner
  - 6efb508 feat: open the QR scanner directly from the Authenticator add button
  - 37f21dc feat: offer gallery and setup-key entry when camera access is denied
  - 7ce1600 feat: move Google Authenticator import to the Authenticator overflow menu
  - plus the `docs:` commit for this log
- Summary of changes:
  - `SecureCaptureActivity`: overrides `initializeContent()` to inflate `res/layout/secure_capture.xml` (vertical: `DecoratedBarcodeView` `zxing_barcode_scanner` with weight 1, then a bar with 2 buttons). A button sets `RESULT_CANCELED` with `EXTRA_SCANNER_ALTERNATIVE` = `ScannerAlternative.name` and finishes. FLAG_SECURE / recents screenshot setting in `onCreate` unchanged. Enum and extra key live in the same file. Labels in `strings.xml`.
  - `MainActivity`: FAB on the Authenticator route calls `openQrScanner()`. The scan callback reads the extra from `result.originalIntent`: Gallery → `openImagePicker()` (only when `VaultState.Unlocked`), ManualEntry → `OpenManualEntry`, otherwise the existing `QrScanned` path.
  - `AuthenticatorViewModel`: `OpenManualEntry` is ignored unless unlocked. `AddOptions` / `OpenAddOptions` removed from state, VM and dialogs, with `AddOptionsSheet`, `AddOption` and their unused imports.
  - `Failed(CameraPermissionDenied)`: stacked "Select from gallery" / "Enter setup key" actions plus Cancel; message mentions allowing camera access in Settings. Other errors keep OK.
  - `AuthenticatorScreen`: overflow menu always shown, with "Import from Google Authenticator" (`ImportExport`, sends `OpenGoogleImport`) and "Export accounts" only when accounts exist. New empty-state copy.
- Decisions / deviations from brief:
  - The scanner button bar sits below the viewfinder (not over it), so zxing's status text at the bottom of `DecoratedBarcodeView` stays visible. The root uses `fitsSystemWindows`. With targetSdk 34 and the Holo fullscreen theme the window doesn't draw behind the nav bar anyway, so this only matters if edge-to-edge is enforced later.
  - The two permission-denied actions are stacked in a Column in the confirm slot. Side by side they don't fit next to Cancel at phone width.
  - Buttons use the platform Holo style from `zxing_CaptureTheme` (no new theme).
- Unit tests added: none (brief: routing is Activity/Compose only). Existing suite passes.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (234 tests, 0 failures).
- Notes for reviewer/verifier:
  - To test denial: `adb shell pm revoke com.example.passwordmanager android.permission.CAMERA`, then tap +.
  - The scanner prompt still reads "Scan the QR code shown by the website" (unchanged).
  - Known gap: after rotation the scanner is portrait-locked, so no extra handling. If the vault locks while the scanner is open, a ManualEntry tap is dropped by the VM and a Gallery tap by `MainActivity`.
