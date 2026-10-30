# Review – TOTP authenticator

## Round 1
- Commit range reviewed: 0bf273d..7b0d0e5
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | data/backup/BackupRepository.kt:64-77 | Credentials and TOTP accounts are inserted in two separate transactions. A lock between them leaves credentials imported, TOTP not, and the user sees only a Locked error (no counts). Re-import fixes it (duplicates skipped). | Accept, or run both inserts in one `withTransaction`. |
| 2 | minor | util/totp/Base32.kt:31-47 | "Drop leftover bits" deviation accepted (matches Google Authenticator / commons-codec lenient mode; codes unaffected). Side effect: for key lengths not a multiple of 8 chars, the normalised stored key can differ from the typed one in its last character (e.g. non-zero trailing bits), which 10's export will show. | Accept; mention in 10 if export shows the key. |
| 3 | minor | util/totp/Base32.kt:32 | Only spaces and hyphens are ignored; a key pasted with a tab or line break is rejected as invalid. | Optional: filter `isWhitespace()`. |
| 4 | minor | util/QrImageReader.kt:32-35 | The pixel `IntArray` (which can encode the secret) isn't cleared after decoding. Negligible; it's GC'd right away and the bitmap is recycled. | Optional `pixels.fill(0)` after decoding. |
| 5 | minor | presentation/Authenticator/AuthenticatorScreen.kt:144,153 | Countdown uses `36.dp` / `3.dp` literals. No colours or sp are hardcoded, so this is within the 07 rules. | Accept. |
| 6 | minor | MainActivity.kt / build | `assembleRelease` (R8 on) succeeds and the merged release manifest has only `SecureCaptureActivity`, but the scanner was not exercised in a minified build. | Verifier: if time allows, scan once with the release APK. |

### Notes
- TOTP/HOTP: dynamic truncation (offset from the low nibble of the last byte, top bit masked), `floorDiv(time, period)`, big-endian 8-byte counter, SHA1/256/512 via `Mac`, modulus up to 10^8 fits `Int`, `padStart` keeps leading zeros. Tests cover RFC 4226 App. D, RFC 6238 App. B ×3 algorithms, `JBSWY3DPEHPK3PXP`@59 and `secondsRemaining` boundaries.
- otpauth parsing: 4 KB cap, scheme/type case-insensitive, label and params URL-decoded (`%3A`, `%20`, `+`), bad `%` → `InvalidParameter`, `issuer` param wins over label prefix, defaults SHA1/6/30, digits {6,8}, period 1–300, secret 1–128 bytes. All validation funnels through `OtpAuthAccount.create`, reused by manual entry and backup import. `build` round-trips.
- Countdown: ticker aligned to the next wall-clock second; codes recomputed per tick on `Dispatchers.Default`; copy recomputes the code at tap time, so a copy at a boundary gets the current code. Warning colour ≤ 5 s uses `extendedColors.warning`.
- Room: `@AutoMigration(1→2)` adds only the `TotpAccount` table; `2.json` committed; `CredentialDatabaseMigrationTest` builds v1 from `1.json` and opens it with v2.
- Backup: header v2, `BackupContent` with `totp` defaulting to empty; v1 bare array still decrypts; version is in the AAD so relabelling fails (tested). TOTP entries re-validated on import, duplicates by normalised secret.
- `VaultAccess` refactor: logic moved verbatim (Cancellation rethrown, ISE/SQLException → Locked only when locked); `CredentialRepository` behaviour unchanged.
- Security: secrets only in SQLCipher DB / encrypted backup; `toString()` of `TotpAccount`, `OtpAuthAccount`, `BackupTotp` omit the secret; no logging added. Main window already FLAG_SECURE; scanner sets FLAG_SECURE + no recents screenshot; all sheets/dialogs `SecureOn`; library `CaptureActivity` removed from the merged manifest (verified); CAMERA permission present, camera features `required="false"`. Copy uses `SecureClipboard.copy` (sensitive clip, 30 s clear, cleared on lock). Setup key field uses password keyboard type. Dialog state (incl. scanned secret / half-typed key) cleared on lock; scan/image results dropped unless unlocked.
- Image picker wrapped in `expectAppTrip`/`endAppTrip`; camera scanner stays in-process so it correctly isn't an app trip (`ProcessLifecycleOwner`).
- Tests are focused on the critical logic; none look needless.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass (`./gradlew assembleDebug testDebugUnitTest assembleRelease`: PASS, 177 tests, 0 failures; release APK built unsigned, no signing error)
