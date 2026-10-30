# TOTP authenticator

Status: IN_REVIEW
Backlog ID: 09

## Goal
An "Authenticator" tab that works like Google/Microsoft Authenticator: add 2FA accounts by scanning a QR code or typing the setup key, see live 6/8-digit codes with a countdown, tap to copy. Secrets live in the encrypted vault and are included in the encrypted backup.

## Scope
- In:
  - Pure-Kotlin core in `util/totp/` (D9; research § TOTP, § otpauth):
    - `Base32` decode/encode (RFC 4648, tolerant of case, spaces, hyphens, missing padding).
    - `Totp.generate(secret, unixSeconds, algorithm, digits, period)` + `secondsRemaining(...)`; HOTP truncation per RFC 4226. Algorithms SHA1/SHA256/SHA512; digits 6/8; period 1–300 s.
    - `OtpAuthUri.parse(text)` → typed result (`Totp` account | error `NotOtpAuth`, `UnsupportedType` (hotp), `MissingSecret`, `InvalidSecret`, `InvalidParameter`); `OtpAuthUri.build(account)` (used by 10).
  - Data: `TotpAccount` entity (`id`, `issuer`, `accountName`, `secret` normalised Base32, `algorithm`, `digits`, `period`, `createdAt`), `TotpDao`, `TotpRepository` (same `whileUnlocked`/`write` pattern as `CredentialRepository`). Add to `CredentialDatabase` version 2 with `@AutoMigration(from = 1, to = 2)` (D11); commit `app/schemas/.../2.json`. `insertSkippingDuplicates` (duplicate = same secret) for imports.
  - UI (07 tokens/components):
    - New bottom-nav tab "Authenticator" after Favorites (icon e.g. `Icons.Rounded.Shield`). FAB on this tab opens an "Add account" sheet: **Scan QR code**, **Scan from image**, **Enter setup key**.
    - List: `ItemRow`-style card per account: issuer (or account if no issuer), account name, code grouped (`123 456` / `1234 5678`), countdown (circular progress + seconds; `warning` colour in the last 5 s). Codes refresh at each period boundary. Search not needed.
    - Tap a card → copy current code via `SecureClipboard.copy()` (existing 30 s auto-clear) + `CopiedMessage`.
    - Overflow/long-press per account: Edit (issuer, account name only), Delete (`ConfirmDialog`, secure: "Removing this account can lock you out of <issuer> unless 2FA is disabled there first").
    - Manual entry form: issuer (optional), account name (required), setup key (required, Base32), "Advanced" collapsed: algorithm, digits, period (defaults SHA1/6/30). Inline validation errors.
    - After a successful scan: confirmation card (issuer, account, current code preview) → Save.
  - QR (D8): `com.journeyapps:zxing-android-embedded:4.3.0`. `SecureCaptureActivity : CaptureActivity` sets FLAG_SECURE, registered in the manifest; launched with `ScanContract` + `ScanOptions` (QR only, no beep, prompt text "Scan the QR code shown by the website"). "Scan from image": SAF `OpenDocument("image/*")` → zxing core `QRCodeReader` (research § QR). Launchers registered on `MainActivity` like the backup launchers; call `autoLockObserver.expectAppTrip()`/`endAppTrip()` around the image picker. Scanned text routing: `otpauth://totp` → confirm/save; `otpauth://hotp` → "Counter-based (HOTP) codes are not supported"; `otpauth-migration://` → "Importing from Google Authenticator comes in a later version" (replaced in 10); anything else → "This QR code is not an authenticator code".
  - Backup v2 (D12): `BackupCrypto` payload becomes `BackupContent(credentials, totp = emptyList())`, header version 2; import accepts v1 (array) and v2; `ImportResult` reports TOTP counts too; Backup UI message mentions authenticator accounts. Notes field is added by 11.
- Out:
  - HOTP, Steam/Yandex/mOTP variants, MD5, account icons/logos, linking TOTP to credentials, search/sort/reorder, NTP/time-skew correction, widgets/wear.
  - Google migration import and QR export (10).

