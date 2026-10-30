# Review – Encrypted vault

## Round 1
- Commit range reviewed: 5b23373..41c2496
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | presentation/LockScreen/LockViewModel.kt:45-54 | `submit` catches only `WrongPassword` and `CorruptHeader`. Any other failure (SQLCipher open `SQLiteException`, header `IOException`, Argon2 error on a real device) is uncaught in `viewModelScope` and crashes the app with no message. On unlock this repeats on every attempt. | Map other `Exception`s to a generic `LockError` (still rethrow `CancellationException`), so the user sees an error instead of a crash. |
| 2 | minor | data/CredentialDatabase.kt:20,25 | In sqlcipher-android 4.6.1, `SupportOpenHelperFactory(byte[])` passes `clearPassphrase = false`. The `x'<hex>'` array is kept by the factory for the DB's lifetime and is never wiped, even after `close()`. The VK is effectively still on the heap while unlocked, so "VK zeroed right away" in dev-log is misleading. The comment "the factory may clear it" is also inaccurate for this version. Acceptable in 01, since there is no lock yet. | Fix the comment and dev-log wording. In 02, keep a reference to the raw-key array and wipe it after `database.close()` on lock. |
| 3 | minor | data/vault/VaultSession.kt:33-49,57-63 | Cancellation gaps. If the coroutine is cancelled when `withContext(Default)` returns, `created.vaultKey` / `vaultKey` is dropped without being zeroed. If it is cancelled after the IO block in `setup`, `vault.db` is open and the header is saved, but the state stays `NotSetUp` and the DB handle leaks. | Assign the key before the `try` and use `withContext(NonCancellable)`, or move the zeroing and `_state` update inside the same block that produces the value. Low priority. |
| 4 | minor | presentation/LockScreen/LockViewModel.kt:43, SetupScreen.kt:22, UnlockScreen.kt:19 | The master password lives in immutable `String`s (Compose text state, the call arguments), so zeroing the `CharArray` copy only partly works. The brief asked for CharArray/ByteArray handling. | Accept as a Compose limitation. Note it in decisions or the dev-log rather than implying full zeroing. |
| 5 | minor | MainActivity.kt:84, HomeScreen/CredentialScreen.kt:106 | `isSheetOpen` and the per-row `isPasswordRevealed` use `rememberSaveable`. After process death, the restored values can be consumed when the Unlocked branch first composes, so a row could reappear revealed, or the sheet reopen, right after unlock. | Use plain `remember` for the reveal flag (and the sheet flag), so the masked-by-default state always holds after unlock. |
| 6 | minor | data/CredentialDatabase.kt:26-38, LockViewModel.kt | No unit test for `sqlCipherRawKey` (format `x'` + 64 lowercase hex + `'`, length 67) or for the LockViewModel error mapping and `isWorking` reset. Both are small but on the critical path. | Make `sqlCipherRawKey` `internal` and add one test. A LockViewModel test with a fake session is optional. |
| 7 | minor | data/vault/VaultHeaderStore.kt:24-28 | KDF params read from the header are not range-checked (e.g. `memoryKib = 0` from tampered prefs ends in an uncaught native error, not `CorruptHeader`). Needs root, so it is low risk. | Optionally reject params below `KdfParams.LOW_RAM` as `CorruptHeader`. |
| 8 | minor | git 4e18c3c, 1491cea | 4e18c3c also touches dev-log.md (acknowledged in the dev-log). The 1491cea subject is about 78 characters. | No action. Keep subjects ≤ 72 characters going forward. |

Notes (no action):
- The KDF params match D2 (64 MiB/t3/p4, low-RAM 19 MiB/t2/p1, 16 B salt, 32 B output). AES-256-GCM uses a fresh random 12 B IV per wrap under a per-salt derived key, a 128-bit tag and versioned AAD. `AEADBadTagException` maps to `WrongPassword`.
- There is no secret logging left in `app/src/main`. `BiometricPromptsUtils` logs only status strings. `allowBackup="false"`, and both rule files exclude every domain for cloud backup and device transfer.
- The `when (vaultState)` gating, instead of NavHost routes, is simpler and drops the back stack by construction. This is a reasonable deviation.
- Not verified on a device (Argon2 native, SQLCipher open, UI). The verifier must cover this.

### Checklist
- [x] Correctness / no obvious bugs (minor edge cases 1, 3, 5)
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling) – see 2, 4
- [x] Critical business logic has unit tests; no needless tests (gaps in 6)
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass (`./gradlew assembleDebug testDebugUnitTest --rerun`: 33 tests, 0 failures)

## Round 2
- Commit range reviewed: 09f8ca2..0077e5e
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | presentation/LockScreen/LockViewModel.kt:55-57 | `catch (e: Exception)` does not catch `Error`s, so `OutOfMemoryError` from Argon2 at 64 MiB or `UnsatisfiedLinkError` from a missing native lib still crash the app. The comment "native KDF failures" overstates the coverage. | No change needed for 01. The verifier should watch for these on a low-RAM device. If they show up, handle them in the KDF layer, not with a broad `Throwable` catch. |
| 2 | minor | git dbac512 | The subject is 78 characters, which repeats Round 1 #8. | No action. Keep subjects ≤ 72 characters. |

Round 1 follow-up:
- #1 fixed. `CancellationException` is rethrown before the generic catch, the password `CharArray` is still zeroed in `finally`, and `Unexpected` has a UI message.
- #3 fixed. `NonCancellable + Default` wraps key creation/unwrap, the DB open, the state update and zeroing. Cancellation still propagates: `withContext` rethrows on return once the block finishes, and the ViewModel rethrows it. Side effect: a cancelled unlock still leaves the app-scoped session Unlocked. That is acceptable, because the KDF is not interruptible anyway and the state stays consistent. The header load in `unlock` stays cancellable, which is correct.
- #5 fixed (plain `remember`). The only `rememberSaveable` left is the nav index, which is harmless.
- #2 and #4 fixed in the comment and dev-log, with clear key-lifetime notes for 02.
- #6 fixed. `SqlCipherRawKeyTest` (1 test) and `LockViewModelTest` (5 tests) are lean and use the real session with PBKDF2. The two validation-mapping tests overlap slightly with `ValidationTest`, but they check the error mapping, so that is fine.
- #7 was deferred with a stated reason. Accepted.
- No over-engineering. The changes are small and stay within scope.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise (see 2)
- [x] Build + unit tests pass (`./gradlew assembleDebug testDebugUnitTest --rerun`: 39 tests, 0 failures)
