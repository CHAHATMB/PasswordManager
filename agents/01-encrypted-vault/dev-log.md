# Dev Log – Encrypted vault

## Round 1
- Base commit: 5b23373a034607d6306dcd68523fd269b95629fe
- Commits (review range `5b23373..HEAD`):
  - 0ade35f chore: make gradlew executable
  - 26ff062 chore: align KSP and lifecycle versions, export Room schema
  - 3cff233 refactor: remove broken CryptoManager, CryptographyManager and Gson
  - 0757661 chore: add SQLCipher, Argon2kt and unit-test dependencies
  - 929f4c1 feat: add Argon2id key derivation, vault key wrapping and header store
  - 51382ed test: cover vault key wrapping and header persistence
  - 4e18c3c fix: favourites query uses isFav = 1 for SQLite before 3.23
  - dab2e14 feat: add VaultSession, SQLCipher-backed vault.db and CredentialRepository
  - 2a04a44 test: cover vault session transitions, DAO and repository
  - 2d9b6b5 feat: add master password and credential form validation
  - 2e2eb43 feat: gate the app on vault setup and unlock
  - cbdb884 fix: keep add-form input, save the event payload and close the sheet
  - 1491cea fix: mask passwords with a per-row reveal and drop the disguised delete button
  - c40421d fix: stop text field defaults from printing typed text
  - 4f77c29 fix: disable backups and exclude all app data from backup and transfer
  - plus the `docs:` commit for this log
- Summary of changes:
  - Build: `gradlew` mode 100755, KSP `1.9.0-1.0.13`, single Room compiler (KSP), Gson removed, `lifecycle-viewmodel-ktx:2.7.0`, schema export to `app/schemas/` (`1.json` committed). New deps: `sqlcipher-android:4.6.1`, `androidx.sqlite:2.4.0`, `argon2kt:1.6.0`; test deps: coroutines-test 1.7.3, Robolectric 4.11.1, androidx.test:core 1.5.0.
  - Crypto (`data/crypto/`): `KeyDerivation` fun interface + `Argon2KeyDerivation` (Argon2id, `KdfParams.DEFAULT` 64 MiB/t3/p4, `LOW_RAM` 19 MiB/t2/p1). `VaultCrypto` wraps a random 32 B VK with AES-256-GCM (16 B salt, 12 B IV, AAD `passwordmanager-vault-v<version>`); `AEADBadTagException` → `VaultException.WrongPassword`. Password/derived-key/VK byte arrays are zeroed after use.
  - Vault (`data/vault/`): `VaultHeader`, `VaultHeaderStore` (private prefs `vault_header`, Base64; missing/garbled/wrong-type/bad-length/unknown-version → `CorruptHeader`), `VaultException`, `VaultSession` (`StateFlow<VaultState>` `NotSetUp | Locked | Unlocked(database)`; KDF on `Dispatchers.Default`, DB/prefs I/O on `Dispatchers.IO`).
  - `CredentialDatabase.openEncrypted()` opens `vault.db` via `SupportOpenHelperFactory` with a fresh `x'<hex>'` raw-key array (no String) and forces the open so failures surface during unlock. `exportSchema = true`.
  - `CredentialRepository`: flows `flatMapLatest` on the session state and emit `emptyList()` unless unlocked; writes while locked throw `IllegalStateException`.
  - `PasswordManagerApp` (loads `sqlcipher` once) + `AppContainer` (header store, crypto, KDF params via `isLowRamDevice`, DB opener, legacy delete). ViewModels use `viewModelFactory { initializer {} }` companions.
  - `presentation/LockScreen/`: `LockViewModel`, `SetupScreen`, `UnlockScreen`, shared `LockScreenLayout` (error text + progress). `presentation/Validation.kt` holds master-password and credential-form rules. `components/PasswordTextField.kt`: masked field with reveal toggle, `KeyboardType.Password`, no autocorrect.
  - `MainActivity`: no DB/VM construction; content is a `when` over the vault state; dead vars, `LaunchedEffect(delay)`, commented blocks and `@RequiresApi(P)` removed.
  - Bug fixes: disguised Lock-icon delete removed; sheet hides after Save; `SaveCredential` uses its payload and is ignored when invalid; `CredentialState` is an immutable data class (form text is local to the sheet); Save disabled while website or password is blank; favourites query `isFav = 1`; favourite toggle writes a copy instead of mutating the entity.
  - Home: biometric reveal-all toggle and its `println`s removed; each row shows `••••••••` with its own eye toggle (`rememberSaveable(credential.id)`, list keyed by id).
  - Backups: `allowBackup="false"`; `backup_rules.xml` and `data_extraction_rules.xml` exclude root/file/database/sharedpref/external (cloud + device transfer).
  - Legacy data (D3): setup deletes `credential.db` (and any stale `vault.db`) via `Context.deleteDatabase` (also removes `-wal/-shm/-journal`) before creating the vault.
