# Review – TOTP import and export

## Round 1
- Commit range reviewed: 1ca3dba..e2529e3
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | util/totp/ProtobufReader.kt `readVarint` | A 10th byte > 0x01 overflows silently: its high bits are shifted out instead of rejected. Harmless here, since every value is coerced or compared to small enums, but it isn't strict. | Optional: throw when `index == 9 && byte > 1`. |
| 2 | minor | presentation/Authenticator/AuthenticatorViewModel.kt `ImportMigration` / `scannedDialog` | The import path has no VM test: summary math (`duplicates = accounts.size - imported`), the all-skipped shortcut straight to `Imported`, the lock check after insert, and `ScanSource` for "Scan next code". The parser and the DAO are covered, but this glue isn't. | Add a small `AuthenticatorViewModel` test with fakes when one exists for this VM. Not needed to ship. |
| 3 | minor | presentation/components/PasswordTextField.kt:51 | Setup-key toggle reads "Show/Hide password" (accepted deviation). | Later: an optional reveal-label parameter, e.g. "Show setup key". |
| 4 | minor | presentation/Authenticator/TotpExportViewModel.kt `verify` | Only `WrongPassword` and `Locked` are caught. Anything else from `loadHeader`/`crypto.unlock` (corrupt header) crashes the coroutine. The backup flow has the same shape, so this is consistent and not new. | Leave as is, or map unexpected exceptions to an error state. |
| 5 | minor | MainActivity.kt export route (dev-log note) | After a lock, the orphaned NavController's back-stack VM is never cleared. Its state is wiped (Warning, no accounts, no bitmap), but each lock-on-export cycle leaves one idle VM and its collector until the activity dies. | Acceptable. Note it in case the shell is ever restructured. |
| 6 | minor | util/totp/OtpAuthUri.kt `splitLabel` | Known limitation: with a blank issuer, an account name containing `:` is split on export → re-import (`a%3Ab` decodes to `a:b`). The format is ambiguous there, and the dev-log documents it. | None. |
| 7 | minor | presentation/Authenticator/AuthenticatorDialogs.kt (setup key) | Dropping `KeyboardCapitalization.Characters` means lowercase input. Decode uppercases, so this is cosmetic only. | None. |

### Notes on the checked areas
- Protobuf reader: varint (multi-byte, more than 10 bytes rejected), tags (field 0, field above `Int.MAX`, negative tag → rejected), length-delimited reads with `length < 0 || length > remaining`, skips of wire types 0/1/2/5 with bounds checks, groups rejected. Repeated `otp_parameters` is a plain loop. It's small and readable (~65 lines), matching D10.
- Payload: the 8 KB cap comes before decoding. Percent-decoding, then `' '`→`+`, URL-safe → standard, and padding trimmed (`java.util.Base64` accepts unpadded). Every `IllegalArgumentException`/`ProtobufException` → `MalformedPayload`, so the result is all-or-nothing and nothing is inserted on error. The 100-entry cap works. Enum mapping matches the research schema: UNSPECIFIED → SHA1/6, MD5/HOTP/unspecified type/unknown digits → skipped and counted. Each entry goes through `OtpAuthAccount.create`. Batch index and size are coerced sensibly. Duplicates are handled by 09's `insertSkippingDuplicates` (`@Transaction`, same-secret dedupe, also within a batch). Secret and payload byte arrays are zeroed.
- Export: the gate uses the existing `VaultSession.verifyMasterPassword`, the same call as the backup flow. Accounts load only after it succeeds, and there's a second lock check after `getAll`. A lock or `onCleared` → `clear()` (job cancelled, list dropped, Warning). The VM is scoped to the back stack entry. Only the visible page has a bitmap, rendered on `Default`, and a stale render is ignored by an index check. The window FLAG_SECURE is global (`MainActivity:129`), and `ConfirmDialog`/`PasswordDialog` are `SecureOn`. The compatibility warning checks the algorithm, digits and period against the defaults. The round trip is covered by `OtpAuthUriTest` (3×2×4) and by the VM test re-parsing the encoded URIs.
- Deviations: all four are accepted. The issuer-with-colon fix is correct and minimal: only the exact `"<issuer>:"` prefix is removed, otherwise the old behaviour holds. Splitting `Issuer:account` when the issuer is set matches real Google exports and the otpauth rule. Putting re-auth on the export route keeps the gate in a testable VM, and cancel pops the route. The "Show password" label is item 3.
- Task B (09 verify issues 2–4 + whitespace): each fix is minimal and in its own commit. `isWhitespace()` in Base32 is tested. The code moved to an `ItemRow.footer` (optional parameter, default unchanged). The countdown uses a plurals resource. The setup key reuses `PasswordTextField` (with a new optional `error`).
- Reuse/style: `PasswordDialog` was moved to components instead of duplicated (the refactor commit only changes the error type to `String?`), and `splitLabel`, `OtpAuthAccount.create`, `insertSkippingDuplicates` and `extendedColors.warning/onWarning` are reused. No new hardcoded colours or sp, and no logging in app code. Test `println`s only print test vectors.
- Tests: the critical decoding and mapping are covered (research vector, encodings, skips, splits, unknown fields, 11 malformed cases, wrong scheme), and so is the re-auth gate, including "wrong password never encodes". None are needless.
- Commits: conventional, one logical change each, concise subjects.
- Build: `./gradlew assembleDebug testDebugUnitTest` → PASS (200 tests, 0 failures, 0 errors).

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests (gap: item 2, minor)
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass
