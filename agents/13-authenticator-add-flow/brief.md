# Authenticator add flow

Status: PLANNED
Backlog ID: 13

## Goal
Tapping + on the Authenticator tab opens the QR camera straight away. The camera screen offers only two other ways in, "Select from gallery" and "Enter setup key manually". "Import from Google Authenticator" moves to the tab's top-bar overflow menu.

## Scope
- In:
  - FAB on the Authenticator route calls `openQrScanner()` directly. The "Add account" bottom sheet is removed.
  - Camera screen (`SecureCaptureActivity`): custom layout with the zxing viewfinder plus two buttons at the bottom, "Select from gallery" and "Enter setup key manually". A tap closes the scanner and returns which button was chosen.
  - `MainActivity` scan result: gallery leads to `openImagePicker()`, manual entry leads to `AuthenticatorEvent.OpenManualEntry`. A scanned text goes through the existing `QrScanned` flow unchanged, `otpauth-migration://` included.
  - Overflow menu (⋮) on the Authenticator top bar is always shown. It has "Import from Google Authenticator" (opens the existing `GoogleImportHelp` dialog) and, only when accounts exist, "Export accounts" (unchanged).
  - Camera permission denied: the existing `Failed(CameraPermissionDenied)` dialog gets two actions, "Select from gallery" and "Enter setup key", plus Cancel. The message also mentions that camera access can be allowed in Settings.
  - Empty-state copy changes to: "Tap + to scan a QR code, or pick an image or enter the setup key from the scanner."
- Out:
  - A replacement scanner (CameraX/ML Kit), torch/zoom controls, an "Open settings" deep link, and any change to parsing, import or export logic.

## Acceptance criteria
- [ ] Authenticator tab: tapping + opens the camera scanner with no intermediate sheet.
- [ ] Scanning an `otpauth://totp` QR shows the existing confirm dialog, and Save adds the account.
- [ ] Scanning a Google Authenticator transfer QR with the camera shows the import preview (existing 10 flow), and "Scan next code" reopens the camera.
- [ ] On the scanner, "Select from gallery" closes the camera and opens the document picker. Picking a QR image adds the account the same way as before.
- [ ] On the scanner, "Enter setup key manually" closes the camera and opens the manual entry sheet.
- [ ] Back from the scanner returns to the list with no dialog.
- [ ] The scanner buttons are not hidden behind the navigation bar (gesture and 3-button navigation).
- [ ] With camera permission denied (including "Don't allow" chosen again), tapping + shows "Account not added" with the camera message and the "Select from gallery" / "Enter setup key" actions, and both work.
- [ ] ⋮ shows "Import from Google Authenticator" with 0 accounts. With at least 1 account it shows that item and "Export accounts".
- [ ] "Import from Google Authenticator" opens the how-to dialog, and its "Scan code" / "From image" buttons work as before.
- [ ] The scanner screen is still blocked from screenshots (FLAG_SECURE kept).

## Design notes
- Affected files/layers:
  - `SecureCaptureActivity.kt`: override `initializeContent()` (a protected method of zxing `CaptureActivity`) to inflate a new `res/layout/secure_capture.xml`. The layout keeps the `DecoratedBarcodeView` with id `zxing_barcode_scanner` and adds 2 buttons. Each button calls `setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_SCANNER_ALTERNATIVE, …))` and then `finish()`. Use `fitsSystemWindows` or an inset listener for the button bar. Put the button labels in `res/values/strings.xml`.
  - New `enum class ScannerAlternative { Gallery, ManualEntry }` and the extra key constant, both next to `SecureCaptureActivity`.
  - `MainActivity.kt`: the `scanQrCodeLauncher` callback reads the extra from `result.originalIntent`. `ScanIntentResult` keeps the result intent on cancel, the same path `MISSING_CAMERA_PERMISSION` uses today. The callback acts only when `lockViewModel.vaultState.value is VaultState.Unlocked`, so the picker or manual sheet can't open over the lock screen. FAB branch: `Authenticator.route -> openQrScanner()`.
  - `AuthenticatorState.kt`: remove `AuthenticatorDialog.AddOptions` and `AuthenticatorEvent.OpenAddOptions`.
  - `AuthenticatorViewModel.kt`: remove the `OpenAddOptions` branch. `OpenManualEntry` ignores the event unless the vault is unlocked, like `QrScanned`.
  - `AuthenticatorDialogs.kt`: remove `AddOptionsSheet`, `AddOption` and their now-unused icon imports. The `Failed` dialog shows gallery and manual actions when `error == CameraPermissionDenied`.
  - `AuthenticatorScreen.kt`: the overflow menu is always visible. Add an "Import from Google Authenticator" item (`ImportExport` icon) that sends `OpenGoogleImport`. Keep the `Export accounts` item behind `items.isNotEmpty()`. Update the empty-state copy.
- Decisions:
  - D13a: The camera routes `otpauth-migration://` to the import flow. It already does: `scannedDialog()` tries `MigrationPayload.parse` first for both camera and image. Users who scan their Google export QR from + still get the import, so the menu entry is only discoverability plus the how-to. No change.
  - D13b: Keep the zxing `CaptureActivity` and customise its layout instead of building a Compose scanner (D8: least code, FLAG_SECURE subclass already exists).
  - D13c: Return the button choice as a cancelled result with an extra. No new ActivityResult contract and no shared state.
  - D13d: Permission denial is handled after the scanner closes. zxing finishes the activity on denial because `SHOW_MISSING_CAMERA_PERMISSION_DIALOG=false`, so the in-camera buttons can't be seen. The denial dialog offers the same two alternatives instead. With "Don't ask again" the dialog appears immediately on +, which is acceptable.
  - D13e: The `GoogleImportHelp` dialog keeps both "Scan code" and "From image". It is unchanged.
- Data model / migration changes: none.
- Security considerations:
  - FLAG_SECURE and the no-recents-screenshot setting stay in `SecureCaptureActivity.onCreate` (the layout override must not drop them).
  - Act on a scanner alternative only while unlocked, because the vault can lock while the camera is open.
  - Do not log scanned text or extras.

## Critical logic needing unit tests
- None new. The routing lives in the Activity and the Compose UI, and parsing/import logic is unchanged. Existing `MigrationPayloadTest`, `OtpAuthUriTest` and `TotpExportViewModelTest` must still pass.

## Dependencies
- 09 (scanner, image decode, manual entry), 10 (migration import, export menu).
