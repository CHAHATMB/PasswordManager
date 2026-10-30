# Architecture Decisions

Decided by the planner from `features.md`, `security.md`, `codebase.md`. Developers follow these; deviations go in the feature's `dev-log.md` with a reason.

## D1. Vault encryption: SQLCipher for the whole DB
- Use `net.zetetic:sqlcipher-android` (current 4.x) + `androidx.sqlite`, plugged into Room with `openHelperFactory(SupportOpenHelperFactory(key))`.
- Why: simplest *sound* option. Entity, DAO, SQL sort/search stay as they are (plain `String` columns); website/username/note metadata and indices are encrypted too. Per-field AES-GCM would need BLOB columns, AAD per field, encrypt/decrypt in the repository, and in-memory search/sort — more code, more places to get crypto wrong.
- Cost accepted: native dependency, ~4 MB APK.
- Key: the random 32-byte Vault Key (VK) is the DB key, passed in SQLCipher raw-key form (`x'<64 hex>'`) so SQLCipher does not run a second KDF. No HKDF: VK has no other use (export uses its own password-derived key, biometrics only wraps VK).
- DB is opened on unlock and closed on lock; VK lives only in memory while unlocked.
- Vault header (not secret, but integrity-protected by GCM): `version, kdf params, salt, iv, wrappedVK_pw` (+ biometric blob later), stored in a private `SharedPreferences` file as Base64 `NO_WRAP`. Verifying the master password = successful AES-256-GCM unwrap of `wrappedVK_pw`; `AEADBadTagException` → wrong password. No password hash stored.
- The Keystore "device key" layer from security.md §3 is **not** used (ties the vault to the device; not needed for a sound v1).

## D2. KDF: Argon2id via `com.lambdapioneer.argon2kt:argon2kt:1.6.0`
- Parameters: m = 64 MiB (65536 KiB), t = 3, p = 4, salt 16 B random, output 32 B.
- Low-RAM fallback (`ActivityManager.isLowRamDevice()`): m = 19 MiB (19456 KiB), t = 2, p = 1 (OWASP floor).
- Params are stored in the vault header so they can be raised later; the export format stores its own.
- Run on `Dispatchers.Default`; show progress. Password handled as `CharArray`/`ByteArray`, zeroed after use.
- Testability: argon2kt is native and won't load in JVM unit tests, so the KDF sits behind a one-method `fun interface` injected into the crypto code. Unit tests use a fast JCA PBKDF2 implementation; Argon2 itself is exercised on the emulator by the verifier.
- Rejected: Bouncy Castle Argon2 (several times slower on ART at 64 MiB); PBKDF2 as primary (weak vs GPUs).

## D3. Existing plaintext data: destructive migration
- The app is pre-release, so **existing plaintext credentials are discarded, not migrated**. This is an explicit decision.
- New SQLCipher DB file `vault.db`, Room schema version 1, `exportSchema = true` (schemas in `app/schemas/`). The legacy plaintext `credential.db` (+ `-wal`/`-shm`) is deleted when the vault is created.
- From the first real release onward, schema changes need proper Room migrations (no `fallbackToDestructiveMigration`).

## D4. No DI framework: manual construction
- Add a `PasswordManagerApp : Application` that owns one `AppContainer` (vault session, repository, settings, application `CoroutineScope`).
- ViewModels are built with `viewModelFactory { initializer { ... } }` reading the container — replaces the unchecked-cast factory in `MainActivity`.
- Why: one module, a handful of singletons; Hilt adds a plugin, annotation processing and a version-compat burden for no testability gain. Classes take their collaborators as constructor params, so tests pass fakes directly.

## D5. Smaller choices
- Settings (auto-lock timeout, biometric enabled) and the vault header: plain `SharedPreferences` (private, excluded from backup). No DataStore — nothing here needs it.
- JSON (export format): `kotlinx.serialization`. Remove Gson 2.1 (only used by dead code).
- CSV import: `org.apache.commons:commons-csv` (RFC 4180, header mapping). No hand-rolled splitting.
- Build: align KSP with Kotlin 1.9.0 (`1.9.0-1.0.13`), drop the duplicate `annotationProcessor` Room compiler, set `gradlew` exec bit. Upgrade Kotlin only if a new dependency forces it (then bump Compose compiler to the matching version).
- Unit-test deps: `kotlinx-coroutines-test`, Robolectric + `androidx.test:core` (for in-memory Room DAO tests with the plain Room factory; SQLCipher native lib is not used in JVM tests).
- Backups: `allowBackup="false"` + exclusion rules in `data_extraction_rules.xml` / `backup_rules.xml`. The encrypted export (feature 05) is the only supported migration path.
- Biometric unlock is CryptoObject-bound (`BIOMETRIC_STRONG` only); device PIN never replaces the master password.
