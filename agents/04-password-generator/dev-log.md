# Dev Log – Password generator

## Round 1
- Base commit: e3c8b8ace79923d4dfdb15d0713e941d4094bc2f
- Commits (review range `e3c8b8a..HEAD`):
  - 8af6b04 feat: add a SecureRandom password generator
  - 3f91087 feat: estimate password strength from pool size and length
  - 0d4da60 feat: show password strength in the add, edit and setup forms
  - a6a04fd feat: generate a password from the add and edit forms
  - 3066495 fix: keep Home rows from scrolling under the search field
  - 686645e fix: keep Save visible above the keyboard on the edit screen
  - b57ed7e fix: ignore a second Edit tap on the detail screen
  - ee29e05 fix: offer no Undo for a delete that finishes after a lock
  - plus the `docs:` commit for this log
- Summary of changes:
  - `util/PasswordGenerator.kt`:
    - `PasswordGenerator(random: SecureRandom)`, `generate(GeneratorOptions)`. `GeneratorOptions(length = 20, classes = all four, avoidAmbiguous = false)`. Constants `MIN/MAX/DEFAULT_GENERATED_LENGTH` (8/64/20) and `AMBIGUOUS_CHARACTERS` (`0Ool1I|`).
    - `CharacterClass` enum: Uppercase, Lowercase, Digits, Symbols (`!@#$%^&*()-_=+[]{};:,.?/|~`).
    - Picks use `SecureRandom.nextInt(bound)`, which rejects out-of-range draws, so there is no modulo bias. Positions 0..k-1 get one character from each selected pool and the rest come from the union. A Fisher–Yates shuffle with the same `SecureRandom` then mixes them.
    - `GeneratorOptionsException` (sealed, extends `IllegalArgumentException`): `NoCharacterClass` and `LengthOutOfRange`.
  - `util/PasswordStrength.kt`: `PasswordStrength { Weak, Fair, Good, Strong }` with `of(password)`.
    - Bits = counted characters × log2(pool). The pool adds 26 for lowercase, 26 for uppercase, 10 for digits and 33 for anything else.
    - Penalties: a character that already appeared, or that is ±1 from the previous character (`abc`, `321`), is not counted.
    - Bands: < 40 Weak, < 60 Fair, < 80 Good, ≥ 80 Strong.
  - `presentation/components/PasswordStrengthIndicator.kt`: a 4-step `LinearProgressIndicator` plus the label "Strength: <level>". It appears under the password field in Add/Edit and under the master-password field in Setup, only when the field is not empty.
  - Setup: on Weak it also shows "This password is easy to guess…". It is a warning only; the 12-character minimum (`validateMasterPassword`) is unchanged.
  - `presentation/components/GeneratorSheet.kt`: a `ModalBottomSheet` (`SecureOn`, fully expanded) with:
    - a monospace preview and its strength
    - a length slider (8–64, integer steps)
    - four class switches plus "Avoid ambiguous"
    - Regenerate and Use buttons. Use fills the field and closes the sheet.

    Every option change regenerates the preview. The last selected class switch is disabled.
  - `PasswordTextField`: optional `onGenerate`. When it is set, a "Generate password" icon (Autorenew) shows before the reveal icon. Only the Add/Edit form passes it.
  - Carried over from 03:
    - Home: the list uses `Modifier.padding(scaffoldPadding)` instead of `contentPadding`, so rows are clipped below the search field.
    - Add/Edit form: only the fields scroll (`weight(1f)`), and Save sits pinned below them, so it stays visible above the keyboard. The shell hides the bottom bar and FAB on the `EDIT/{id}` route.
    - Detail Edit uses the same `isCurrent()` guard as Back/Delete.
    - `CredentialViewModel` sends the deleted credential for Undo only if `vaultState.value` is still the same `Unlocked` instance as when Delete was tapped.
