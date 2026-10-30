# Backlog

Ordered by dependency and priority. Decisions: `research/decisions.md`. Sizes: S < 1 day, M 1–3 days, L > 3 days.

| ID | Feature | Summary | Size | Status |
|---|---|---|---|---|
| 01 | [Encrypted vault](01-encrypted-vault/brief.md) | Build/test fixes, critical bug fixes, repository + app container; create a master password, add credentials into a SQLCipher DB keyed by an Argon2id-wrapped vault key, relaunch, unlock, credentials decrypt correctly. Backups disabled. | L | DONE |
| 02 | [App lock and hardening](02-app-lock-hardening/brief.md) | CryptoObject-bound biometric unlock, manual lock, auto-lock on background with configurable timeout, failed-attempt delay, FLAG_SECURE (incl. bottom sheet), settings screen, R8 log stripping. | L | DONE |
| 03 | [Credential management](03-credential-management/brief.md) | Detail/edit screen, delete with undo, copy with auto-clearing sensitive clipboard, search, working favourites tab, back-stack-synced bottom bar. | M | DONE |
| 04 | [Password generator](04-password-generator/brief.md) | SecureRandom generator (length, character classes, avoid ambiguous) inline in add/edit, plus a strength meter used on add/edit and master-password setup. | S | DONE |
| 05 | [Encrypted backup and import](05-backup-import/brief.md) | Password-protected encrypted export/import via SAF (Argon2id + AES-GCM JSON format) and CSV import from Google Password Manager / Chrome / Bitwarden. | L | DONE |
| 06 | [Autofill service](06-autofill/brief.md) | Android Autofill Framework service filling and saving logins with package/domain matching. **Future** — after 07–11. | L | PLANNED (future) |
| 07 | [Design system and modern UI](07-design-system/brief.md) | Central tokens in `ui/theme/` (teal light/dark schemes, typography, shapes, spacing, extended colours) + shared components; standard M3 nav bar with per-tab FAB; theme setting; all screens restyled. No dynamic colour. | M | DONE |
| 08 | [Branding: Vaultly](08-branding/brief.md) | App name "Vaultly", adaptive shield-lock icon (Material Symbols, Apache 2.0), core-splashscreen, welcome copy on unlock/setup. Data identifiers unchanged. | S | VERIFY_INCOMPLETE |
| 09 | [TOTP authenticator](09-totp/brief.md) | Authenticator tab: RFC 6238 codes (SHA1/256/512, 6/8 digits, period) with countdown, tap to copy; add via QR scan (zxing-android-embedded), QR image or Base32 key; stored in the vault DB (auto-migration v2); included in encrypted backup v2. | L | VERIFY_INCOMPLETE |
| 10 | [TOTP import and export](10-totp-transfer/brief.md) | Import Google Authenticator "Transfer accounts" QR (hand-written protobuf decode, multi-batch); export one otpauth:// QR per account (Google/Microsoft compatible) behind master-password re-auth. | M | IN_VERIFY |
| 11 | [Encrypted notes](11-secure-notes/brief.md) | Notes tab: unlimited notes in the vault DB (auto-migration v3), auto title (first chars + date, editable), list/search/create/edit/delete with Undo; included in encrypted backup. | M | IN_VERIFY |
| 12 | [Vault list filters](12-vault-list-filters/brief.md) | Remove the Favourites bottom tab; narrower search with a favourites toggle and sort menu (incl. Recently modified, DB v4); row shows a reveal (eye) icon, favourite moves into the row menu. | S | PLANNED |
| 13 | [Authenticator add flow](13-authenticator-add-flow/brief.md) | FAB opens the QR camera directly; camera screen offers only "Pick from gallery" and "Enter setup key"; Google Authenticator import moves to the top-bar menu. | S | PLANNED |
| 14 | [Notes redesign](14-notes-redesign/brief.md) | Editor without a title field (auto title shown inline and editable); no letter avatars; grid view (default) with list toggle in the top bar; search behind a top-bar icon. | M | PLANNED |
| 15 | [Onboarding and setup biometrics](15-onboarding/brief.md) | 3–4 first-run screens (vault, authenticator, notes, security); biometric opt-in on master-password setup. | M | PLANNED |
| 16 | [Passphrase generator](16-passphrase-generator/brief.md) | Optional word-based passphrase mode (bundled wordlist, SecureRandom), off by default. | S | PLANNED |
| 17 | [Website catalog](17-website-catalog/brief.md) | Bundled offline list of popular websites with suggestions while typing the website field on add/edit. | S | PLANNED |

Order: 07 → 08 → 09 → 10 → 11 (design system first so new screens use the tokens). Out of scope for now: password health, categories/tags, password history, change master password, passkeys/Credential Manager, breach check, cloud sync.