- Decisions / deviations from brief:
  - Gating uses a `when (vaultState)` in `MainActivity` rather than routes in one NavHost. The Unlocked branch owns its NavController and sheet state, so every transition drops the back stack by construction and nothing from the shell can be composed behind Setup/Unlock.
  - `VaultSession` zeroes its own VK array right after the DB opens. *(Corrected in Round 2.)* The VK is still on the heap while unlocked: `SupportOpenHelperFactory(byte[])` in sqlcipher-android 4.6.1 uses `clearPassphrase = false` and keeps the `x'<hex>'` raw-key array for the DB's lifetime, and it doesn't wipe it on `close()`. Feature 02 needs VK for biometric wrapping. It should either wrap at unlock time or keep the VK in the session.
  - `VaultSession` takes a second lambda, `deleteDatabases`, besides the DB opener, so setup can wipe the legacy and stale DB files and tests can pass a no-op.
  - Add-form fields use `remember`, not `rememberSaveable`, so typed passwords never enter the saved-state Bundle. Cost: text typed in the sheet is lost on rotation.
  - Saving from the sheet no longer navigates to Home underneath. It only closes the sheet. The unreachable `Add` route pops back on save.
  - `AppContainer` has no application `CoroutineScope` or settings yet. Nothing uses them in 01; 02/03 add them.
  - `util/BiometricPromptsUtils.kt` is unused now but kept, because brief 02 lists it as a file to change. Its `Log.d` calls log no secrets.
  - Unchanged dead code (out of scope): the VM search block, the `else -> {}` branch, `TextFireldComponent.kt` (only its `println` defaults were removed), and the Favourites screen's in-UI filtering. Favourites rows now also get the eye toggle because they reuse `CredentialItem`.
  - A corrupt header leaves the app on Unlock with "Vault data is corrupted…". There is no reset path (out of scope).
  - Process slip: `4e18c3c` (favourites fix) also contains the base-sha line of this log, because of a `git commit -a`. Not amended, per the rules.
- Unit tests added (32 new, 33 total including the template test):
  - `data/crypto/VaultCryptoTest` (6): round trip returns the same VK, wrong password → `WrongPassword`, tampered IV/salt/ciphertext fail, two setups give different salt/IV/VK. Uses the test-only `Pbkdf2KeyDerivation`.
  - `data/vault/VaultHeaderStoreTest` (8, Robolectric): absent → null, round trip, missing field, missing KDF param, garbled Base64, wrong type, bad IV length, unsupported version → `CorruptHeader`.
  - `data/vault/VaultSessionTest` (5, Robolectric): no header → NotSetUp, setup → Unlocked + header saved + DBs deleted, new session → Locked, wrong unlock stays Locked, correct unlock → Unlocked.
  - `data/CredentialDaoTest` (4, in-memory Room): upsert insert/update, delete only that row, ordering by website and by date, favourites query.
  - `data/CredentialRepositoryTest` (5, file-backed plain Room): empty while NotSetUp, empty while Locked even with stored data, write while locked throws, store → relaunch → unlock → retrieve, delete.
  - `presentation/ValidationTest` (4): too short, mismatch, valid at 12 characters, blank website/password.
  - `app/src/test/resources/robolectric.properties` pins SDK 34 and uses a plain `Application`, because `PasswordManagerApp` loads native SQLCipher.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (invoked as `./gradlew`).
