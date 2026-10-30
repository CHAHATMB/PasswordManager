# Security research: local-only Android password manager

Sources: OWASP MASVS v2 / MASTG, OWASP Password Storage Cheat Sheet, Bitwarden Security Whitepaper (KDF, export formats), KeePass/KeePassDX docs, Android developer docs (Keystore, BiometricPrompt, Auto Backup, ClipboardManager).
Project constraints: minSdk 27, targetSdk 34, Room 2.6.1, androidx.biometric 1.1.0.

## 1. Recommended architecture (summary)

```
master password (CharArray) --Argon2id(salt)--> KEK (32 B)
                                                  |
               random Vault Key VK (32 B) <--AES-256-GCM unwrap-- wrappedVK_pw   (vault header)
                         |                 <--AES-256-GCM unwrap-- wrappedVK_bio (Keystore key, biometric-bound)
                         v
        encrypts vault data (SQLCipher DB key, or per-field AES-GCM)
```

- VK never changes when the master password changes: only re-wrap VK.
- Verifying the master password = successful GCM unwrap of `wrappedVK_pw` (tag check). No password hash stored.
- VK lives in memory only while unlocked; locking drops it and closes the DB.

## 2. Master password and key derivation

**Algorithm:** Argon2id (MASVS-CRYPTO-1, OWASP first choice). PBKDF2 only as fallback.

| Option | Parameters | Notes |
|---|---|---|
| Argon2id (recommended) | m = 64 MiB (65536 KiB), t = 3, p = 4, salt 16 B, output 32 B | Bitwarden's Argon2id defaults. Takes about 0.3 to 1 s on mid-range phones. |
| Argon2id (minimum) | m = 19 MiB (19456 KiB), t = 2, p = 1 | OWASP floor. Use it when `ActivityManager.isLowRamDevice()` is true. |
| PBKDF2-HMAC-SHA256 | 600,000 iterations, salt 16 B | OWASP/Bitwarden value. Built in as `SecretKeyFactory "PBKDF2WithHmacSHA256"` (API 26+). It is weak against GPUs, so use it only if a native library is not acceptable. |

**Libraries:**
- `com.lambdapioneer.argon2kt:argon2kt:1.6.0`: the recommended choice. It is a native binding (MIT licence), takes a `ByteArray` or `ByteBuffer` password, and returns raw hash bytes.
- `org.bouncycastle:bcprov-jdk18on` `Argon2BytesGenerator`: pure Java and several times slower on ART. Avoid it at 64 MiB.
- `org.signal:argon2`: an alternative native binding.
- Do not use `androidx.security:security-crypto` (EncryptedSharedPreferences/EncryptedFile). It is deprecated.

**Rules:**
- Store the KDF type and parameters in a versioned vault header (`version, kdf, m, t, p, salt, iv, wrappedVK`). This lets you raise the parameters later: on a successful unlock, re-derive with the new parameters and re-wrap VK.
- Run the KDF on `Dispatchers.Default` and show progress. Never run it on the main thread.
- Keep the password as a `CharArray`, encode it to UTF-8 bytes, and zero both arrays after deriving. Compose `TextField` forces a `String`, so keep its scope short and clear the state after unlock.
- Do not compare the password with anything. Treat `AEADBadTagException` from the unwrap as "wrong password".
- Brute-force UX: add increasing delays after failed attempts. The KDF cost is the real protection, so do not wipe the vault after N tries unless the user opts in.
- Password strength on setup: require at least 8 to 12 characters. Optionally use zxcvbn (`com.nulabinc.zxcvbn:zxcvbn`) and warn on a low score. The master password cannot be recovered, so tell the user clearly.

## 3. Vault encryption

