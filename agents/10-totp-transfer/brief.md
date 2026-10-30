# TOTP import and export

Status: PLANNED
Backlog ID: 10

## Goal
Users can move their 2FA accounts in from Google Authenticator ("Transfer accounts" QR codes, possibly several) or any `otpauth://` QR, and out to Google or Microsoft Authenticator by showing one standard QR code per account after re-authenticating.

## Scope
- In:
  - Import:
    - `util/totp/MigrationPayload.kt`: decode `otpauth-migration://offline?data=…` (percent-decode → Base64, tolerant of URL-safe/no padding → hand-written protobuf reader, D10; schema in research § Google migration). Output: accounts (secret bytes → Base32, name/issuer split when issuer is blank and name is `Issuer:account`, SHA1/6/30 defaults for UNSPECIFIED), skipped count (HOTP, MD5, unspecified type), `batchIndex`, `batchSize`, `batchId`. Typed errors: `NotMigrationUri`, `MalformedPayload`.
    - Entry points: the existing scanner and "Scan from image" (09) route `otpauth-migration://` here; add sheet entry "Import from Google Authenticator" with a short how-to ("In Google Authenticator: ⋮ → Transfer accounts → Export accounts, then scan the code").
    - Preview: list of accounts in the QR (issuer + account, no secrets) → Import → `TotpRepository.insertSkippingDuplicates` → result "Imported 3, 1 already in Vaultly, 1 not supported (counter-based)". If `batchSize > 1`: "Code k of n imported" + button "Scan next code" (each QR imported independently).
    - Standard `otpauth://totp` QR import is already covered by 09 (no change).
  - Export:
    - Authenticator tab overflow → "Export accounts" → explanation dialog ("Anyone who sees these codes can copy your 2FA accounts") → master password re-auth via `VaultSession.verifyMasterPassword()` (reuse the backup flow's dialog/validation pattern) → export screen.
    - Export screen: pager/next-prev through accounts, one QR per account from `OtpAuthUri.build()` (zxing `BarcodeEncoder`, ≥ 240 dp, dark modules on white for scannability in dark mode), issuer + account + "k of n", "Show setup key" toggle revealing the grouped Base32 key, warning chip on non-default params (SHA256/512, 8 digits, period ≠ 30): "May not work in Google or Microsoft Authenticator".
    - Leaving the screen or locking discards the rendered bitmaps/state; re-entry needs re-auth again.
- Out:
  - Import from Microsoft Authenticator (it has no export for third-party accounts); exporting in Google's migration format (multi-account QR); Aegis/2FAS/andOTP file formats; scanning several QRs in one camera session; HOTP.

## Acceptance criteria
- [ ] Scan from image of a QR for the research test vector `otpauth-migration://offline?data=Ci4KCkhl…AowMQH` (full string in `research/v2-features.md`) → preview lists "Example / alice@example.com" and "ACME Co / bob" → Import → both appear; codes match `oathtool --totp -b JBSWY3DPEHPK3PXP` and `oathtool --totp=sha256 -d 8 -b GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`.
- [ ] Importing the same QR again → "0 imported, 2 already in Vaultly".
- [ ] A multi-batch export (generate a 2-batch test payload, or a real Google Authenticator export with > 10 accounts on a second device) shows "Code 1 of 2 imported" and "Scan next code"; scanning code 2 imports the rest.
- [ ] Corrupted `data` (truncated) → "This transfer code couldn't be read" — no crash, nothing imported.
- [ ] (If a second device/emulator with Google Authenticator is available) real "Transfer accounts" QR scanned with the camera imports correctly. Otherwise mark as not run.
- [ ] Export: wrong master password → error, no QR shown; correct → QR for account 1 of N; next/prev works; "Show setup key" shows the Base32 key.
- [ ] The exported QR, decoded by an external decoder (e.g. `zbarimg` on a photo/screen capture taken with a second device), is `otpauth://totp/...` with the right issuer, account, secret and params; re-importing it into Vaultly (fresh install) yields identical codes.
- [ ] Screenshot attempt on the export screen is blocked; locking (power button) while on it returns to Unlock, and after unlock the export screen is not restored.
- [ ] Entry with SHA512/8 digits shows the compatibility warning on export.

## Design notes
- Affected files/layers: `util/totp/MigrationPayload.kt` (+ tiny `ProtobufReader` in the same package), `presentation/Authenticator/` (import preview, export screen, re-auth dialog, VM events), `presentation/AppNavigation.kt` (export route), `MainActivity.kt` (scan routing for migration, "scan next").
- Reader: `ProtobufReader(bytes)` with `readTag()`, `readVarint()`, `readBytes()`, `skip(wireType)`; bounds-check every length; reject varints > 10 bytes. Keep it internal to `util/totp/`.
- Export QR bitmaps are created in the VM only for the visible page (no cache of all secrets), on `Dispatchers.Default`.
- Data model / migration changes: none (uses 09's table and dedupe).
- Security considerations:
  - Export is the only place secrets are shown: re-auth every time, FLAG_SECURE, nothing persisted, state cleared on lock and on leaving the screen.
  - Migration payload is untrusted: input length cap, strict bounds, skip unknown fields, cap accounts per payload (e.g. 100), validate each secret like manual entry.
  - Do not log decoded payloads or URIs.

## Critical logic needing unit tests
- `MigrationPayload`: research test vector → 2 accounts with exact issuer/name/Base32 secret/algorithm/digits; batch fields read; percent-encoded `+ / =` data; missing padding; HOTP/MD5 entries skipped and counted; `Issuer:account` name split; unknown fields skipped; truncated/garbage input → `MalformedPayload`; wrong scheme → `NotMigrationUri`.
- `OtpAuthUri.build` for export: spaces as `%20`, `:` in names encoded, blank issuer omitted; build → parse round trip for each algorithm/digits/period combination (extends 09 tests).
- Re-auth gate in the export VM: wrong password never exposes accounts (VM unit test with fakes, like `LockViewModelTest`).
- No UI tests.

## Dependencies
- 09 (TOTP core, table, scanner, image decode, `OtpAuthUri.build`).
