# Feature Research: Local-Only Android Password Manager

Scope: offline, single-user, Room-backed, Kotlin + Compose. Reference apps: Bitwarden, 1Password, KeePassDX, Proton Pass, Google Password Manager (GPM).
Complexity: S = < 1 day, M = 1-3 days, L = > 3 days.

## Current-state notes (from a quick look at the code, not a full audit)

- `Credential.password` is stored as **plaintext** in Room; the Keystore encrypt/decrypt calls in `CredentialViewModel.kt` / `CredentialScreen.kt` are commented out.
- `AndroidManifest.xml` has `allowBackup="true"` and `data_extraction_rules.xml` is still the unedited sample, so the DB can go into Google cloud backup or device transfer.
- `minSdk 27`, `targetSdk 34`, `androidx.biometric:1.1.0` (old; current stable is 1.1.x/1.2.x alpha, check before upgrading).
- These make "encryption at rest" and "backup exclusion" P0. Everything else builds on them.

## Must-have (needed for daily use)

| # | Feature | Why | Size | Android APIs |
|---|---|---|---|---|
| 1 | Encryption at rest | A password manager that stores plaintext isn't one. Every reference app encrypts the vault. | M | Android Keystore (AES-256-GCM, `KeyGenParameterSpec`), `Cipher`; optionally SQLCipher for full-DB encryption |
| 2 | App lock (biometric + device credential fallback) | Users unlock 20+ times a day; biometrics make that bearable. All 5 apps have it. | M | `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, `BiometricManager.canAuthenticate`, Keystore key with `setUserAuthenticationRequired` |
| 3 | Auto-lock timeout + lock on background | Unlocked vault on an unattended phone is the main local threat. Bitwarden/KeePassDX: configurable timeout (immediately, 1/5/15 min, never). | S | `ProcessLifecycleOwner` (`ON_STOP`), `SystemClock.elapsedRealtime`, DataStore for the setting |
| 4 | Screen protection | Blocks screenshots, screen recording and the recents thumbnail. Default-on in Bitwarden/KeePassDX. | S | `WindowManager.LayoutParams.FLAG_SECURE`; `setRecentsScreenshotEnabled(false)` (API 33+) |
| 5 | Copy to clipboard with auto-clear | The most-used action on mobile when autofill fails. KeePassDX clears after a configurable timeout (default ~30 s). | S | `ClipboardManager`, `ClipDescription.EXTRA_IS_SENSITIVE` (hides keyboard/clipboard preview, API 33+), `clearPrimaryClip()` (API 28+), `WorkManager` or a delayed coroutine for the clear |
| 6 | Search + sort | Finding an entry in < 2 s is the core loop. Instant filter on title/website/username. | S | Room `LIKE` query or in-memory filter on a `Flow`; Compose `SearchBar` |
| 7 | Password generator | Users need it whenever they create an account. All 5 apps have it. | S | `java.security.SecureRandom` (never `kotlin.random.Random`) |
| 8 | CRUD with edit, delete confirm, show/hide password | Basic correctness: edit existing entries, reveal toggle, undo or confirm on delete. | S | Compose `PasswordVisualTransformation`, `Snackbar` with undo |
| 9 | Encrypted backup export + import | Local-only means **the user's only protection against phone loss is a backup file**. Without it, offline-only is a liability. | M | Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`), PBKDF2/Argon2 key from a user-chosen backup password + AES-GCM |
| 10 | Autofill service | The difference between "a notes app for passwords" and a password manager. Bitwarden, KeePassDX, Proton, GPM all have it. | L | Autofill Framework (`AutofillService`, `FillResponse`, `Dataset`, `SaveCallback`), `AssistStructure` parsing, inline suggestions (`InlineSuggestionsRequest`, API 30+), Digital Asset Links / package-name matching |
| 11 | Backup exclusion | Stops the vault and its Keystore-bound ciphertext from leaving the device unencrypted or becoming unrecoverable after a restore. | S | `data_extraction_rules.xml` (API 31+), `fullBackupContent` (API ≤ 30), or `allowBackup="false"` |

Notes:
- #10 is the biggest daily-UX win but also the largest and riskiest item. Ship 1-9 and 11 first. #5 (clipboard) is the fallback until autofill lands.
- #9 must not be a plaintext CSV by default. GPM exports plaintext CSV and is criticised for it; offer CSV export only behind a warning.

## Should-have (big UX wins)

| Feature | Why | Size | Android APIs |
|---|---|---|---|
| CSV import from GPM / Bitwarden / Chrome | Lets users migrate their existing passwords. GPM CSV headers: `name,url,username,password,note`. Bitwarden and 1Password use their own column sets, so map them per source. | M | SAF, a CSV parser (handle quoted fields and newlines) |
| Password health (weak / reused / old) | 1Password Watchtower and GPM Checkup. Fully offline for weak, reused and age checks. | M | Local only: entropy or zxcvbn-style scoring, hash-grouping for reuse. Breach check (HIBP k-anonymity) needs network, so it is future/opt-in |
| Password strength meter on add/edit | Instant feedback while typing; cheap once the scoring exists. | S | Same scorer as above |
| Categories / tags + favourites filter | The app already has favourites. Categories (Login, Card, Note, Wi-Fi) or tags help once the vault holds 50+ items. | M | Room schema migration, `@Relation` or a tags table |
| Favicon / letter avatar | Faster visual scanning. Proton and Bitwarden show site icons. Offline: coloured first-letter avatar. Fetching real icons needs network (future). | S | Compose only |
| TOTP codes (2FA) | Proton (paid), Bitwarden (premium), KeePassDX (free). Removes the need for a separate authenticator app. | M | RFC 6238 HMAC-SHA1 (`javax.crypto.Mac`), `otpauth://` URI parsing, QR scan via CameraX + ML Kit barcode (adds a dependency) |
| Secure notes | Store recovery codes, Wi-Fi keys and PINs. Bitwarden, 1Password and Proton all have them. | S | Reuse the entry model with a type enum |
| Password history per entry | Recover the previous password after a botched site change. Bitwarden and KeePassDX have it. | S | Extra Room table |
| Quick Settings tile / app shortcut for the generator | Bitwarden v2 added a generator tile. | S | `TileService`, `ShortcutManager` |
| Keyboard-free entry: "copy username, then password" chain | Bitwarden/KeePassDX notification flow for apps where autofill fails. | M | Notification actions, `ClipboardManager` |
| Dark theme / dynamic colour | Expected on modern Android. | S | Material 3 `dynamicColorScheme` (API 31+) |

