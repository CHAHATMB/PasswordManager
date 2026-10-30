# Backlog

Ordered by dependency and priority. Decisions: `research/decisions.md`. Sizes: S < 1 day, M 1–3 days, L > 3 days.

| ID | Feature | Summary | Size | Status |
|---|---|---|---|---|
| 01 | [Encrypted vault](01-encrypted-vault/brief.md) | Build/test fixes, critical bug fixes, repository + app container; create a master password, add credentials into a SQLCipher DB keyed by an Argon2id-wrapped vault key, relaunch, unlock, credentials decrypt correctly. Backups disabled. | L | DONE |
| 02 | [App lock and hardening](02-app-lock-hardening/brief.md) | CryptoObject-bound biometric unlock, manual lock, auto-lock on background with configurable timeout, failed-attempt delay, FLAG_SECURE (incl. bottom sheet), settings screen, R8 log stripping. | L | DONE |
| 03 | [Credential management](03-credential-management/brief.md) | Detail/edit screen, delete with undo, copy with auto-clearing sensitive clipboard, search, working favourites tab, back-stack-synced bottom bar. | M | PLANNED |
| 04 | [Password generator](04-password-generator/brief.md) | SecureRandom generator (length, character classes, avoid ambiguous) inline in add/edit, plus a strength meter used on add/edit and master-password setup. | S | PLANNED |
| 05 | [Encrypted backup and import](05-backup-import/brief.md) | Password-protected encrypted export/import via SAF (Argon2id + AES-GCM JSON format) and CSV import from Google Password Manager / Chrome / Bitwarden. | L | PLANNED |
| 06 | [Autofill service](06-autofill/brief.md) | Android Autofill Framework service filling and saving logins with package/domain matching. **Future** — not started until 01–05 are DONE. | L | PLANNED (future) |

Out of scope for now: TOTP, password health, categories/tags, secure notes, password history, change master password, passkeys/Credential Manager, breach check, cloud sync.