- **Cipher:** `AES/GCM/NoPadding`, 256-bit key, 128-bit tag, **12-byte IV that is fresh and random for every encryption**. Never reuse an (key, IV) pair. With random IVs, keep each key under about 2^32 encryptions (no practical problem here).
- **AAD:** bind each ciphertext to its context, for example `"v1|credential|<id>|password"`. This stops an attacker swapping fields or records.
- **Storage format:** `version (1 B) | iv (12 B) | ciphertext+tag`, stored as a Room BLOB. Do not store Base64 `String`s with `Base64.DEFAULT`, which adds line breaks.
- **Data-layer choice** (decision for the planner):
  - **A. SQLCipher** (`net.zetetic:sqlcipher-android` + `androidx.sqlite`), keyed with a 32-byte key derived from VK via HKDF. It encrypts the whole DB, including website and username metadata and the indices, and needs few code changes. Cost: a native dependency and about 4 MB of APK size.
  - **B. Field-level AES-GCM** in the repository layer (JCA only, no new dependency). You must encrypt every sensitive column (website, username, password, note). Search and sorting then happen in memory after decryption.
  - The recommendation is **A** for full metadata protection. **B** is fine if dependency size matters. In either case, crypto belongs in a `data/crypto` layer, not in the UI or ViewModel.
- **Role of the Android Keystore:** Keystore keys cannot be exported and can be TEE/StrongBox-backed, but they cannot be derived from a password. Use the Keystore for:
  1. the biometric wrapping key (section 4);
  2. optionally, a non-auth "device key" (`setUserAuthenticationRequired(false)`, `setIsStrongBoxBacked(true)` with a fallback on `StrongBoxUnavailableException`) that additionally encrypts the vault header. A copied DB or header file then cannot be brute-forced offline. Trade-off: the vault is tied to the device, so migration needs the encrypted export.
- Do not put VK itself directly in the Keystore with no password involvement. That reduces vault security to the device lock screen.

## 4. Biometric unlock (MASVS-AUTH-2)

The prompt must be **CryptoObject-bound**. A prompt without crypto is only a UI gate and can be bypassed with Frida or on a rooted device.

- **Key:** AES-256-GCM in the AndroidKeyStore, alias for example `vault_biometric_key`:
  - `setUserAuthenticationRequired(true)`
  - API 30+: `setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)`. API 27 to 29: `setUserAuthenticationValidityDurationSeconds(-1)`, which means every use needs authentication.
  - `setInvalidatedByBiometricEnrollment(true)` (this is the default, but set it explicitly)
  - `setIsStrongBoxBacked(true)` with a fallback.
- **Enable flow:** this needs a session that is already unlocked with the master password:
  1. Initialise the ENCRYPT cipher.
  2. Call `BiometricPrompt.authenticate(promptInfo, CryptoObject(cipher))`.
  3. In `onAuthenticationSucceeded`, take `result.cryptoObject.cipher.doFinal(VK)`.
  4. Store `iv + wrappedVK_bio`.
- **Unlock flow:** initialise the DECRYPT cipher with the stored IV, show the prompt with the CryptoObject, then unwrap VK.
- **PromptInfo:** `setAllowedAuthenticators(BIOMETRIC_STRONG)` (Class 3 only; Class 2 cannot be used with crypto). Use the negative button "Use master password". Do not use `DEVICE_CREDENTIAL` for vault unlock, because the device PIN must not replace the master password.
- **Handle:**
  - `KeyPermanentlyInvalidatedException` (new fingerprint enrolled, or lock screen removed): delete the key and the biometric blob, disable biometrics, and require the master password.
  - `ERROR_LOCKOUT` / `ERROR_LOCKOUT_PERMANENT`: fall back to the master password.
  - `ERROR_NEGATIVE_BUTTON` / `ERROR_USER_CANCELED`: show the password field.
- Check availability with `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`.
- Recommended policy: also require the master password after a reboot, or after N days (for example 7). Keep an in-memory or `elapsedRealtime` marker for this.
- Upgrade `androidx.biometric` from 1.1.0 to the current stable 1.2.x/1.4.x line. It has many OEM bug fixes.

## 5. Runtime hardening

**Auto-lock (MASVS-AUTH):**
- Use `ProcessLifecycleOwner` (`androidx.lifecycle:lifecycle-process`). On `ON_STOP`, record `SystemClock.elapsedRealtime()`. On `ON_START`, lock if elapsed time is at least the timeout.
- Timeout options: immediately, 30 s, 1 min (default), 5 min, 15 min.
- Also lock on `Intent.ACTION_SCREEN_OFF` (dynamic receiver) and when the app process dies (this happens naturally because VK is only in memory).
- Lock means: null VK (zero the array), close SQLCipher, clear the decrypted state in ViewModels or repository caches, and navigate to the lock screen while clearing the back stack.
- Single source of truth: a `VaultSession` / `LockManager` holding `StateFlow<Locked|Unlocked>`.

