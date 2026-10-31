# Verify – Onboarding and biometric at setup

- Build commit: 5ab298d. Same screenshot build (FLAG_SECURE-off patch, reverted afterwards). `pm clear` before each run. Master password `correcthorse1`.
- Device: AVD `Pixel_9a`, API 36, emulator-5554, device PIN 1234. A fingerprint was enrolled during the run (Settings → Pixel Imprint + `adb -e emu finger touch 1`).
- Result: **PASS**
- Unit tests: `SettingsStoreTest` (11), `LockViewModelTest` (11) → 0 failures.
- Not tested: dark mode / landscape, upgrade install over an existing vault, the "switch off" creation path (default off was seen), swipe paging (Next only).

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Fresh install → 4 pages via Next; page 4 has Get started and no Skip | `pm clear` → launch → Next ×3 | PASS: Passwords, 2-step codes, Notes, then "Locked down by design" with 6 fact lines | 01-onboarding-security-page.png |
| 2 | Back: page 3 → 2, page 1 closes the app | Back on page 3, then on page 1 | PASS | – |
| 3 | Killed during onboarding → starts again | Page 2 → force-stop → launch | PASS: page 1 | – |
| 4 | Skip → setup; relaunch → setup directly | Skip → force-stop → launch | PASS | – |
| 5 | No fingerprint enrolled → no switch | Setup before enrolment | PASS | – |
| 6 | Enrolled → switch shown, default off | Enrol → relaunch | PASS: "Unlock with fingerprint", `checked=false` | 02-setup-fingerprint-switch.png |
| 7 | Mismatched passwords → no prompt; fix → prompt once | Switch on, `…1`/`…2` → Create → fix → Create | PASS: "Passwords do not match", no prompt; then one "Turn on fingerprint unlock" prompt | – |
| 8 | Cancel prompt → vault open, biometric off, no error | Cancel | PASS: empty vault list; Settings → Fingerprint unlock `checked=false` | – |
| 9 | Accept → enabled; after relaunch the prompt is automatic | Fresh run, switch on → Create → finger touch → Settings → Lock → relaunch → touch | PASS: toggle on; "Unlock Vaultly" prompt on launch (also after an emulator reboot); touch opens the vault | – |

## Issues found
- None in the app. Once, after Home and returning, setup showed instead of onboarding page 1. It did not reproduce in 2 retries (with Home for 1 s and 8 s), so it was probably a stray tap during the first, failed UI dump.
