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
