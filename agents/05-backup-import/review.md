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
