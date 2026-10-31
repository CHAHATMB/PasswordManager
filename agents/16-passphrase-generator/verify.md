# Verify – Passphrase generator

- Build commit: b4ada95 (last feature 16 code commit 1dffebd). Unmodified debug APK, same install and vault as 14 (master password `correcthorse1`).
- Device: AVD `Pixel_9a`, API 36, emulator-5554.
- Result: **PASS** (1 cosmetic issue)
- Unit tests: `PassphraseGeneratorTest` (14), `WordlistTest` (5), `PasswordStrengthTest` (11) → 0 failures.
- Proof: PNG screenshots were not possible. The generator sheet is `SecureOn`, so `screencap` is black, and the temporary FLAG_SECURE-off edit was blocked by the tool permission policy. The `uiautomator` dumps in `screenshots/16-passphrase-generator/*.xml` are the proof instead.

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Sheet opens in password mode with Passphrase off | Passwords → + → Generate password | PASS: Passphrase switch `checked=false`, 20-char preview, Length slider and class switches shown | – |
| 2 | Passphrase on: 6-word lowercase `-` preview; password controls hidden; Words slider, separator, Capitalise, Add a digit shown | Toggle on | PASS: `recreate-drift-removing-decline-jalapeno-wound`, "Words: 6", Separator `-`/Space/`.`/`_`, both switches, no Length/class rows. No visible delay on first switch | – |
| 3 | Word count 4 and 10; Regenerate gives a new phrase | Slider to min, max, then 6 → Regenerate | PASS: 4 words, 10 words, then 6. Regenerate changed the phrase | – |
| 4 | Separators, Capitalise, Add a digit | Space / `.` / `_` → Capitalise → Add a digit → Regenerate ×2 | PASS: `unraveled rewrite …`, `paralyses.flatten…`, `cactus_sprig…`. Capitalised: `Strife_Paying_…`. Exactly one digit at a word end in each sample (`Dealmaker4`, `Basil6`, `Dedicate9`) | 01-passphrase-mode.xml |
| 5 | Strength label in bits: 4 → 51 Fair, 6 → 77 Good, 7 → 90 Strong | Slider 4 / 6 / 7 | PASS: "Fair · 51 bits", "Good · 77 bits", "Strong · 90 bits" (10 → "Strong · 129 bits"; 6 + digit → "Strong · 83 bits", matching log2(10)+log2(6)) | 01-passphrase-mode.xml |
| 6 | Attribution visible | Passphrase mode | PASS: "Wordlist: EFF (CC BY 3.0)" | 01-passphrase-mode.xml |
| 7 | "Use" fills the exact phrase; saved credential matches | Use → reveal field → website `phrase.example` → Save → open → Show password | PASS: `Rentable_Bagful_Sinuous_Dedicate9_Vixen_Skyline` in both places. The Add field shows "Strength: Strong" (the char-class gap noted in the brief) | 02-saved-credential-revealed.xml |
| 8 | Passphrase off restores password options and preview | Reopen sheet → on → off | PASS: the same password preview `r?lC]zg0B#…` and Length 20 + class switches are back. Reopening the sheet starts in password mode with default passphrase options | – |

## Issues found
1. **Cosmetic:** turning Passphrase on makes the sheet taller, so the switch moves about 93 px down (y 987 → 1080). A quick second tap at the same spot misses the switch. Repro: open the generator, tap the Passphrase switch twice at the same coordinates → it stays on. This is standard bottom-sheet resizing and low impact.
- Extra proof (verify of 12–17, screenshot build at 5ab298d): `screenshots/16-passphrase-generator/03-passphrase-on.png` (Passphrase on, 6 words, Good · 77 bits).
