# Encrypted vault

Status: PLANNED
Backlog ID: 01

## Goal
The user creates a master password on first launch, and every credential is stored in an encrypted SQLCipher database. After a relaunch, the vault opens only with the correct master password, and the saved credentials decrypt correctly. This feature also includes the build fixes and critical bug fixes that the vault work depends on.

## Scope
- In:
  - Build fixes: commit `gradlew` with the exec bit (`git update-index --chmod=+x gradlew`), align KSP with Kotlin 1.9.0 (`1.9.0-1.0.13`), remove the duplicate `annotationProcessor` Room compiler, remove Gson, align `lifecycle-viewmodel-ktx` with lifecycle 2.7.0, add a Room schema export dir (`app/schemas`).
  - Unit-test dependencies: `kotlinx-coroutines-test`, Robolectric, `androidx.test:core`.
  - Delete `util/CryptoManager.kt` and `util/CryptographyManager.kt` and every use and import of them. Remove all `println`/`Log` output of secrets.
  - `PasswordManagerApp` + `AppContainer` (manual DI, decisions D4). ViewModels are built with `viewModelFactory { initializer {} }`.
  - Crypto and vault: Argon2id KDF (D2), AES-256-GCM wrap/unwrap of a random 32-byte vault key, a vault header in private SharedPreferences, `VaultSession` with `StateFlow` states `NotSetUp | Locked | Unlocked`, and a SQLCipher-backed Room DB `vault.db` (D1).
  - `CredentialRepository` between the ViewModel and the DAO. The ViewModel no longer touches the DAO.
  - Setup screen: master password + confirm, at least 12 characters, and a "cannot be recovered" notice. Unlock screen: password field, progress indicator while the key is derived, and a "Wrong master password" error.
  - Navigation gated on the session state: `NotSetUp` shows Setup, `Locked` shows Unlock, `Unlocked` shows the existing shell. Clear the back stack on every transition.
  - Critical bug fixes from `codebase.md`:
    - Remove the Lock-icon button that sends `DeleteCredential`.
    - Close the bottom sheet after Save.
    - `SaveCredential` uses its event payload.
    - Typed form text survives list re-emissions. Move the form to immutable state, or to local saveable state in the sheet, and drop the `MutableState` fields from `CredentialState`.
    - Favourites DAO query uses `isFav = 1`.
  - Minimal validation: Save is disabled while website or password is blank.
  - Replace the non-crypto biometric "reveal all" toggle on Home with a per-row eye toggle, masked by default. Mask the password field in the add form, with a reveal toggle.
  - Backups: `allowBackup="false"`, plus exclusion rules in `data_extraction_rules.xml` and `backup_rules.xml`.
  - Destructive migration (D3): delete the legacy `credential.db*` files when the vault is created.
- Out: biometric unlock, manual lock and auto-lock, FLAG_SECURE (all in 02); edit and detail screens, search, favourites UI rework, clipboard (03); generator and strength meter (04); export and import (05); changing the master password.

## Acceptance criteria
- [ ] `./gradlew assembleDebug testDebugUnitTest` passes, invoked as `./gradlew` (not `sh gradlew`).
- [ ] Fresh install: the app opens on the Setup screen. No vault content and no bottom bar are visible.
- [ ] Setup rejects a password under 12 characters and a confirm field that doesn't match, with a visible error. Valid input leads to an empty Home screen.
- [ ] Add two credentials through the FAB. The sheet closes after each Save, and both rows appear. Text typed into the form doesn't disappear while typing.
- [ ] The Save button is disabled when website or password is empty.
- [ ] Each row shows a masked password. Its eye icon reveals that row's correct password and no other. The add-form password field is masked.
- [ ] No row icon except the delete icon removes anything. Delete removes only that row.
- [ ] Force-stop and relaunch shows the Unlock screen, and no credentials are visible behind it.
- [ ] A wrong password shows "Wrong master password" and the vault stays locked. The correct password shows the same two credentials, and their passwords reveal correctly.
- [ ] `adb shell run-as com.example.passwordmanager head -c 16 databases/vault.db` is not `SQLite format 3`, and a pulled copy of `vault.db` doesn't contain a saved password string (`strings | grep`). `databases/credential.db` doesn't exist.
- [ ] Upgrade path: install the baseline build (a328dad), add a credential, then install this build over it. Setup is shown and the old credential is gone.
- [ ] `adb logcat` during setup, unlock and add shows no master password, saved password, key or IV.
- [ ] `adb shell dumpsys package com.example.passwordmanager` doesn't list `ALLOW_BACKUP` in its flags.

