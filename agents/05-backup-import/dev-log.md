# Dev Log – Encrypted backup and import

## Round 1
- Base commit: 7fe82119a76f3d08c34fee747be86bf7dc468481
- Commits (review range `7fe8211..HEAD`):
  - dd2801e chore: add kotlinx.serialization and commons-csv
  - cc95ccf refactor: share the AES-GCM and password-key helpers in data/crypto
  - 8944cf0 feat: encrypt and decrypt password-protected backup files
  - 3bc1486 test: cover backup round trip, tampering and format checks
  - f6bd6a6 feat: parse Google, Chrome and Bitwarden CSV exports up to 10 MB
  - ebdd8f1 test: cover CSV header mapping, quoting and the import size limit
  - 7f57c6b test: keep the CRLF line endings of the Chrome CSV fixture
  - 074644b test: restore the CRLF line endings in the Chrome CSV fixture
  - dc9ded5 feat: bulk insert credentials in one transaction, skipping duplicates
  - 1898ba1 test: cover duplicate skipping in the bulk insert
  - 6b646c3 feat: re-check the master password without changing the vault state
  - 0b530fc feat: export, read and import backup and CSV files through SAF URIs
  - 6a658c6 test: cover import counts, restore into a new vault and file limits
  - 4130375 feat: add backup export, backup import and CSV import to Settings
  - 783d0cd fix: hide the bottom bar while the keyboard is open
  - 3f8beb6 fix: keep the setup fields after a failed setup
  - 201b106 fix: drop a backup step that finishes after the vault locked
  - plus the `docs:` commit for this log
