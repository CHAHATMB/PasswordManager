# Review – Authenticator add flow

## Round 1
- Commit range: `28bee1d..HEAD` (c5e3a8e, 6efb508, 37f21dc, 7ce1600, f53758e)
- Verdict: **APPROVED**

| # | Severity | File | Issue | Suggested fix |
|---|----------|------|-------|---------------|
| 1 | minor | `presentation/Authenticator/AuthenticatorDialogs.kt` (`Failed`) | `dialog.error == ScanError.CameraPermissionDenied` is checked twice, once for `confirmButton` and once for `dismissButton`. | Optional: hoist it into a local `val` such as `offersAlternatives`. |
| 2 | minor | `MainActivity.kt` / `AuthenticatorViewModel.kt` | The lock guard sits in two layers: Gallery is checked in the Activity, ManualEntry in the VM. The brief asks for this, and the Activity check is needed because the picker launch never reaches the VM. The comment explains it. | None needed. Noted for consistency only. |
| 3 | minor | `SecureCaptureActivity.kt` | If a decode completes in the same frame as a button tap, `CaptureManager` can call `setResult(RESULT_OK)` after `finishWith`, and the scan result wins. That outcome is harmless. | None. |

Checked, no issues:
- FAB on the Authenticator route calls `openQrScanner()`. `AddOptions`, `OpenAddOptions`, `AddOptionsSheet`, `AddOption` and their imports are removed, with no references left. The remaining imports (`ModalBottomSheet`, `ExperimentalMaterial3Api`, `Icon`) are still used.
- `initializeContent()` override: the layout keeps `zxing_barcode_scanner` with the library defaults (centerCrop, no TextureView). FLAG_SECURE and the recents setting are still set in `onCreate` before `super.onCreate`, which is where `initializeContent()` runs. `fitsSystemWindows` is on the root.
- Result routing: the extra is read from `originalIntent` on cancel. An unknown or missing extra falls through to the existing `QrScanned` path, so back press, permission denial and `otpauth-migration://` (via `scannedDialog`) behave as before. Gallery opens only while unlocked. `OpenManualEntry` is dropped while locked.
- Permission-denied dialog: "Select from gallery" dismisses, then opens the picker (app-trip handling is reused). "Enter setup key" replaces the dialog with ManualEntry. Cancel dismisses. The copy mentions Settings.
- The overflow menu is always shown. The Google import item sends `OpenGoogleImport`, and Export stays behind `items.isNotEmpty()`. The empty-state copy matches the brief.
- Nothing is logged. No new permissions.
- Commits follow Conventional Commits, one logical change each, with no prose bodies.
- Tests: no new business logic. The only VM change is a one-line lock guard, and the brief scopes routing as Activity/Compose with no unit tests. Agreed.
