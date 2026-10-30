# Password generator

Status: DONE
Backlog ID: 04

## Goal
Users can generate a strong random password from inside the add and edit forms. A strength indicator shows how good any password is, including the master password at setup.

## Scope
- In:
  - `PasswordGenerator` using `SecureRandom`:
    - Length 8–64, default 20.
    - Toggles for uppercase, lowercase, digits and symbols (all on by default), and "avoid ambiguous characters" (`0 O o l 1 I |`).
    - Guarantees at least one character from each selected class.
    - Rejects the "no classes selected" case with a specific error. The UI prevents it.
  - A generate icon in the add/edit password field opens a bottom sheet (`SecureOn`) with:
    - a preview
    - a length slider
    - the class toggles
    - a regenerate button
    - a "Use" button that fills the field
  - `PasswordStrength` estimator: an entropy estimate from character-pool size × length, with a penalty for repeated characters. Output levels are Weak, Fair, Good and Strong.
  - The level is shown under the password field in add/edit and in master-password setup. Setup only warns on Weak. The 12-character minimum from 01 still applies.
- Out: passphrase mode with a wordlist, generator history, a Quick Settings tile, zxcvbn, breach checks.

## Acceptance criteria
- [ ] The generate icon in the add form opens the generator sheet with a 20-character preview.
- [ ] Moving the slider to 8 and to 64 changes the preview length to match. Regenerate produces a different value.
- [ ] Turning off symbols and digits produces previews with letters only. Turning on "avoid ambiguous" produces previews without `0 O o l 1 I |`. The last remaining class toggle can't be turned off.
- [ ] "Use" fills the password field, and the saved credential shows that exact password when revealed.
- [ ] Typing `password` shows Weak. A generated 20-character password shows Strong.
- [ ] The master-password setup on a fresh install shows the strength indicator.

## Design notes
- Affected files/layers: new `util/PasswordGenerator.kt`, `util/PasswordStrength.kt`, `presentation/components/GeneratorSheet.kt`, `AddScreen/AddCredentialScreen.kt`, `LockScreen/SetupScreen.kt`.
- `PasswordGenerator` takes `SecureRandom` as a constructor parameter, so tests can seed it.
- Data model / migration changes: none.
- Security considerations:
  - Never use `kotlin.random.Random` or `java.util.Random`.
  - Shuffle the guaranteed characters with the same `SecureRandom` instance.
  - Don't log generated values.

## Critical logic needing unit tests
- Output length equals the requested length for 8, 20 and 64.
- Each selected class appears at least once. Unselected classes never appear. Ambiguous characters are excluded when the option is on (run each check over many iterations).
- Selecting no classes throws the specific error.
- Strength levels for representative inputs: an empty string, `password`, `aaaaaaaaaaaa`, a mixed 12-character password, and a generated 20-character password.

## Dependencies
- 01 (setup screen). 03 (edit form).

## Carried over from 03 review/verify
- [ ] Home list rows must not scroll under/overlap the search field (CredentialScreen.kt: give search a background or move it out of the list's content padding)
- [ ] Edit screen: Save reachable/visible with keyboard open; bottom bar + FAB must not float above the keyboard
- [ ] Double tap on Edit in Detail must not open Edit twice
- [ ] Delete in flight when Lock is tapped must not offer Undo after unlock