## Design notes
- Affected files/layers:
  - New: `PasswordManagerApp.kt`, `AppContainer.kt` (package root); `data/crypto/KeyDerivation.kt` (a `fun interface` plus the Argon2id implementation and params), `data/crypto/VaultCrypto.kt`; `data/vault/VaultHeader.kt`, `VaultHeaderStore.kt`, `VaultSession.kt`; `data/CredentialRepository.kt`; `presentation/LockScreen/` (`SetupScreen.kt`, `UnlockScreen.kt`, `LockViewModel.kt`).
  - Changed: `MainActivity.kt` (no DB or VM construction; gated NavHost), `CredentialViewModel.kt`, `CredentialState.kt`, `CredentialEvent.kt`, `CredentialDao.kt`, `CredentialDatabase.kt`, `HomeScreen/CredentialScreen.kt`, `AddScreen/AddCredentialScreen.kt`, `AndroidManifest.xml`, the backup rule XMLs, `app/build.gradle.kts`, `build.gradle.kts`.
  - Deleted: `util/CryptoManager.kt`, `util/CryptographyManager.kt`.
- `VaultSession` owns the open `CredentialDatabase`. It takes a DB-opener function `(key) -> CredentialDatabase`, so tests can pass an in-memory plain Room DB. The repository exposes flows that emit an empty list while the vault is locked, for example `flatMapLatest` on the session state.
- Errors: use a sealed `VaultException` with `WrongPassword` and `CorruptHeader`. Map `AEADBadTagException` to `WrongPassword`. Don't catch-and-swallow.
- Data model / migration changes: new DB file `vault.db`, schema v1, `exportSchema = true`. The `Credential` columns stay plaintext Strings, because SQLCipher encrypts the whole file. Keep `isFav` as is for now (03 reworks it). Delete the legacy DB on setup.
- Security considerations:
  - Run the KDF on `Dispatchers.Default`.
  - Zero the password and key byte arrays after use.
  - Hold VK only in memory, inside `VaultSession`.
  - Pass SQLCipher a copy of the raw-key bytes (`x'<hex>'`), because the factory may clear the array it is given.
  - Call `System.loadLibrary("sqlcipher")` once in the Application.
  - Clear the master password text state after submit.
  - Low-RAM devices use the fallback Argon2 parameters.
- Remove the dead imports, vars and commented blocks you touch in `MainActivity`. Leave other unrelated dead code (rule: surgical changes).

## Critical logic needing unit tests
- `VaultCrypto`, using a fast PBKDF2 `KeyDerivation` in tests:
  - The setup→unlock round trip returns the same VK.
  - A wrong password throws `VaultException.WrongPassword`.
  - A tampered IV, salt or ciphertext fails.
  - Two setups produce different salts and IVs.
- `VaultHeader` survives a SharedPreferences round trip (Robolectric). A missing or garbled field gives `CorruptHeader`.
- `VaultSession` transitions:
  - No header → `NotSetUp`.
  - Setup → `Unlocked`.
  - A new session with an existing header → `Locked`.
  - A wrong unlock stays `Locked`.
  - A correct unlock → `Unlocked`.
- Master password validation (length, confirm match) and credential form validation (blank website or password).
- DAO and repository on in-memory Room (Robolectric):
  - Upsert and delete.
  - Ordering by website and by date.
  - The favourites query returns only favourites.
  - Flows emit empty while locked.

## Dependencies
- None. This is the foundation for 02–06.
