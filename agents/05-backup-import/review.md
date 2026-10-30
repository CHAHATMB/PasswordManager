# Review – Encrypted backup and import

## Round 1
- Commit range reviewed: 7fe8211..3bac378
- Verdict: CHANGES_REQUESTED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | major | `AutoLockObserver.kt:46-49`, `BackupSection.kt:54,85,109` | With auto-lock "Immediately", every SAF picker trip sends the app to the background, so the vault locks on return and the result is dropped. Export, Import backup and Import CSV can then never work, and the user gets no feedback. Export is the only protection against losing the phone, and "Immediately" is the setting the most security-minded users pick. On export the picker has already created an empty `passwords-export.json`, which stays behind. | Let the observer skip one background trip that the app starts itself: set a flag right before `launch(...)`, clear it in `onStart` and in the result callback, and still lock if the trip lasts longer than a short cap (for example 5 min). Screen-off keeps locking. If this is out of scope, at least tell the user before opening the picker instead of failing silently. |
| 2 | minor | `CsvImporter.kt:28-32,42` | `RFC4180` keeps empty lines (`ignoreEmptyLines=false`), so every blank line becomes a row that is counted as "invalid". All rows are built as `Credential`s and kept in a list before `partition`, so a 10 MB file of `\n` creates about 10 M objects, which is enough to OOM-crash a normal Android heap. The 10 MB cap does not bound the row count. | Use `.setIgnoreEmptyLines(true)`, and count invalid rows while parsing instead of keeping them (or cap the row count). |
| 3 | minor | `CsvImporter.kt:59` vs `:87` | `layoutOf` trims header names, but `value()` looks them up untrimmed. A header such as ` url` passes the column check, then every row reads `""` and is reported as invalid instead of `MissingColumns`. | Compare headers the same way in both places: drop the `trim()` in `layoutOf`, or look values up by the trimmed name. |
| 4 | minor | `BackupCrypto.kt:88` | `memoryKiB` may be 0 (the bound fits the PBKDF2 test stand-in). Argon2 needs at least 8×p KiB, so a crafted file fails in native code and the user sees "Something went wrong" instead of "damaged". The 256 MiB upper bound is also more than a low-RAM device can give Argon2, while this app only ever writes 64 MiB or less. | Use `8 * parallelism..MAX` (and give the test params a valid memory value). Consider a lower cap, such as 128 MiB. |
| 5 | minor | `ImportFile.kt:32` | If `read()` throws an `IOException` partway through, the partially filled buffer (possibly plaintext CSV) is not wiped. | Wrap the loop in `try/catch` (or `finally` on failure) and call `buffer.fill(0)`. |
| 6 | minor | commit `7f57c6b` | The subject says it keeps the fixture's CRLFs, but it only adds `.gitattributes`. The fixture is fixed in `074644b`. This is already disclosed in the dev-log; nothing to do, since history is not rewritten. | — |

Notes (no action):
- Crypto reuses `derivePasswordKey`/`aesGcmCipher`/`randomBytes` and adds no new primitives. Salt and IV are fresh per export, the AAD is the canonical header encoding (every field is covered, and unknown keys are rejected because `Json` is strict), and the version is checked before any KDF work. KDF params and file size are bounded, and a wrong password or tampering maps cleanly to `WrongPassword`.
- No logging, temp or cache files. Only ciphertext waits in the ViewModel. Password `String`s in Compose state match the rest of the app.
- I/O runs on IO, and the KDF and parsing on Default. Streams are closed with `use`. Process death mid-flow loses only the pending ciphertext or parsed header (the vault is locked in a new process anyway), which is acceptable.

### Checklist
- [ ] Correctness / no obvious bugs (finding 1)
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass (`assembleDebug testDebugUnitTest`: 127 tests, 0 failures)

## Round 2
- Commit range reviewed: fa69bf3..648e12e
- Verdict: APPROVED

### Round 1 findings
- #1 fixed. `expectAppTrip()` is armed only right before a picker launch (`MainActivity.openBackupPicker`). The next process `ON_STOP` consumes it, and the following `ON_START` is the only check that uses it. `endAppTrip()` in every result callback and on `ActivityNotFoundException` clears a flag that a picker closing within the `ProcessLifecycleOwner` stop delay would otherwise leave armed for a real trip. The skip can't be stretched: it covers one trip, the limit is `max(timeout, 5 min)`, and the screen-off receiver is untouched. A lock during the trip dismisses the dialog, which clears `pendingExport`, so the late `SaveBackup` deletes the empty document. `save()` also deletes the document on `IOException`/`SecurityException`. Moving the launchers to the activity is what makes that late result arrive at all, so it's justified.
- #2 fixed. `setIgnoreEmptyLines(true)`, and invalid rows are counted, not kept.
- #3 fixed. One trimmed, lowercased name → index map is used for both the column check and the value reads, and dropping `setIgnoreHeaderCase` is correct.
- #4 fixed. Parallelism is checked first, so `8 × p` is safe. The 128 MiB cap is above the app's 64 MiB `DEFAULT`. Test params are now valid (16 KiB).
- #5 fixed. The `finally` wipe runs after `copyOf(size)` builds the return value, and every exit path is covered.

### New findings
| # | Severity | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | `AutoLockTest.kt` | Only `shouldLock` is tested. The security-relevant part is the flag sequencing in `AutoLockObserver`: armed → consumed by one stop → cleared after start or by `endAppTrip`, so the second trip locks normally. That part has no test. | Optional: add a Robolectric test that drives `onStop`/`onStart` and checks that a second trip with "Immediately" locks. |
| 2 | minor | `SettingsViewModel.kt` (`SaveBackup`) | The delete-on-lock path depends on the lock's `Dismiss` having cleared `pendingExport` before the result arrives. It does today (the lock happens in `ON_START`, before the result is delivered). If that ever changes, a locked `runBackup` returns early and leaves the empty file behind. | No action needed. Treat it as a note for the verifier's "wait more than 5 min" check. |
| 3 | minor | `ImportFile.kt:6` | The 5 MB cap departs from the brief's 10 MB (the coordinator asked for it; it's disclosed). | Planner: update `brief.md` so the verifier expects 5 MB. |

Notes (no action):
- No over-engineering. The deletion helper's four specific empty catches match the "specific errors" rule. `toParsedImport` is still used by the backup import.
- Lock-on-return is still evaluated only at `ON_START`, so the key stays in memory while the app is in the background. That behaviour predates this feature and isn't changed here. Screen-off still locks at once.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (skip bounded to one trip and 5 min, screen-off lock intact, buffers wiped, empty export removed)
- [x] Critical business logic has unit tests; no needless tests (see finding 1)
- [x] Commit messages follow convention and are concise
- [x] Unit tests pass (`testDebugUnitTest`: 132 tests, 0 failures)
