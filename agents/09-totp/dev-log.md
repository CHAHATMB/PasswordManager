# Dev Log – TOTP authenticator

## Round 1
- Base commit: 0bf273d (branch `scratch/STRY-09-totp`)
- Commits (review range `0bf273d..HEAD`):
  - 97d94f3 feat: add Base32 and RFC 6238 TOTP code generation
  - 0b10169 test: cover Base32 and TOTP/HOTP against the RFC vectors
  - dd7faff feat: parse and build otpauth TOTP URIs
  - ad6eea1 test: cover otpauth parsing, rejections and build round trip
  - a32b921 refactor: share the locked-vault read and write helpers between repositories
  - 0d7e4b7 feat: store TOTP accounts in the vault DB with a 1 to 2 auto-migration
  - e5bf18d test: cover TOTP persistence, duplicate skipping and the v1 to v2 migration
  - cb5e586 feat: include authenticator accounts in backup format v2
  - 7a7735e test: cover backup v2 round trip with TOTP and v1 compatibility
  - 704eb04 feat: add a secure zxing QR scanner and a QR image reader
  - 1cfe7d6 feat: let AppTextField show an inline error
  - 353373d feat: add the Authenticator tab with QR, image and manual setup
  - plus the `docs:` commit for this log
- Summary of changes:
  - `util/totp/`: `Base32` (encode with padding; decode ignores case/spaces/hyphens/padding, null on other chars), `Totp` (`hotp`, `generate`, `secondsRemaining`), `TotpAlgorithm` (SHA1/256/512, `fromName` case-insensitive), constants for defaults and ranges (digits 6/8, period 1–300, secret 1–128 bytes).
  - `util/totp/OtpAuthUri.kt`: `OtpAuthAccount.create(...)` is the one validation point (scan, manual entry, backup import); it normalises the secret to unpadded uppercase Base32. `OtpAuthUri.parse` → `OtpAuthResult` (`Totp`, `NotOtpAuth`, `UnsupportedType`, `MissingSecret`, `InvalidSecret`, `InvalidParameter`); 4 KB cap; `build` for 10.
  - `data/VaultAccess.kt`: `VaultSession.whileUnlocked` / `VaultSession.write`, moved out of `CredentialRepository` unchanged so both repositories share the lock handling. `CredentialRepository.write` (used by its test) still exists and delegates.
  - `data/totp/`: `TotpAccount` entity (algorithm stored as enum name, `toString` omits the secret, `codeAt`, `secondsRemaining`, `title`), `TotpDao` (upsert/delete/list oldest first/`insertSkippingDuplicates` by secret), `TotpRepository`.
  - `CredentialDatabase` v2 with `@AutoMigration(from = 1, to = 2)`; `app/schemas/.../2.json` committed.
  - Backup v2: `BackupContent(credentials, totp)`, header version 2; `parse` accepts 1 and 2, `decrypt` reads a bare array for v1. TOTP entries are re-validated through `OtpAuthAccount.create`; invalid ones are counted. `ImportResult` has `totpImported/totpDuplicates/totpInvalid`. Backup dialogs mention authenticator accounts and show the TOTP counts when the backup had any.
  - QR: `zxing-android-embedded:4.3.0`; `SecureCaptureActivity` (FLAG_SECURE, no recents screenshot, portrait, not exported). The library's own `CaptureActivity` is removed from the merged manifest (`tools:node="remove"`). `util/QrImageReader` decodes a SAF image (subsampled to ≤ 2048 px) with zxing `QRCodeReader`.
  - UI (`presentation/Authenticator/`): tab "Authenticator" (Shield icon) after Favorites; FAB opens a sheet with Scan QR code / Scan from image / Enter setup key. Rows use `ItemRow`: title = issuer or account, subtitle = account, trailing = grouped code, circular countdown (warning colour ≤ 5 s), overflow menu Edit/Delete. Tap copies via `SecureClipboard` + `showCopiedMessage`. Manual entry sheet with inline errors after first Save and a collapsible Advanced section. Scan result → confirmation dialog with live code → Save. All sheets/dialogs are `SecureOn`.
  - `AuthenticatorViewModel`: ticker flow aligned to the wall-clock second, `combine` with the accounts on `Dispatchers.Default`, `WhileSubscribed(5000)`. Dialog state is Compose state in the VM (survives rotation, not in the Bundle), cleared on lock. Scan and image results are dropped unless the vault is unlocked.
- Decisions / deviations from brief:
  - `OtpAuthAccount` (util, no Room) is the parser's result; `TotpAccount` (data) is built from it with `toTotpAccount(createdAt)`. Keeps `util/totp` free of the data layer.
  - Base32 decode drops trailing partial bits instead of rejecting lengths like 1/3/6 mod 8, as authenticator apps do. Codes are unaffected.
  - Unknown otpauth types other than `hotp` (e.g. `steam`) → `InvalidParameter`; the UI shows "invalid setup key or settings". A valid otpauth URI with a bad secret/digits/period shows the same message.
  - Edit requires an issuer or an account name (not both), since scanned accounts may have an empty account name. Manual entry requires the account name, per brief.
  - Manual add and scan save do not skip duplicates (only imports do, per brief).
  - Camera permission: the library's own "missing permission" dialog is turned off; the scanner returns at once and the tab shows "Scanning needs access to the camera…".
  - No top-bar actions on the Authenticator tab (Settings/Lock stay on Passwords).
  - Extra test: `CredentialDatabaseMigrationTest` builds a v1 DB from `1.json` and opens it with Room v2 (not in the brief's list; covers the upgrade-install criterion).
- Unit tests added: `Base32Test`, `TotpTest` (RFC 4226 D, RFC 6238 B ×3 algorithms, `JBSWY3DPEHPK3PXP`@59 = 996554, boundaries), `OtpAuthUriTest`, `TotpDaoTest`, `TotpRepositoryTest`, `CredentialDatabaseMigrationTest`; `BackupCryptoTest` / `BackupRepositoryTest` extended (v2 round trip with TOTP and equal codes, v1 file still decrypts, v2 relabelled as v1 fails, invalid TOTP entries rejected).
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (177 tests, 0 failures), no Kotlin warnings.
- Notes for reviewer/verifier:
  - QR test images: e.g. `pip install qrcode pillow`, then `python3 -c "import qrcode; qrcode.make('otpauth://totp/ACME%20Co:bob?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=ACME%20Co&digits=8').save('acme.png')"`; same for an `otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1` and a plain `https://example.com` QR. `adb push acme.png /sdcard/Download/`, then FAB → Scan from image → pick it.
  - Camera scan on the emulator: AVD back camera = VirtualScene. Extended controls → Camera lets you set a wall/table image to the QR PNG; then FAB → Scan QR code and move the virtual camera (Alt + WASD/mouse) to the image. First use shows the system permission prompt; "Don't allow" returns to the tab with the camera message.
  - Reference codes: `oathtool --totp -b JBSWY3DPEHPK3PXP`, `oathtool --totp=sha256 -d 8 -s 60 -b <key>`.
  - Upgrade: install this APK over the 08 build without uninstalling; credentials must stay, Authenticator tab empty.
  - A v1 backup from the 08 build must still import (`BACKUP_VERSION_CREDENTIALS_ONLY`).
  - `assembleRelease` (R8 on) was not run, so R8 handling of zxing is unchecked.

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