- Notes for reviewer/verifier:
  - Key lifetime (02 must handle this): `CredentialDatabase.openEncrypted` hands SQLCipher a raw-key array that the factory keeps and never wipes. On lock, 02 must keep a reference to that array, call `database.close()`, then `fill(0)` it.
  - The master password also exists as immutable `String`s (Compose text state, the ViewModel call arguments), so zeroing the `CharArray`/`ByteArray` copies only shortens their lifetime. This is a Compose limitation.
  - Not run on a device: the emulator would not start (host disk space too low). Argon2kt, SQLCipher raw-key open and the UI flows are therefore unverified on device. Check first that Setup completes without a crash (native libs load, Argon2 at 64 MiB).
  - Argon2 is called through its `ByteBuffer` overload with our own direct buffers, wiped afterwards. Buffer helpers use absolute `put(index, …)` to avoid the `flip()`/`clear()` covariant-return `NoSuchMethodError` on older Android.
  - Manual test flow (package `com.example.passwordmanager`):
    1. `./gradlew assembleDebug` → install `app/build/outputs/apk/debug/app-debug.apk` on a fresh emulator (`adb uninstall` first).
    2. Launch → "Create master password" screen with the cannot-be-recovered notice; no bottom bar.
    3. Enter `short` + `short` → "Master password must be at least 12 characters". Enter `correcthorse1` + `correcthorse2` → "Passwords do not match". Fields clear after each submit.
    4. Enter `correcthorse1` twice → spinner → empty Home with bottom bar.
    5. FAB → sheet. "Added" is disabled. Type website only → still disabled. Add a password → enabled. The password field is masked, and its eye shows it. Save `github.com / alice / gh-secret-1` → the sheet closes and the row appears. Repeat with `example.com / bob / ex-secret-2`. Type slowly and check that text never disappears.
    6. Rows show `••••••••`. Tap row 1's eye → `gh-secret-1`, row 2 still masked. Tap row 2's eye → `ex-secret-2`.
    7. The favourite heart toggles without removing anything. Delete removes only that row (re-add it afterwards for the next steps).
    8. `adb shell am force-stop com.example.passwordmanager`, relaunch → "Unlock vault" screen, no rows visible.
    9. Wrong password → "Wrong master password", still locked. `correcthorse1` → both rows, and both passwords reveal correctly.
    10. `adb shell run-as com.example.passwordmanager head -c 16 databases/vault.db` is not `SQLite format 3`. Pull a copy (`adb exec-out run-as com.example.passwordmanager cat databases/vault.db > vault.db`), then `strings vault.db | grep gh-secret-1` returns nothing. `run-as … ls databases/` has no `credential.db`.
    11. Upgrade: `git checkout a328dad`, install, add a credential, then install this build with `adb install -r` → Setup is shown. After setup, Home is empty and `credential.db` is gone.
    12. `adb logcat` during steps 3–9: grep for the master password, saved passwords, and any hex/Base64 key or IV. Expect no hits.
    13. `adb shell dumpsys package com.example.passwordmanager | grep -i flags` → no `ALLOW_BACKUP`.

## Round 2 (fixes from review/verify)
- Addressed findings (review.md Round 1):
  - #1: `LockViewModel.submit` maps any other `Exception` to `LockError.Unexpected` ("Something went wrong. Please try again.") and rethrows `CancellationException`. This covers both setup and unlock.
  - #3: `VaultSession.setup`/`unlock` run key creation or unwrap, the DB open, the state update and key zeroing in one `withContext(NonCancellable + Dispatchers.Default)` block. Cancellation can no longer drop an unzeroed key or leak an opened DB with the state stuck at NotSetUp. The existing close-on-header-save-failure still applies.
  - #5: the per-row `isPasswordRevealed` and `isSheetOpen` use plain `remember`, so rows are always masked and the sheet is closed after unlock.
  - #2/#4: fixed the raw-key comment in `CredentialDatabase` and the dev-log wording. Added key-lifetime and String notes under Round 1 "Notes".
  - #6: `sqlCipherRawKey` is now `internal`. Added `SqlCipherRawKeyTest` (1) and `LockViewModelTest` (5, Robolectric, real `VaultSession` with PBKDF2 and in-memory Room, `Dispatchers.setMain`): too short, mismatch, wrong password, SQLCipher failure → Unexpected (still NotSetUp), correct unlock.
  - Not done: #7 (the KDF param range check is not a one-liner, because the tests store `memoryKib = 0` headers) and #8 (no action).
- Commits:
  - e87b7ce fix: show a generic error when vault setup or unlock fails unexpectedly
  - dbac512 fix: zero the vault key and keep the DB handle if setup or unlock is cancelled
  - f4dc890 fix: keep password reveal and sheet flags out of saved state
  - 34544f6 fix: correct the SQLCipher raw-key lifetime comment (also makes `sqlCipherRawKey` internal)
  - a56bd27 test: cover LockViewModel error mapping and SQLCipher raw-key format
- Tests: 39 total, 0 failures.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS.
- Still not verified on a device (disk space).
