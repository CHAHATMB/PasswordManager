# Verify – Password generator

- Build commit: 0d2080e (last code commit 1865025). Debug via `./gradlew installDebug`, with FLAG_SECURE (activity) and `SecureFlagPolicy.SecureOn` (Add sheet, generator sheet) turned off in a local, uncommitted edit for screenshots only; restored with `git checkout` afterwards.
- Device: AVD `Pixel_9a` (sdk_gphone64_arm64, API 36), headless. Fresh `pm clear`, master password `correcthorse1`, auto-lock 15 min.
- Result: PASS

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Generate icon in Add opens the sheet with a 20-char preview | FAB → Password field shows "Generate password" + reveal icons → tap Generate → "Generate password" sheet, preview 20 chars, "Length: 20", all 4 classes on, Avoid ambiguous off, "Strength: Strong" | PASS | 07-add-sheet-generate-icon.png, 08-generator-default.png |
| 2 | Slider 8 / 64 changes the length; Regenerate differs | Drag fully left → "Length: 8", 8-char preview; fully right → "Length: 64", 64 chars. Back to 20, Regenerate ×3 → 4 distinct values | PASS | 09-generator-length-8.png, 10-generator-length-64.png, 11-generator-regenerated.png |
| 3 | Letters only; avoid ambiguous; last class can't be turned off | Symbols + Digits off → 6 previews, upper+lower only. Uppercase off → Lowercase switch `enabled=false`, tap does nothing, preview lowercase only; Uppercase on → Lowercase enabled. All on + Avoid ambiguous + length 64, 11 previews → none contain `0 O o l 1 I \|`, all four classes present | PASS | 12-generator-letters-only.png, 13-generator-last-class-disabled.png, 14-generator-avoid-ambiguous-64.png |
| 4 | Use fills the field; saved credential shows the exact value | Length 20 → Use → sheet closes, revealed field equals the last preview, "Strength: Strong". Save as `github.com/alice` → Detail reveal → identical value | PASS | 15-generator-before-use.png, 16-add-filled-revealed.png, 17-detail-revealed-generated.png |
| 4b | Generator from Edit | Detail → Edit → Generate → Use → field = preview, Strong → Save → Detail reveal shows the new value | PASS | 21-edit-generator-open.png, 22-detail-after-edit-generated.png |
| 5 | Strength: `password` Weak, generated 20 Strong (Add/Edit) | Edit: `password` → Weak (red), `qmzv7xk2wt` → Fair, generated 20 → Strong. Edit opens with "Strength: Strong" for the saved value | PASS | 18-edit-strength-no-bar.png, 19-edit-weak-keyboard-save-visible.png, 20-edit-fair.png |
| 6 | Setup shows the strength indicator | Fresh install: empty field → no indicator. `password` → Weak + "easy to guess" warning; `qmzv7xk2wt` → Fair, no warning; `Kx9#mP2$vL7qR4wZ` → Strong; `correcthorse1` → Good. `password`/`password` → Create vault → "must be at least 12 characters". `correcthorse1` creates the vault | PASS | 01-setup-empty.png, 02-setup-weak.png, 03-setup-fair.png, 04-setup-strong.png, 05-setup-min-length.png, 06-setup-good-correcthorse1.png |
| C1 | Home rows don't draw under the search field | 9 credentials, scroll → rows clipped below the search field. 03 repro `gitf` → `GIT` → github row clipped, no overlap | PASS | 25-home-many-rows.png, 26-home-scrolled-no-overlap.png, 27-search-GIT-no-overlap.png |
| C2 | Edit: Save visible with keyboard; bottom bar + FAB hidden | Edit route: no Home/Favorite/Add nodes in the dump. Password focused, keyboard shown → Save pinned directly above the keyboard, no scrolling | PASS | 18-edit-strength-no-bar.png, 19-edit-weak-keyboard-save-visible.png |
| C3 | Double tap Edit opens it once | Two taps ~80 ms apart, plus 2 trials of concurrent taps → one Edit; one system Back → Detail each time | PASS | 23-double-tap-edit-once.png, 24-back-to-detail.png |
| C4 | Delete in flight at Lock → no Undo after unlock | Row Delete on example.org and Lock in the same `adb shell` call → Unlock screen → unlock → no Snackbar, example.org gone. Control: a normal delete still shows "Deleted site3.net / Undo", and Undo restores site4.net | PASS | 28-locked-after-delete.png, 29-unlock-no-undo.png, 30-normal-delete-undo.png |
| 7 | Logcat hygiene | Whole session (92k lines): 0 `FATAL EXCEPTION`, empty crash buffer; 0 hits for `correcthorse`, `Kx9#mP2`, `qmzv7xk2wt`, `pw-*-secret`, or any generated preview checked (`G2eM#`, `f7OXynu`, `pkidwrmz`, `0%ozp$BT`, `7**A(YAc1`). Remaining "password" matches are the process tag and system IME/a11y noise | PASS | – |

Notes:
- The slider track has inset padding: a drag ending at x≈75–120 px stops at 9–11, and only a drag to the screen edge reaches 8. A drag that *starts* within ~63 px of the left edge triggers system Back and closes the generator sheet (gesture navigation, not an app bug).
- After a failed Create vault (too short), both setup fields are cleared (05-setup-min-length.png). This is existing 01 behaviour, not part of this feature.
- The Home bottom bar still rides above the keyboard while searching (27-search-GIT-no-overlap.png); the dev-log lists this as a known 03 edge case outside the carried items.
- The Undo Snackbar lasts about 4 s; a first attempt at tapping Undo through a UI dump arrived too late, so site3.net stayed deleted (expected expiry behaviour).

## Issues found
- None blocking.