## Nice-to-have / future

- **Passkeys + Credential Manager provider** (L). On Android 14+ third-party managers can supply passwords and passkeys via `CredentialProviderService`. This is the long-term replacement for the Autofill Framework. KeePassDX, Proton and Bitwarden already support it.
- **Custom keyboard (KeePassDX "Magikeyboard")** (L). An `InputMethodService` that fills fields where autofill is blocked. Niche.
- **Breach check (HIBP k-anonymity)** (M). Needs the INTERNET permission, which breaks the "fully offline" promise. Make it opt-in.
- **KDBX (KeePass) format import/export** (L). Interop with KeePassXC/KeePassDX.
- **Attachments** (M). Encrypted files.
- **Credit cards / identities** (M), plus autofill of payment fields.
- **Duress / panic wipe**, failed-attempt lockout (S-M).
- **Cloud sync, multi-device, family/team sharing, browser extensions, email aliases (Proton)**. Out of scope for a local-only app.

## UX patterns worth copying

- **Generator options**: length slider (8-64, default 16-20), toggles for upper/lower/digits/symbols, "avoid ambiguous characters" (0/O, l/1), min digits/symbols, a passphrase mode (word count, separator, capitalise, include number) using EFF wordlist (Bitwarden), regenerate button, copy button, and generator history (Bitwarden v2).
- **Generator inline in add/edit**: a "generate" icon in the password field opens a bottom sheet. Don't make users leave the form.
- **Search**: search bar pinned at the top of the list, instant filter, matches website/title/username, recent/most-used first.
- **List row actions**: tap a row to open details; long-press or trailing icons to copy username or copy password without opening the entry (1Password, Bitwarden).
- **Copy feedback**: a Snackbar or toast saying "Password copied, clears in 30 s". On API 33+ the system already shows a copy toast, so skip yours to avoid a duplicate.
- **Masked by default**: passwords shown as dots, with an eye toggle. Optional monospace with coloured digits/symbols for readability (1Password, Bitwarden).
- **Lock screen**: biometric prompt shows automatically on open, with a fallback button. Never show vault content behind the prompt.
- **Empty state**: first-run screen offering "Add first password" and "Import from CSV".
- **Delete**: swipe or menu with an Undo Snackbar; optional trash/recycle bin (Bitwarden, KeePassDX).
- **Settings worth exposing**: auto-lock timeout, clipboard clear timeout, screenshot protection toggle (default on), biometric on/off, export/import.

## Security gotchas for implementers

- Keystore keys with `setUserAuthenticationRequired(true)` are invalidated when biometrics are re-enrolled (`setInvalidatedByBiometricEnrollment`). Handle `KeyPermanentlyInvalidatedException` with a recovery path, or the user loses the vault. This is another reason backup export (#9) is a must-have.
- Keystore-encrypted data cannot be restored on another device. Exclude it from backup (#11) and rely on the user-password-derived export (#9) for migration.
- Use `SecureRandom` for the generator and IVs. Never reuse a GCM IV.
- Don't log credentials; strip `Log.d` calls on sensitive paths.
- Autofill: match by package name **and** verify the web domain. Phishing apps can spoof `webDomain`, so prefer Digital Asset Links verification for app-to-site mapping.

## Sources

- [KeePassDX README](https://cdn.jsdelivr.net/gh/kunzisoft/keepassdx@master/README.md), [KeePassDX AutoFill wiki](https://github.com/Kunzisoft/KeePassDX/wiki/AutoFill)
- [Bitwarden Android autofill](https://bitwarden.com/help/auto-fill-android.md), [Bitwarden app settings](https://bitwarden.com/help/app-settings/), [Bitwarden mobile v2.0](https://bitwarden.com/it-it/blog/mobile-app-v2-0-released.md)
- [Proton Pass Android](https://proton.me/support/use-pass-android), [Proton Pass on F-Droid](https://f-droid.org/packages/proton.android.pass.fdroid/)
- [1Password Watchtower (Northwestern KB)](https://services.northwestern.edu/TDClient/30/Portal/KB/Article/2907/1Password-Use-Watchtower-in-1Password)
- [Google Password Manager CSV import](https://support.google.com/chrome/answer/13068232), [GPM CSV columns (Bitwarden forum)](https://community.bitwarden.com/t/error-importing-csv-file-from-google-password-manager/70499)
- [Android secure clipboard handling](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling), [Credential Manager provider integration](https://developer.android.com/identity/sign-in/credential-provider)
