# Review – App lock and hardening

## Round 1
- Commit range reviewed: 14d0954..2d08d33
- Verdict: CHANGES_REQUESTED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | major | presentation/LockScreen/LockViewModel.kt:75-89, MainActivity.kt:99 | `takeBiometricUnlockCipher()` only catches `KeyPermanentlyInvalidatedException`. Any other Keystore failure (`UnrecoverableKeyException`/`KeyStoreException` on some OEMs after lock-screen or enrolment changes, `ProviderException`, or `InvalidAlgorithmParameterException` from a bad stored `bio_iv`) is thrown inside `withResumed` on the main thread and crashes the app. Because the prompt is due on every cold start, this is a crash loop that also blocks the master-password field. | Also catch `GeneralSecurityException` and `ProviderException` and handle them like invalidation (disable biometrics, show the message). Add a unit test with a key store that throws. |
| 2 | minor | data/vault/VaultHeaderStore.kt:32,40,64 | A non-Base64 `bio_iv`/`wrapped_vk_bio` throws `IllegalArgumentException`, which `load()` turns into `CorruptHeader` for the whole header, so the master password stops working too. This contradicts the dev-log decision that a bad biometric blob means "biometrics off". | Decode the biometric fields in `loadBiometric()` under their own try and return null on failure. |
| 3 | minor | data/vault/VaultSession.kt:60,78 | Screen-off (or the manual Lock) during an Argon2 unlock is a no-op because the state is still `Locked`, so the vault opens after the KDF finishes with the screen off. With a timeout greater than 0 it then stays open on return. This is documented, but it is a gap in "lock on screen off". | Track a lock request in `lock()` while an unlock is in flight (or recheck it in `openVault` before publishing `Unlocked`) and close right away. |
| 4 | minor | presentation/CredentialViewModel.kt:65,75 | A lock while an upsert or delete is in flight closes the DB under it, or `requireDao()` throws, which is uncaught in `viewModelScope` and crashes the app. The window is narrow (screen off right after Save). This is documented. | Catch `IllegalStateException`/`SQLiteException` in the repository write path, or defer `close()` until in-flight writes finish. Can be handled in feature 03. |
| 5 | minor | presentation/LockScreen/LockViewModel.kt:64 | The backoff gate (unlock ignored while blocked, counter reset on success, shared throttle across a new ViewModel) has no test. Only the pure delay function is covered. | Add a `LockViewModelTest` case: three wrong unlocks, then the 4th call is ignored while `retryInSeconds > 0`. |
| 6 | minor | test/.../AutoLockTest.kt | `oneMinuteStaysUnlockedAfterThirtySecondsAndLocksAfterSixty` is already covered by the two boundary tests. | Drop it, or keep it as the brief's acceptance example. No action is required. |
| 7 | minor | commit 3c08b38 | The unit tests don't compile at this commit (missing `deleteBiometricKey` until 9ba13e6). This is acknowledged in the dev-log, and history must not be rewritten. | No action. Keep every commit building its tests in later rounds. |

Notes (no action):
- The Keystore spec is right: auth per use (`0, AUTH_BIOMETRIC_STRONG` / `-1`), invalidated by enrolment, and StrongBox falls back to TEE. Every `authenticate` call carries a `CryptoObject`.
- `lock()` sets `Locked` before closing, so `flatMapLatest` drops the DAO flows first. Both key arrays are zeroed, and `openVault` zeroes them on failure.
- FLAG_SECURE is set before `setContent`, recents screenshots are off on API 33+, and the sheet uses `SecureOn`. There are no other dialogs or popups, and no `Log` calls remain in `app/src/main`.
- The backoff can only be bypassed by killing the process, which the brief accepts (the count is in memory). Argon2 is still the real cost.
- `assembleRelease` passes locally with R8 enabled. Runtime behaviour of the minified build is left to the verifier.

### Checklist
- [ ] Correctness / no obvious bugs (finding 1)
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [ ] Critical business logic has unit tests; no needless tests (finding 5, minor)
- [x] Commit messages follow convention and are concise (all subjects 72 characters or fewer, no bodies)
- [x] Build + unit tests pass (`./gradlew assembleDebug testDebugUnitTest`: 60 tests, 0 failures; `assembleRelease` OK)

## Round 2
- Commit range reviewed: 7023304..b143e41
- Verdict: APPROVED

### Round 1 findings
- #1: fixed. `takeBiometricUnlockCipher()` also catches `GeneralSecurityException`/`ProviderException`, and `KeyPermanentlyInvalidatedException` is still caught first. `disableBiometric()` removes the blob before deleting the key, and `deleteKey()` is best effort, so a broken Keystore can no longer cause a crash loop. A test covers it, using Robolectric's missing `AndroidKeyStore` and asserting that precondition.
- #2: fixed. Biometric Base64 is decoded under its own try, and a test covers it.
- #3: fixed. The `lockRequests` counter plus `compareAndSet` in `lock()` is correct for both orderings: a lock before `Unlocked` is published, and a lock between publishing and the recheck. Both keys are zeroed once, and the DB is closed once.
- #4: fixed. `VaultException.Locked` is mapped only when the session is no longer unlocked, other errors are rethrown, and the ViewModel drops it quietly.
- #5 / persisted backoff: correct. The clamp `coerceIn(0, delay)` caps a clock set backwards at one full delay. A clock set forward skips the wait, which is accepted and documented. Prefs are excluded from backup by `data_extraction_rules`. `retryInSeconds` is set together with the error, and `init` starts the countdown, so a cold start shows the remaining wait.
- #6: done. #7: the dev-log says every commit builds with its tests (not re-checked here).

### New findings
| # | Severity | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | data/CredentialRepository.kt:34 | `catch (e: IllegalStateException)` also catches kotlinx `CancellationException` (it is a subclass). When the vault is locked, a cancellation gets turned into `VaultException.Locked`. This is harmless today, because the job is already cancelled and the ViewModel drops `Locked`, but it breaks cancellation propagation for future callers. | Rethrow `CancellationException` before the `IllegalStateException` catch. This can go in feature 03. |
| 2 | minor | presentation/LockScreen/LockViewModel.kt:99 | `turnOffBiometric` launches `disableBiometric()` without a catch. A failed header `commit()` (`IOException`, e.g. a full disk) would crash, and this path can now be reached from any Keystore failure. The window is narrow. | Optional: catch `IOException` in the launch. The worst case is the prompt being skipped again on the next launch. |

Notes (no action):
- `UnlockThrottle` calls `commit()` on the main thread. It is a tiny write and matches `VaultHeaderStore`.
- `UnlockThrottleTest.successResetsTheCount` overlaps with the last assertion of the `LockViewModelTest` backoff test. Both are cheap, so no change is needed.
- After a relock during an unlock, `Unlocked` shows briefly, and then the biometric prompt is due again on `Locked`. This is expected.

### Checklist
- [x] Correctness / no obvious bugs (2 minor, non-blocking)
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise (all subjects 71 characters or fewer, no bodies)
- [x] Build + unit tests pass (`./gradlew assembleDebug testDebugUnitTest`: 66 tests, 0 failures)
