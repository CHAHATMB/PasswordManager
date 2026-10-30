# Verify – Encrypted backup and import

- Build commit: 31bed4a (last code commit d0b3d4f). Debug via `./gradlew installDebug`, with FLAG_SECURE (activity) and `SecureFlagPolicy.SecureOn` (backup dialogs, Add sheet, generator sheet) turned off in a local, uncommitted edit for screenshots only; restored with `git checkout` afterwards and reinstalled for the final secure-build check (41).
- Device: AVD `Pixel_9a` (sdk_gphone64_arm64, API 36), headless, device PIN 1234. Test files pushed to `/sdcard/Download` per dev-log Round 2 (`big.csv` = 6 MB, plus `chrome.csv`, `other.json`, and `tampered.json`).
- Result: PASS

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| C1 | Failed Setup keeps its fields | `pm clear`, `correcthorse1` / `correcthorse2` → "Passwords do not match"; both fields revealed still hold their text. Fixed the confirmation → vault opens | PASS | 01-setup-mismatch-fields-kept.png |
| C2 | Home bottom bar hidden while the search keyboard is open | Tap search → `mInputShown=true`, no Home/Favorite/Add nodes in the dump. Back → bar and FAB return | PASS | 02-home-empty-bar-visible.png, 03-home-search-keyboard-bar-hidden.png, 04-home-keyboard-closed-bar-back.png |
| 1 | GPM CSV: warning, exact values, 1 invalid; re-import = duplicates | Plus 1 manual entry (`manual.net/mona`). Import from CSV → plaintext warning → picker → `gpm.csv` → "Imported: 3, Skipped as duplicate: 0, Invalid: 1". Values: `gh,secret"1`, `ex-pass-2`, `app pass 4 ` (trailing space kept), github note `line one\nline two, with comma`, note `=SUM(A1)`, `com.example.app` host. No `nopass.example.org`. Re-import → 0 / 3 / 1 | PASS | 05–09, 12, 13 |
| 1b | Chrome CSV (BOM, CRLF, no note) | `chrome.csv` → 2 / 0 / 0 (`gitlab.com/erin/gl-pass,5`, `Intranet/frank/intranet-6` via name fallback). Re-import → 0 / 2 / 0 | PASS | 10, 11 |
| 2 | Export: wrong master rejected; export password rules; picker saves `passwords-export.json` to Downloads | `wrongmaster12` → "Wrong master password." `correcthorse1` → export-password dialog. `short`/`short` → "Use at least 12 characters." `backup-pass-12`/`backup-pass-13` → "The passwords don't match." `backup-pass-12` twice → picker in Downloads with `passwords-export.json` → SAVE → "Backup saved" | PASS | 14–18 |
| 3 | Pulled file is pm-export JSON with no plaintext | `adb pull` (1260 B): keys `format, version, kdf{argon2id, 65536 KiB, 3 it, p4, salt}, cipher AES-256-GCM, iv, data`; one `"format":"pm-export"` match; 0 matches for any website, username, password or note typed or imported | PASS | – |
| 4 | Restore into a fresh vault: wrong password imports nothing; correct one restores all | `pm clear`, setup `newmaster-123`. Import backup → `backup-pass-13` → "Wrong export password, or the file was modified.", Home empty. `backup-pass-12` → "Imported: 6, 0, 0". All 6 rows match (passwords, notes incl. multi-line and `hand-note`), github.com in Favourites | PASS | 19–21, 24–26 |
| 5 | Same backup again → 0 imported, all duplicates | → "Imported: 0, Skipped as duplicate: 6, Invalid: 0" | PASS | 22 |
| 5b | Tampered file fails cleanly | One Base64 char of `data` changed, pushed as `tampered.json`, correct password → "Wrong export password, or the file was modified.", nothing imported | PASS | 23 |
| 6 | Bitwarden CSV, including favourites | `pm clear`, setup, `bitwarden.csv` → 2 / 0 / 1. `github.com/alice/gh-secret-1` favourite, note `multi\nline "note"`; `example.com/bob/ex,pass-2` not favourite; no Secure note. Re-import → 0 / 2 / 1 | PASS | 27–30 |
| 7 | Non-CSV or >5 MB file → error, nothing imported | CSV: `not-a-csv.png` → "This is not a CSV file: it is not UTF-8 text."; `big.csv` → "The file is larger than 5 MB."; `passwords-export.json` → "This is not a valid CSV file." Backup: `gpm.csv` → "This backup file is damaged."; `other.json` → "This is not a Password Manager backup file."; `big.csv` → 5 MB message. All under "Nothing was imported or exported"; Home still 2 rows | PASS | 31-*, 32-* |
| 8 | Auto-lock "Immediately": export and import via picker work | Export → SAVE → "Backup saved" (564 B file). Import backup → 0 / 2 / 0. Import CSV `gpm.csv` → 2 / 1 / 1. Vault stayed unlocked across each picker trip | PASS | 33, 35–37 |
| 9 | Cancel export picker leaves no file | Reached save picker, Back → back in app, unlocked, no `passwords-export*` in `/sdcard/Download` | PASS | 34 |
| 10 | Lock mid-export leaves no empty file | Save picker open → Power off/on + device PIN → SAVE → app shows Unlock vault; no `passwords-export*` in Downloads | PASS | 38 |
| 11 | Screen-off still locks with "Immediately" | Unlocked Home → Power off/on → Unlock vault | PASS | 39 |
| 12 | Import picker cancel paths | Back out of Import backup and Import CSV pickers → Settings, no dialog, no change | PASS | 40 |
| 13 | Secure build hides dialogs | After restoring the flags: Export master-password dialog open (confirmed by uiautomator) → screencap black | PASS | 41-secure-build-dialog-black.png |
| 14 | Logcat hygiene | ~258k lines over the session: 0 `FATAL EXCEPTION`, empty crash buffer; 0 hits for every master, export and credential password, username or note used (`correcthorse`, `wrongmaster`, `backup-pass`, `newmaster`, `bwmaster`, `bw-backup`, `manualpw77`, `gh,secret`, `gh-secret`, `ex-pass-2`, `ex,pass`, `app pass 4`, `gl-pass`, `intranet-6`, `hand-note`, `line one`, `SUM(A1`, `bob@example`, `mona`) | PASS | – |

Notes:
- The 5-minute app-trip cap (picker left open > 5 min with "Immediately") was not waited out; the lock-during-picker path was covered with screen-off instead (row 10).
- The AVD has a device PIN (1234), so storage is unavailable until the keyguard is unlocked after boot; `adb push` fails before that.
- Wrong master/backup password dialogs clear the field on error, while the export-password dialog keeps both fields after a rule error. Not a criterion; mention only.

## Issues found
- None.