## Acceptance criteria
- [ ] Authenticator tab appears in the bottom bar; empty state with "Add account" hint when empty.
- [ ] Manual entry: issuer `Example`, account `alice@example.com`, key `JBSWY3DPEHPK3PXP` → saved; the code equals `oathtool --totp -b JBSWY3DPEHPK3PXP` (or any reference generator) at the same time. Same with lowercase key containing spaces (`jbsw y3dp ehpk 3pxp`).
- [ ] Manual entry with SHA256, 8 digits, period 60 shows an 8-digit code with a 60 s countdown that matches `oathtool --totp=sha256 -d 8 -s 60 -b <key>`.
- [ ] Invalid key (`ABC1!`) or empty account name shows an inline error and does not save.
- [ ] Scan from image: `adb push` a PNG QR of `otpauth://totp/ACME%20Co:bob?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=ACME%20Co&digits=8` → confirmation shows "ACME Co / bob", save → listed with an 8-digit code.
- [ ] Scan QR code with the camera: first use asks for camera permission; denying shows a message and returns without crashing; allowing opens the scanner (emulator virtual scene camera; a successful scan here is a bonus, the image path is the required proof).
- [ ] Scanning/importing an `otpauth://hotp/...` or a non-otpauth QR shows the matching error message.
- [ ] Countdown ticks every second; code changes at the period boundary; last 5 s shown in warning colour.
- [ ] Tap a card → "Copied"; clipboard holds the code; cleared after 30 s and on lock.
- [ ] Edit issuer/account name persists; delete asks for confirmation, then removes the account.
- [ ] Lock and relaunch: accounts are still there after unlock; while locked nothing is visible. Screenshot attempts on the tab and scanner are blocked.
- [ ] Upgrade install over the 08 build: existing credentials preserved (auto-migration 1→2), Authenticator tab empty.
- [ ] Export backup → wipe app data → set up → import: credentials **and** authenticator accounts restored with the same codes. A v1 backup made by the 08 build still imports.

## Design notes
- Affected files/layers: `util/totp/{Base32,Totp,OtpAuthUri}.kt` (new), `data/totp/{TotpAccount,TotpDao,TotpRepository}.kt` (new), `data/CredentialDatabase.kt` (entity, version 2, auto-migration), `data/backup/{ExportFormat,BackupCrypto,BackupRepository}.kt`, `AppContainer.kt`, `presentation/Authenticator/` (screen, add sheet, form, `AuthenticatorViewModel`), `presentation/AppNavigation.kt` (route + nav item), `MainActivity.kt` (tab, FAB action per tab, scan/image launchers), `SecureCaptureActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, Settings backup copy.
- Code ticker: a VM `flow` that emits wall-clock seconds aligned to the next second while subscribed (`SharingStarted.WhileSubscribed`); codes computed on `Dispatchers.Default` from accounts × time. Keep `Totp` free of Android types; inject the clock.
- Store `algorithm` as an enum (`TypeConverter` or string column); validate digits/period ranges in one place (`TotpAccount` factory or parser) and reuse for manual entry.
- Scan result arrives after a possible lock (screen-off during scanning): drop it unless `VaultState.Unlocked`.
- Merged manifest: confirm `CAMERA` permission present and camera `uses-feature` `required="false"`; `SecureCaptureActivity` `screenOrientation="portrait"`, `exported="false"`.
- Data model / migration changes: new table `TotpAccount`; DB v1 → v2 via `@AutoMigration`; schema `2.json` committed. Backup format v2 (D12).
- Security considerations:
  - Secrets only in SQLCipher DB and the encrypted backup; never logged, never in `toString()` used in logs, never in intents other than the scanner's result string.
  - Codes are copied with the sensitive flag + auto-clear; secrets are never displayed in 09 (export display is 10, behind re-auth).
  - Scanner window FLAG_SECURE; all dialogs/sheets `SecureOn`.
  - Treat scanned/imported text as untrusted: length cap (e.g. 4 KB), strict parsing, secret 1–128 bytes after decoding.

## Critical logic needing unit tests
- `Totp`: all RFC 6238 App. B vectors (SHA1/256/512 × 6 timestamps, 8 digits) and RFC 4226 App. D HOTP vectors (counters 0–9, 6 digits); `JBSWY3DPEHPK3PXP` @ t=59 → `996554`; `secondsRemaining` at boundaries (0, 29, 30).
- `Base32`: RFC 4648 §10 vectors both ways; lowercase/spaces/no-padding accepted; invalid characters rejected.
- `OtpAuthUri.parse`: full URI, defaults, `issuer` param vs label prefix, `%3A` separator, `%20`/`+` in names, case-insensitive algorithm, missing secret, invalid digits/period, `hotp`, wrong scheme; `build` → `parse` round trip.
- `TotpDao`/`TotpRepository`: insert/update/delete round trip, duplicate skipping, locked → `VaultException.Locked` (Robolectric in-memory Room, like `CredentialDaoTest`/`CredentialRepositoryTest`).
- `BackupCrypto`: v2 round trip with credentials + TOTP; v1 payload still decrypts.
- No UI tests.

## Dependencies
- 07 (tokens, nav bar + per-tab FAB, `ItemRow`, `ConfirmDialog`), 05 (backup), 03 (`SecureClipboard`).