- Decisions / deviations from brief:
  - Strength adds a sequence penalty next to the repeat penalty (the brief names only repeats), so `abcdefghijkl` and `123456789012` are Weak. There is no dictionary, per the brief's out-of-scope list. `password` is Weak only because it is short and lowercase. A long dictionary phrase can still score high.
  - Bands of 40/60/80 bits are my choice. A random 12-character password with all four classes scores about 72–79 bits, which is Good. It needs 13 or more effective characters for Strong.
  - Length outside 8–64 also throws its own `LengthOutOfRange` (the brief only names the no-class case). The UI cannot produce either case.
  - "Avoid ambiguous" is off by default (the brief leaves this open; all four classes are on).
  - The generator sheet also shows the preview's strength. This reuses the indicator and adds no other logic.
  - Edit keyboard fix: Save is pinned instead of relying on scrolling. The bottom bar is hidden only on Edit (not on Detail or Settings, where there is no form). The Home search field still lets the bar ride above the keyboard (03 known edge case, not in the carried list).
  - Delete vs Lock: I used an identity check on the `Unlocked` instance (stricter than the reviewer's `is Unlocked`), so a lock followed by an unlock during the write also sends nothing.
  - The `SecureRandom` is built once per sheet (`remember`). The generator is not in `AppContainer`, because only the sheet uses it.
- Unit tests added (14 new, 95 total):
  - `util/PasswordGeneratorTest` (7, seeded `SHA1PRNG`):
    - length is 8, 20 and 64
    - every class appears at length 8 (1 000 runs)
    - unselected classes never appear (1 000 runs)
    - avoid-ambiguous at length 64 has no `0Ool1I|` and still has every class (1 000 runs)
    - two consecutive outputs differ
    - no classes → `NoCharacterClass`
    - lengths 7 and 65 → `LengthOutOfRange`
  - `util/PasswordStrengthTest` (7):
    - empty, `password` and `aaaaaaaaaaaa` → Weak
    - `abcdefghijkl` and `987654321098` → Weak
    - `qmzv7xk2wt` → Fair
    - `Kx9#mP2$vL7q` → Good
    - 100 generated 20-character defaults → Strong
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (95 tests, 0 failures).
  - `assembleDebug` ran before every commit. Test sources change only in 8af6b04 and 3f91087, and their tests were run in those commits.
- Notes for reviewer/verifier:
  - Not run on a device. These are unverified at runtime:
    - the nested sheet (the generator opened from inside the Add sheet)
    - the pinned Save with the keyboard open
    - the hidden bar on Edit
  - The generator preview is shown in plain text by design. The sheet is `SecureOn` and the activity has `FLAG_SECURE`. Nothing is logged.
  - The Add sheet's form state survives opening and closing the generator sheet (plain `remember` in the same composition).
  - Manual test flow (package `com.example.passwordmanager`, API 33+ AVD; set auto-lock to 15 min after setup):
    1. `adb uninstall com.example.passwordmanager`, `./gradlew installDebug`, then launch.
    2. Setup strength:
       - With the master field empty, there is no indicator.
       - Type `password` → "Strength: Weak" (red) plus the "easy to guess" warning.
       - Clear and type `Kx9#mP2$vL7q` → "Strength: Good", no warning.
       - Type `password` in both fields and tap Create vault → the "at least 12" error still appears.
       - Create the vault with `correcthorse1`. It shows Weak plus the warning (repeats and the `rs` step leave about 36 bits), and creation still succeeds because the warning does not block.
    3. Tap the FAB → in the Add sheet, the Password field has a "Generate password" icon and a reveal icon. Tap Generate → the sheet titled "Generate password" opens. Check:
       - The preview is 20 characters and the Length label reads 20.
       - All four class switches are on and Avoid ambiguous is off.
       - The preview strength is Strong.
    4. Length and Regenerate:
       - Drag the slider fully left → Length 8 and an 8-character preview.
       - Drag fully right → Length 64 and a 64-character preview.
       - Set it back to about 20 and tap Regenerate three times → the value differs each time.
    5. Turn off Symbols and Digits → over several Regenerates, the previews contain letters only.
    6. Turn off Uppercase → the Lowercase switch is disabled (greyed, a tap does nothing). Turn Uppercase back on → Lowercase is enabled again.
    7. Turn all classes back on, turn on Avoid ambiguous, set the length to 64, and Regenerate about 10 times → no `0 O o l 1 I |` in any preview.
    8. Turn off Avoid ambiguous and set the length to 20. Tap Use → the generator sheet closes and the Add sheet's Password field is filled.
       - Reveal it → it matches the last preview.
       - Under the field: "Strength: Strong".
    9. Enter Website `github.com` and Username `alice`, then Save. Open the `github.com` row → reveal the password → it is the same value as in step 8.
    10. On Detail, tap Edit. The form opens with:
        - the strength shown under the password
        - the bottom bar and FAB hidden.

        Then:
        - Clear the password and type `password` → "Strength: Weak".
        - With the keyboard open, Save is visible without scrolling, directly above the keyboard.
        - Tap Generate → Use → Save → Detail shows the new value when revealed.
    11. On Detail, double-tap Edit quickly → one Edit screen. System Back once → back on Detail (not a second Edit).
    12. Home overlap: add 6 or more credentials (or enough to scroll). Scroll the list → rows are clipped under the search field and never drawn through it. Repeat the 03 repro: type `gitf`, then replace it with `GIT`.
    13. Delete vs Lock: on Home, tap a row's Delete and then Lock immediately → unlock → no Undo Snackbar. The row is gone.
    14. `adb logcat` during 3–13 → no generated values, `password` or `correcthorse`.

## Round 2 (fixes from review/verify)
- Addressed findings (review.md Round 1):
  - #1 (major): a repeat now counts as zero only if the same character appeared within the previous 3 characters (`REPEAT_WINDOW`). The ±1 sequence rule is unchanged. `aaaa`, `abab` and `abcabc` are still caught.
  - #2: `PasswordStrengthIndicator` has an overload that takes a `PasswordStrength`. Setup computes the level once and passes it in.
  - #3: the length test uses `assertThrows`.
  - #4 (seeding non-uniformity): not changed, per the coordinator.
- Scores now (bands unchanged):
  - `correcthorse1` → Fair (56.9 bits; was Weak).
  - `password` → Weak (32.9).
  - `Kx9#mP2$vL7q` → Good (78.8).
- Simulated with a Python mirror of the scorer, 20 000 uniform random strings each:
  - 64 digits: Strong 99.99 %.
  - 20 lowercase: Good 67 %, Strong 32 %, Fair 0.75 %.
  - 20 of all four classes: Strong 100 %.
  - 12 of all four classes: Good 98 %, Fair 2 %.
- Remaining limitation: 20 random digits (≈ 66 bits true) still score Weak about 54 % of the time and Fair 46 %. The ±1 sequence rule hits 2 in 10 digit pairs, and the pool is small. A digits-only preview in the generator sheet will often show Weak/Fair. I left this alone because the instruction was only to keep the sequence rule. Narrowing the sequence rule to runs of 3 or more would fix it.
- Commits (`e2fe459..HEAD`):
  - 9cb2ed2 fix: discount only short-range repeats in the strength score
  - 550b0b2 refactor: compute the setup password strength once
  - 6edc1ba test: use assertThrows for the generator length check
  - plus this `docs:` commit
- Tests: 97, 0 failures (+2).
  - `PasswordStrengthTest` now has 9 tests, using fixed strings:
    - `aaaaaaaa`, `aaaaaaaaaaaa` and `abababababab` → Weak
    - `abcdefgh`, `abcdefghijkl` and `987654321098` → Weak
    - a fixed 64-digit random string is not Weak
    - three fixed random 20-character lowercase strings are Good or Strong
  - The earlier band tests and the 100 generated defaults → Strong still pass.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS. Each commit compiled, and its tests were run.
- Verifier changes to the Round 1 flow:
  - Step 2: `correcthorse1` now shows "Strength: Fair" with no warning.
  - Step 5: with Symbols and Digits off, the preview strength should be Good or Strong.