- Summary of changes:
  - Build: `kotlin.plugin.serialization` 1.9.0 (matches Kotlin), `kotlinx-serialization-json:1.6.3`, `commons-csv:1.10.0`. `.gitattributes` keeps the CSV fixtures byte-exact (`-text`).
  - `data/crypto/VaultCrypto.kt`: the private helpers are now internal top-level functions, `aesGcmCipher(mode, key, iv, aad)`, `KeyDerivation.derivePasswordKey(...)` and `SecureRandom.randomBytes(n)`. VaultCrypto uses them with the same AAD as before, so existing vaults still unlock. The backup code reuses them and adds no crypto of its own.
  - `data/backup/ExportFormat.kt`: `@Serializable` `BackupFile` (`format, version, kdf{type, memoryKiB, iterations, parallelism, salt}, cipher, iv, data`), `BackupHeader` (the same fields without `data`), `BackupItem(website, username, password, note, favorite, created)`, and the Credential↔item mappers.
  - `data/backup/BackupCrypto.kt`:
    - `encrypt(items, password, params)` uses a fresh salt and IV each time. The AAD is `Json.encodeToString(BackupHeader)`. `data` is AES-256-GCM over the JSON items array.
    - `parse(bytes)` runs before any KDF work. It checks, in this order: valid JSON → `Malformed`; a `format` other than `pm-export` (or a non-object) → `UnknownFormat`; `version != 1` → `UnsupportedVersion(v)`; then fields, cipher/KDF names, KDF bounds, Base64 and salt/IV length → `Malformed`.
    - `decrypt(backup, password)` maps an `AEADBadTagException` to `BackupException.WrongPassword`. It zeroes the key and the plaintext bytes.
  - `data/backup/ImportFile.kt`:
    - `InputStream.readImportFile()` reads at most 10 MB and throws `FileTooLarge` at byte 10 MB + 1. Outgrown buffers are wiped.
    - `toParsedImport()` counts rows with a blank website or password as invalid.
  - `data/backup/CsvImporter.kt`:
    - Uses commons-csv `RFC4180` with the header taken from the first row and header names matched case-insensitively. A leading UTF-8 BOM is skipped, and the strict UTF-8 decoder turns non-text into `NotText`.
    - Layout: if any header starts with `login_` → Bitwarden (requires `type,name,login_uri,login_username,login_password`; `notes` and `favorite` optional). Otherwise → Google/Chrome (requires `name,url,username,password`; `note` optional). Missing columns → `MissingColumns(list)`. Parser errors → `Malformed`.
    - Values are never trimmed or evaluated (`=SUM(A1)` stays literal).
  - `CredentialDao`: `getAll()`, `@Insert insertAll`, and the `@Transaction insertSkippingDuplicates(list): Int`. A row is a duplicate when its website+username (trimmed, lowercased) matches a saved row or an earlier row in the list. `CredentialRepository` exposes `getAll()` and `insertSkippingDuplicates()` through `write {}` (now generic), so a locked vault gives `VaultException.Locked`.
  - `VaultSession.verifyMasterPassword(pw)` runs `crypto.unlock` on `Dispatchers.Default`, zeroes the key and leaves the state alone.
  - `data/backup/BackupRepository.kt` (in `AppContainer`, sharing the Argon2 instance and the `kdfParams` with the vault):
    - `createBackup(pw)` → encrypted bytes
    - `save(uri, bytes)`
    - `readBackup(uri)` → `EncryptedBackup`
    - `importBackup(backup, pw)` → `ImportResult(imported, duplicates, invalid)`
    - `importCsv(uri)`, which zeroes the CSV bytes

    KDF and parsing run on Default, and I/O on IO.
  - UI (`presentation/SettingsScreen/BackupEvent.kt`, `BackupSection.kt`, `SettingsViewModel`): there is a new "Backup" section with three buttons, and the Settings column now scrolls. Every dialog is an `AlertDialog` with `SecureOn`, and a non-dismissable "Working…" spinner shows while the KDF or I/O runs.
    - Export:
      1. Master-password dialog.
      2. Export password plus confirmation. This reuses `validateMasterPassword`: 12 or more characters and matching.
      3. The data is encrypted.
      4. The `CreateDocument("application/json")` picker opens with `passwords-export.json`.
      5. The file is written, and "Backup saved" shows.
    - Import backup: `OpenDocument(*/*)` → read and parse → file-password dialog. A wrong password re-shows it with an error; otherwise the result dialog shows the counts.
    - CSV: a plaintext warning ("Choose file" / "Cancel") → `OpenDocument(*/*)` → result dialog. A failure shows "Nothing was imported or exported" with a specific message, for example "The file is larger than 10 MB.", "This is not a CSV file: it is not UTF-8 text.", "The CSV file has no username, password column…" or "This is not a Password Manager backup file."
    - A lock dismisses every backup dialog and drops the pending state. A step that finishes after a lock is dropped too.
  - Carried over from 04:
    - The bottom bar and FAB are hidden while the soft keyboard is open (`presentation/components/KeyboardVisibility.kt`, which reads the root window insets in a global-layout listener). This covers the Home search. Edit still hides the bar by route.
    - SetupScreen no longer clears its fields on submit. Success leaves the screen, which discards them.
- Decisions / deviations from brief:
  - The website is the URL's host (`https://github.com/login` → `github.com`, `android://…@com.example.app/` → `com.example.app`). If the URL has no host, the URL is kept as written; if it is blank, the name is used. This matches how the app already stores websites, and it makes duplicate matching work across paths.
  - A row with neither a URL nor a name has a blank website, so it is invalid too (the Add form rule). This also applies to backup items.
  - Duplicates inside one file are skipped like duplicates of saved rows. The first occurrence wins.
  - Non-login Bitwarden rows (notes, cards) are skipped silently and not counted. A multi-URI `login_uri` cell uses its first URI. `favorite` is on for `1` or `true`.
  - A backup JSON fed to "Import from CSV" fails as "not a valid CSV file" (its mid-field quotes break RFC 4180), not as missing columns.
  - KDF parameters read from a file are bounded (memory ≤ 256 MiB, iterations 1–20, parallelism 1–16), so a crafted file can't make Argon2 exhaust memory. The cipher must be `AES-256-GCM` and the KDF `argon2id`.
  - The AAD is the canonical kotlinx encoding of the parsed header, not the raw file bytes. Any changed value changes it.
  - Export encrypts *before* the save picker. Only ciphertext waits in the ViewModel, so the export password is zeroed as soon as possible. "Backup saved" shows no count.
  - Re-authentication before export is not throttled (the Unlock backoff does not apply). The vault is already open at this point, and Argon2 is the cost.
  - The size limit is enforced while reading, not taken from the provider's `SIZE` column, which can be missing or wrong.
  - Plaintext zeroing covers the JSON plaintext bytes, the CSV file bytes and the read buffers. `String`s (the JSON string, `Credential` fields) and the decoder and commons-csv char buffers can't be wiped, as with the rest of the app.
  - The import pickers accept `*/*`, so a wrongly typed file can be picked and gets a clear error.
  - Process slip: `7f57c6b` only adds `.gitattributes` (the global `core.autocrlf=input` had already stripped the CRLFs of `chrome.csv` in `ebdd8f1`). The fixture itself is fixed in `074644b`. Not amended, per the rules.