**FLAG_SECURE (MASVS-PLATFORM-3):**
- Call `window.setFlags(FLAG_SECURE, FLAG_SECURE)` in `MainActivity.onCreate` before `setContent`. This blocks screenshots, screen recording and the recents thumbnail.
- **Gotcha:** Compose `Dialog`, `ModalBottomSheet` and `Popup` are separate windows. Pass `DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)` / `ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn)`. The app already has a bottom sheet.
- API 33+: also call `setRecentsScreenshotEnabled(false)`.

**Clipboard:**
- Mark the clip as sensitive. Use `ClipData.newPlainText(...)`, then `clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }` on API 33+. On API 27 to 32, use the literal key `"android.content.extra.IS_SENSITIVE"`, which some keyboards and OEMs honour.
- Auto-clear after 30 to 60 s (configurable). Use `clipboardManager.clearPrimaryClip()` (API 28+). On API 27, overwrite with an empty clip.
  - Schedule it with a `WorkManager` one-time request or a process-scoped coroutine. Don't use a ViewModel scope, because the ViewModel dies with the screen.
  - Since Android 10, a background app cannot read the clipboard, so it cannot check whether the clip is still its own. Clear unconditionally, or compare only while in the foreground.
- On API 33+, do not show your own "copied" toast, because the system shows one.

**Logs (MASVS-STORAGE-2):**
- Never log secrets, IVs, keys or plaintext. That includes `println`, which goes to logcat.
- Strip `Log.*` from release builds with R8: set `isMinifyEnabled = true` and add `-assumenosideeffects class android.util.Log { *; }` to `proguard-rules.pro`. Or use Timber with no tree planted in release.

**Backups (MASVS-STORAGE-1):**
- Set `android:allowBackup="false"`.
- Also write real rules in `data_extraction_rules.xml` (API 31+): exclude `database`, `sharedpref`, `file` and `root` under both `<cloud-backup>` and `<device-transfer>`. Do the same in `backup_rules.xml` for API 30 and below.
- Keystore keys are never backed up, so restored Keystore-wrapped blobs are useless anyway.
- The supported migration path is the encrypted export.

**Memory:**
- Use `ByteArray`/`CharArray` for keys and passwords and call `fill(0)` after use. JVM/ART gives no hard guarantee, so the aim is to keep the window short.
- Do not cache decrypted credentials in long-lived singletons. Decrypt on demand for the detail screen.
- Do not keep a `Credential.password: String` around after lock.

**Input fields:**
- Use `KeyboardType.Password` / `PasswordVisualTransformation` for secrets. This turns off IME learning on Gboard and most keyboards.
- Hide passwords by default, with a reveal toggle.

**Release build:** enable R8 minification. Optional MASVS-RESILIENCE checks (Play Integrity, root detection) are low value for a local-only app, so treat them as nice-to-have.

## 6. Encrypted export/import

**Own encrypted format (recommended default)**, modelled on Bitwarden's "password-protected" JSON export:
```json
{ "format": "pm-export", "version": 1,
  "kdf": { "type": "argon2id", "memoryKiB": 65536, "iterations": 3, "parallelism": 4, "salt": "<b64>" },
  "cipher": "AES-256-GCM", "iv": "<b64>", "data": "<b64 ciphertext+tag of the JSON items array>" }
```
- Use a separate export password (default it to the master password if you like), with a fresh salt and IV per export. The AAD is the header JSON bytes, so the parameters cannot be tampered with.
- Write through the Storage Access Framework (`ACTION_CREATE_DOCUMENT`). Do not write temp or cache files, and do not use external storage paths.
- Require the master password (re-authentication) before any export.

**Bitwarden JSON (unencrypted) import:**
- Structure: `items[]` with `type=1` (login), `name`, `notes`, `favorite`, and `login.{username, password, totp, uris[].uri}`.
- Encrypted Bitwarden exports use `"encrypted": true`. The account-key variant cannot be imported elsewhere. The password-protected variant uses PBKDF2 or Argon2 plus AES-CBC-256 with HMAC-SHA256 (Bitwarden "EncString" type 2: `2.iv|ct|mac`). Supporting that variant is optional and complex, so defer it.

