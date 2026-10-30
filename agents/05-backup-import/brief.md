# Encrypted backup and import

Status: PLANNED
Backlog ID: 05

## Goal
Users can save a password-protected encrypted backup file and restore it on a fresh install. This is the only protection against losing the phone, since backups are disabled. Users can also import their existing passwords from a Google Password Manager, Chrome or Bitwarden CSV export.

## Scope
- In:
  - Export:
    - Started from Settings.
    - Asks for the master password again, verified by unwrapping the header.
    - Then asks for an export password, entered twice. It is a separate password, at least 12 characters.
    - Writes through SAF `ACTION_CREATE_DOCUMENT` (`.json`).
    - Format per `security.md` §6: `format, version, kdf{argon2id params, salt}, cipher, iv, data`, where `data` is the AES-256-GCM encryption of the JSON items array.
    - Uses a fresh salt and IV for every export. The AAD is the header JSON bytes.
    - Never writes temp or cache files.
  - Import backup:
    - SAF `ACTION_OPEN_DOCUMENT`.
    - Asks for the file password, then decrypts and validates.
    - Inserts all rows in one Room transaction.
    - Skips duplicates (same website and username, case-insensitive).
    - Reports the counts imported, skipped as duplicate and invalid.
  - CSV import:
    - Shows a warning dialog first ("plaintext file; delete it afterwards").
    - Parses with `commons-csv` (RFC 4180, UTF-8, strips the BOM).
    - Maps columns by header name:
      - GPM/Chrome `name,url,username,password,note`
      - Bitwarden `name,notes,favorite,login_uri,login_username,login_password` (only `type=login` rows)
    - Website comes from the URL, falling back to the name.
    - Rows with no password are invalid.
    - Same transaction and reporting as the backup import.
  - Rejects files over 5 MB. An unknown format or missing required headers gives a specific error message.
  - JSON uses `kotlinx.serialization` (D5).
- Out:
  - Plaintext CSV export.
  - Bitwarden encrypted JSON import.
  - KDBX.
  - Merging edits into existing entries (duplicates are only skipped).
  - Scheduled automatic backups.

## Acceptance criteria
- [ ] Settings → Export: a wrong master password is rejected. With the correct one plus an export password, the system file picker saves `passwords-export.json` to Downloads.
- [ ] The pulled file (`adb pull`) is JSON with `"format":"pm-export"`, and none of the saved passwords, websites or usernames appear in it.
- [ ] Clear the app data, set up a new master password, then Settings → Import backup with that file: a wrong file password shows an error and imports nothing. The correct one restores every credential with the right passwords, notes and favourites, and shows the count.
- [ ] Importing the same file again imports 0 and reports every row as a duplicate.
- [ ] `adb push` a GPM-format CSV whose rows include commas, quotes and a newline in a note, and one row with no password. Import CSV shows the warning, then imports the valid rows with exact values and reports 1 invalid.
- [ ] A Bitwarden-format CSV imports its login rows correctly, including favourites.
- [ ] Choosing a non-CSV file, or a file over 5 MB, shows an error, and nothing is imported.

## Design notes
- Affected files/layers:
  - New `data/backup/ExportFormat.kt` (serializable models), `data/backup/BackupCrypto.kt` (reuses `KeyDerivation` and the AES-GCM helpers from `VaultCrypto`, without duplicating them), `data/backup/CsvImporter.kt`, `data/backup/BackupRepository.kt`, or methods on `CredentialRepository` if that stays small.
  - `CredentialDao` gets a `@Transaction` bulk insert.
  - `presentation/SettingsScreen/` (export and import entries, password dialogs, result dialog).
  - `app/build.gradle.kts` (serialization plugin and runtime, `commons-csv`).
- Data model / migration changes: none.
- Security considerations:
  - Re-authenticate before export.
  - Zero the plaintext JSON and CSV byte buffers after use.
  - Treat every input file as untrusted: check the size first, then the schema.
  - Never log rows.
  - Dialogs use `SecureOn`.
- Run the KDF and the I/O on background dispatchers. Show progress while they run.

## Critical logic needing unit tests
- Export→import round trip gives equal items, including the note and the favourite flag.
- A wrong password throws a specific error.
- Modifying any header field (the AAD) or the ciphertext causes decryption to fail.
- Two exports of the same data produce different salts, IVs and ciphertext.
- Malformed JSON, an unknown `format` and an unsupported `version` each produce a specific error.
- CSV parsing:
  - quoted commas, quotes and newlines
  - BOM
  - GPM, Chrome and Bitwarden header mapping
  - non-login Bitwarden rows skipped
  - missing password counted as invalid
  - missing required headers give a specific error
- Duplicate detection (case-insensitive website and username) and the counts reported.
- The size-limit check.

## Dependencies
- 01 (crypto, `KeyDerivation`, repository). 02 (Settings screen).

## Carried over from 04 review/verify
- [ ] Home bottom bar must not sit above the keyboard while searching (same treatment as Edit screen)
- [ ] Failed Setup should keep the entered fields (only clear on success) — minor