- Unit tests added (29 new, 127 total):
  - `data/backup/BackupCryptoTest` (9):
    - The round trip gives equal items, including a multi-line note with quotes and the favourite flag.
    - The file contains `"format":"pm-export"` and no website, username, password or note.
    - A wrong password throws `WrongPassword`.
    - Changing memoryKiB or parallelism (AAD only, since PBKDF2 ignores them), iterations, salt, iv or data makes decryption fail.
    - Two exports have a different salt, IV and data.
    - Invalid JSON → `Malformed`. Other formats or a non-object → `UnknownFormat`. Version 2 → `UnsupportedVersion(2)`.
    - Missing fields, an unknown cipher, a huge memory value, 0 iterations, bad Base64 or a short IV → `Malformed`.
  - `data/backup/CsvImporterTest` (9): uses the fixtures in `app/src/test/resources/csv/`.
    - `gpm.csv`: quoted commas, quotes and newlines are exact; an Android row; a trailing space and a formula are kept; 1 invalid.
    - `chrome.csv`: BOM, CRLF, no `note` column, and a name fallback.
    - `bitwarden.csv`: favourites, a multi-URI cell, the note row skipped, and 1 invalid.
    - Header order and case don't matter.
    - A row with no URL and no name is invalid.
    - Missing columns are listed per layout, including for an empty file.
    - Backup JSON → `Malformed`, PNG bytes → `NotText`, and an unterminated quote → `Malformed`.
  - `data/backup/ImportFileTest` (4): exactly the limit is read; limit + 1 throws; the default 10 MB limit stops reading at 10 MB + 1 bytes; invalid-row counting.
  - `data/backup/BackupRepositoryTest` (4, Robolectric with registered content URIs):
    - CSV import gives (3 imported, 0 duplicates, 1 invalid), then (0, 3, 1).
    - A backup restores into a second vault with every field equal, and the re-import gives (0, 2, 0).
    - A wrong backup password imports nothing.
    - An 11 MB file → `FileTooLarge` and nothing imported.
  - `CredentialDaoTest` (+2): duplicates ignoring case and whitespace, against saved rows and within the batch; the same list a second time inserts 0.
  - `VaultSessionTest` (+1): `verifyMasterPassword` accepts only the master password and stays Unlocked.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (127 tests, 0 failures, 0 skipped).