**CSV pitfalls:**
- CSV is plaintext. Warn the user, ask for explicit confirmation, and suggest deleting the file afterwards.
- **Formula/CSV injection:** prefix any cell that starts with `= + - @ \t \r` with `'` when exporting. When importing, never evaluate anything.
- Follow RFC 4180 quoting. Notes and passwords contain commas, quotes and newlines, so do not split on `,`. Use a real parser such as `com.opencsv:opencsv` or `org.apache.commons:commons-csv`.
- Encoding: UTF-8, and handle a possible BOM.
- Columns differ between vendors. Bitwarden uses `folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp`. Chrome uses `name,url,username,password,note`. Map columns by header name, not by position.

**Import hardening:**
- Treat every import file as untrusted. Enforce a size limit (for example 10 MB) and validate the schema.
- Report rows that were skipped and duplicate rows (same site and username).
- Insert all rows inside one Room transaction.
- Zero the plaintext buffers afterwards.
- Gson 2.1 (2011) is ancient. Upgrade it, or switch to `kotlinx.serialization`.

## 7. Weaknesses in the current code

**`util/CryptoManager.kt` (should be deleted or replaced):**
- The key comes from `KeyGenerator.getInstance("AES")` in memory and is regenerated with every instance. Nothing is persisted (the Keystore code is commented out), so the data cannot be decrypted after a restart.
- It uses `AES/CBC/PKCS7` with no MAC, so the encryption is unauthenticated: it is malleable and exposed to padding-oracle attacks.
- `encryptCipher` is a `get()` property, so each access creates a new Cipher with a new IV. `encryptString*` returns the IV of a *different* cipher from the one that encrypted, and decryption fails or produces garbage.
- `encrypt()` writes lengths with `OutputStream.write(Int)`, which writes 1 byte, so anything over 255 bytes is truncated. `decrypt()` ignores short reads.
- `decryptString`/`decryptString3` return `ByteArray.toString()` (`"[B@..."`), not the plaintext.
- `encryptString5` ignores its input and encrypts the hard-coded `"chahatbag"`. `encryptString10`/`decryptString10` contain hard-coded key literals and run CBC with no IV.
- It `println`s IVs and plaintext. There are unused imports and fields (`keyStore`, `SecureRandom`).

**`util/CryptographyManager.kt`:**
- **Bug:** `getInitializedCipherForEncryption` declares a second `val cipher` inside `try`. The outer cipher that gets returned is **never initialised**, so `doFinal` throws `IllegalStateException`. A `RuntimeException` is also swallowed with `println`.
- `encryptData` decrypts with the hard-coded alias `"chahat"` and prints the plaintext. On failure it returns a dummy all-zero `CiphertextWrapper`, which means silent data loss.
- `decryptFromWraper` is debug code that logs plaintext.
- The key does `setUserAuthenticationRequired(true)` but has no explicit `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` or `setInvalidatedByBiometricEnrollment`. There is no handling of `KeyPermanentlyInvalidatedException` and no StrongBox.
- The decrypt path calls `getOrCreateSecretKey`, so a missing key gets silently regenerated instead of reported as an error.
- The SharedPreferences persistence uses Gson 2.1. Byte arrays are stored as JSON int arrays (which works, but is bloated). Errors are not typed.

**`util/BiometricPromptsUtils.kt`:**
- The prompt has no CryptoObject support in its API, and `setAllowedAuthenticators(BIOMETRIC_STRONG)` is not set.
- `onAuthenticationError` only logs, so the caller never gets a fallback path (lockout or cancel).
- The negative button text is the placeholder `"Navigation button texts"`, and the description has a typo.

**`AndroidManifest.xml`, data and build:**
- `allowBackup="true"` with empty, template-only backup and extraction rules means the Room DB is included in cloud backup and device transfer.
- `Credential.password` (and the website, username and note) are stored **in plaintext** in Room. The encrypted BLOB columns are commented out.
- There is no `FLAG_SECURE` anywhere, and no auto-lock or lock screen.
- `isMinifyEnabled = false` in release, so `Log.d`/`println` ship.
- Dependencies are outdated: `androidx.biometric:1.1.0`, `gson:2.1`.
- `MainActivity` imports `CryptographyManager`, but crypto is not wired into persistence.