- Notes for reviewer/verifier:
  - Not run on a device: no emulator is attached, and the disk is nearly full. The SAF flows, the dialogs, Argon2 on export and import, and the keyboard detection for the bottom bar are unverified at runtime. `assembleRelease`/R8 was not built. kotlinx.serialization 1.6.3 ships its own R8 rules, but a release smoke test of export and import is worth one run.
  - Known limitation: the SAF picker sends the app to the background. With auto-lock "Immediately" (or a picker kept open past the timeout), the vault locks on return and the picker result is dropped, so nothing happens. Use the default 1 minute for the flow below.
  - Every commit compiles with its tests (each was compiled or tested before it was committed).
  - Manual test flow (package `com.example.passwordmanager`, emulator API 30+, auto-lock left at "After 1 minute"):
    1. Push the fixtures and the error files:
       - `adb push app/src/test/resources/csv/gpm.csv /sdcard/Download/gpm.csv`
       - `adb push app/src/test/resources/csv/bitwarden.csv /sdcard/Download/bitwarden.csv`
       - `adb shell screencap -p /sdcard/Download/not-a-csv.png`
       - `adb shell "head -c 11534336 /dev/zero > /sdcard/Download/big.csv"` (11 MB)
       - Then `adb shell content call --method scan_volume --uri content://media --arg external_primary`, so the files show in the picker's Downloads. If they still don't appear, open ☰ → the device storage root (for example "sdk_gphone64…") → Download.
    2. `adb uninstall com.example.passwordmanager; ./gradlew installDebug`, then launch.
    3. Setup carry-over: enter `correcthorse1` and `correcthorse2` → "mismatch" error, and both fields still hold their text. Fix the confirmation to `correcthorse1` → the vault opens.
    4. Home carry-over: tap the search field → the keyboard opens and the bottom bar and FAB are hidden. Press Back to close the keyboard → the bar returns.
    5. CSV import (GPM): gear → Settings → scroll to Backup → "Import from CSV" → the plaintext warning → "Choose file" → Downloads → `gpm.csv` → "Imported: 3, Skipped as duplicate: 0, Invalid (no website or password): 1". On Home, check:
       - `github.com` / `alice` / password `gh,secret"1`; Detail note is two lines: `line one` and `line two, with comma`
       - `www.example.com` / `bob@example.com` / `ex-pass-2` / empty note
       - `com.example.app` / `dave` / `app pass 4 ` (trailing space) / note `=SUM(A1)`
       - No `nopass.example.org` entry
    6. Mark `github.com` as a favourite (heart icon).
    7. Export:
       - Settings → "Export encrypted backup" → enter `wrongmaster12` → "Wrong master password." (after the spinner).
       - Enter `correcthorse1` → the export-password dialog. `short` / `short` → "Use at least 12 characters." `backup-pass-12` / `backup-pass-13` → "The passwords don't match."
       - `backup-pass-12` twice → spinner → the system picker opens with `passwords-export.json` → choose Downloads → Save → "Backup saved".
    8. `adb pull /sdcard/Download/passwords-export.json` → `grep -o '"format":"pm-export"' passwords-export.json` prints one match. `grep -E 'github|alice|gh,secret|example|bob|dave|SUM' passwords-export.json` prints nothing. The keys are `format, version, kdf{type:"argon2id", memoryKiB:65536 (19456 on a low-RAM device), iterations, parallelism, salt}, cipher:"AES-256-GCM", iv, data`.
    9. Restore:
       - `adb shell pm clear com.example.passwordmanager`, launch, and set up with `newmaster-123`.
       - Settings → "Import backup" → `passwords-export.json` → `backup-pass-13` → "Wrong export password, or the file was modified.", and Home stays empty.
       - Import again with `backup-pass-12` → "Imported: 3, Skipped as duplicate: 0, Invalid: 0". The three entries from step 5 are back with the same passwords and notes, and `github.com` is in Favourites.
    10. Import the same backup again → "Imported: 0, Skipped as duplicate: 3, Invalid: 0".
    11. Bitwarden: `adb shell pm clear com.example.passwordmanager`, set up again, then Import from CSV → `bitwarden.csv` → "Imported: 2, Skipped as duplicate: 0, Invalid: 1". Check:
        - `github.com` / `alice` / `gh-secret-1`, shown in Favourites, with note `multi` and `line "note"` on two lines
        - `example.com` / `bob` / `ex,pass-2`, not a favourite
        - No "Secure note" entry
    12. Errors (each shows "Nothing was imported or exported", and Home is unchanged):
        - Import from CSV → `not-a-csv.png` → "This is not a CSV file: it is not UTF-8 text."
        - Import from CSV → `big.csv` → "The file is larger than 10 MB."
        - Import from CSV → `passwords-export.json` → "This is not a valid CSV file."
        - Import backup → `gpm.csv` → "This backup file is damaged." (it is not JSON).
        - Import backup → a JSON file that isn't ours (for example `adb shell "echo '{\"items\":[]}' > /sdcard/Download/other.json"`) → "This is not a Password Manager backup file."
        - Import backup → `big.csv` → the 10 MB message.
    13. Cancel paths: back out of each picker → no dialog and no change. Cancel on each password dialog → closed.
    14. Screenshots: `adb exec-out screencap -p > shot.png` while a backup dialog is open → black.
    15. `adb logcat` during steps 5–12: no master, export or credential passwords, and no file contents.

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
