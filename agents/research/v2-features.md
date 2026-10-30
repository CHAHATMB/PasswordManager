# Research: v2 features (07–11)

Inputs: user requirements (2026-10-30), current code on `main` (features 01–05 done). Decisions derived from this: `decisions.md` D6–D12.

## Codebase facts that shape the plan
- Toolchain: AGP 8.2.2, Kotlin 1.9.0, Compose compiler 1.5.1, BOM 2023.08.00 + `material3:1.2.1`, compileSdk/targetSdk 34, minSdk 27. New deps must be compatible; do not bump Kotlin/AGP unless forced.
- DB: `CredentialDatabase` (Room 2.6.1 over SQLCipher, `vault.db`), version 1, `exportSchema = true`, schema in `app/schemas/.../1.json`. Opened on unlock, closed on lock (`VaultSession`). Repositories read via `whileUnlocked {}` / write via `write {}` (`CredentialRepository`) — copy that pattern for new DAOs.
- Navigation: `MainActivity.VaultShell` (Scaffold + custom notched `BottomNavCustomShape` + centre FAB opening the add-credential `ModalBottomSheet`); routes in `presentation/AppNavigation.kt`; 2 tabs (Home, Favorite); Settings and Lock are icons in the Home top bar.
- Theme: `ui/theme/` is the Android Studio template (purple, `dynamicColor = true`, status bar = primary). `poppinsFontFamily` lists 9 `Font()`s without `weight=`, so every file maps to `FontWeight.Normal` (broken). Hard-coded colours/sizes in screens (e.g. `Color(0xFFEEF0F2)` in `TextFireldComponent.kt`, strength colours in `PasswordStrengthIndicator.kt`, `fontSize = 22.sp` in `LockScreenLayout.kt`).
- Strings are hard-coded in Kotlin; `strings.xml` only has `app_name = PasswordManager`. "Password Manager" also appears in Home/Favorites titles, `SecureClipboard.CLIP_LABEL`, backup error messages.
- Must NOT change for branding (breaks existing data): `applicationId`/namespace `com.example.passwordmanager`, vault AAD `"passwordmanager-vault-v$version"` (`VaultCrypto`), backup `format = "pm-export"`.
- Launcher icon: template Android robot (`drawable/ic_launcher_*`, legacy `mipmap-*/ic_launcher*.webp`). minSdk 27 ⇒ adaptive icon in `mipmap-anydpi` covers every device; legacy webps are unused.
- Reusable pieces: `SecureClipboard.copy()` (sensitive flag, 30 s auto-clear, cleared on lock), `VaultSession.verifyMasterPassword()` (re-auth), activity-registered SAF launchers + `autoLockObserver.expectAppTrip()/endAppTrip()`, `BackupCrypto` (v1 payload = JSON array of `BackupItem`), `readImportFile()` size cap (5 MB).
- Dialogs/sheets are separate windows: every new `Dialog`/`AlertDialog`/`ModalBottomSheet` needs `securePolicy = SecureFlagPolicy.SecureOn` (FLAG_SECURE). Any new Activity must set FLAG_SECURE itself.

## Design system (Compose Material 3)
- One `AppTheme` composable in `ui/theme/Theme.kt` is the single switch point. Layout:
  - `Color.kt`: raw tonal palette + `LightColors`/`DarkColors` (`lightColorScheme`/`darkColorScheme`, all roles filled). Generate with Material Theme Builder (seed teal `#00796B`, static scheme). Reference roles: light primary `#006A60`, onPrimary `#FFFFFF`, primaryContainer `#74F8E5`, onPrimaryContainer `#00201C`; dark primary `#53DBC9`, onPrimary `#003731`, primaryContainer `#005048`, onPrimaryContainer `#74F8E5`.
  - `ExtendedColors` (`success`, `onSuccess`, `warning`, `onWarning`) via `staticCompositionLocalOf`, exposed as `MaterialTheme.extendedColors` — M3 has no success/warning roles; needed for the strength meter and TOTP countdown in both themes.
  - `Type.kt`: full M3 `Typography` scale; Poppins with explicit weights (Regular/Medium/SemiBold/Bold) for display/headline/title/label, same family for body. Delete unused weight files.
  - `Shape.kt`: M3 `Shapes` (extraSmall 4, small 8, medium 12, large 16, extraLarge 28 dp).
  - `Spacing.kt`: `object Spacing { xs=4, s=8, m=12, l=16, xl=24, xxl=32 }` (dp). Plain object: theme-independent, no CompositionLocal needed.
- Rules: screens use only `MaterialTheme.colorScheme/typography/shapes`, `MaterialTheme.extendedColors`, `Spacing`. No `Color(0x…)`, raw `sp`, or ad-hoc dp gaps outside `ui/theme` (component-intrinsic sizes like icon 24.dp are fine).
- Dark theme: `isSystemInDarkTheme()` default; optional `ThemeMode {SYSTEM, LIGHT, DARK}` in `SettingsStore` (plain prefs, readable while locked). Window background via `values/` + `values-night/` `themes.xml` so there is no white flash in dark mode.
- System bars: `enableEdgeToEdge()` (activity 1.8) or set status-bar colour = `surface` with correct icon contrast; the current code inverts `isAppearanceLightStatusBars`.
- Navigation: replace the custom notched bar with the standard M3 `NavigationBar` (3–5 destinations) and a per-tab `FloatingActionButton` in the Scaffold — standard, accessible, makes the FAB contextual for the new tabs.
- Shared components (in `presentation/components/`): `AppTopBar` (M3 `TopAppBar`), `AppTextField`, `SearchField`, `ItemRow` (leading monogram/icon, title, subtitle, trailing slot — used by credentials, TOTP, notes), `EmptyState`, `SectionHeader`, `ConfirmDialog` (secure). Not wrapping M3 `Button` — theming already styles it.
- "Security feel": calm teal surfaces, shield/lock iconography on lock/setup/empty states, monogram avatars, plenty of whitespace, no gradients/heavy shadows; masked secrets by default.

## Branding / splash
- Name "Vaultly": `strings.xml app_name` (launcher label via manifest), top-bar titles, `CLIP_LABEL`, biometric prompt titles, backup messages/file name (`vaultly-backup.json`). Keep the data identifiers listed above.
- Icon source: Material Symbols **`shield_lock`** (Rounded, fill 1), Google `material-design-icons`, **Apache License 2.0** — https://fonts.google.com/icons?selected=Material+Symbols+Rounded:shield_lock , license https://github.com/google/material-design-icons/blob/master/LICENSE . Export as Android vector (24×24 viewport, path data).
- Adaptive icon: 108×108 dp canvas; keep the glyph in the central 66 dp safe zone (≈ 48–56 dp glyph); background = solid teal layer; foreground = white glyph; `monochrome` = same glyph (themed icons, API 33). Delete legacy `mipmap-*dpi/*.webp`.
- Splash: `androidx.core:core-splashscreen:1.0.1` (stable, compileSdk ≥ 31). Theme `Theme.App.Starting` parent `Theme.SplashScreen` with `windowSplashScreenBackground`, `windowSplashScreenAnimatedIcon` (icon without its own background: glyph within a 192 dp circle on a 288 dp canvas; simplest = reuse the adaptive foreground drawable, which follows the same 2/3 rule), `postSplashScreenTheme`. Manifest activity theme → `Theme.App.Starting`; call `installSplashScreen()` before `super.onCreate()`. No keep-on-screen condition needed (vault state is read synchronously from prefs). Night variant of the background colour in `values-night`.

## TOTP (RFC 6238 / RFC 4226)
- HOTP(K, C) = Truncate(HMAC-H(K, C)); C = 8-byte big-endian counter. Truncate: `offset = hash[last] & 0x0f`; `bin = ((hash[o] & 0x7f) << 24) | (hash[o+1] & 0xff) << 16 | (hash[o+2] & 0xff) << 8 | (hash[o+3] & 0xff)`; code = `bin % 10^digits`, left-padded with zeros.
- TOTP: `C = floor((unixSeconds − T0) / period)`, T0 = 0, period default 30 s. `Mac.getInstance("HmacSHA1"|"HmacSHA256"|"HmacSHA512")` (JCA, works in JVM tests). Remaining seconds = `period − (unixSeconds % period)`. Inject the clock.
- RFC 4226 App. D (key ASCII `12345678901234567890`, 6 digits), counters 0–9: `755224 287082 359152 969429 338314 254676 287922 162583 399871 520489`.
- RFC 6238 App. B (8 digits, period 30). Keys: SHA1 = ASCII `12345678901234567890` (20 B); SHA256 = `12345678901234567890123456789012` (32 B); SHA512 = `1234567890123456789012345678901234567890123456789012345678901234` (64 B). Verified with Python hmac:

| Time (s) | SHA1 | SHA256 | SHA512 |
|---|---|---|---|
| 59 | 94287082 | 46119246 | 90693936 |
| 1111111109 | 07081804 | 68084774 | 25091201 |
| 1111111111 | 14050471 | 67062674 | 99943326 |
| 1234567890 | 89005924 | 91819424 | 93441116 |
| 2000000000 | 69279037 | 90698825 | 38618901 |
| 20000000000 | 65353130 | 77737706 | 47863826 |

- Extra vector: Base32 `JBSWY3DPEHPK3PXP` (= `Hello!` + `DE AD BE EF`), SHA1/6/30, t = 59 → `996554`.
- Base32 (RFC 4648, alphabet `A–Z2–7`): hand-written decoder/encoder (~30 lines). Input normalisation: uppercase, strip spaces/hyphens, optional `=` padding; reject other chars. RFC 4648 §10 vectors: `""→""`, `f→MY======`, `fo→MZXQ====`, `foo→MZXW6===`, `foob→MZXW6YQ=`, `fooba→MZXW6YTB`, `foobar→MZXW6YTBOI======`. Avoid `commons-codec` (clashes with Android's bundled copy).
- Codes depend on the device wall clock; a skewed clock gives wrong codes (no NTP check — out of scope, mention in UI help only if cheap).

## otpauth:// URI (Google "Key Uri Format")
- `otpauth://TYPE/LABEL?PARAMETERS`; TYPE `totp` (supported) | `hotp` (reject: "counter-based codes not supported").
- LABEL = `issuer:account` or `account`, percent-encoded; separator may be `:` or `%3A`, optional spaces after it.
- Params: `secret` (required, Base32, padding optional), `issuer` (recommended; wins over label prefix if both), `algorithm` SHA1|SHA256|SHA512 (default SHA1, case-insensitive), `digits` 6|8 (default 6), `period` (default 30; accept 1–300), `counter` (hotp only). Ignore unknown params (`image`, etc.).
- Parse with `java.net.URI` + manual query split + `URLDecoder.decode(…, UTF-8)` (JVM-testable; no `android.net.Uri`).
- Build for export: `otpauth://totp/<enc(issuer)>:<enc(account)>?secret=<B32 no padding>&issuer=<enc(issuer)>&algorithm=…&digits=…&period=…`; encode spaces as `%20` (not `+`). Omit issuer prefix when issuer is blank.
- Compatibility: Google Authenticator and Microsoft Authenticator both scan standard `otpauth://totp` QR codes. Non-default parameters (SHA256/512, 8 digits, period ≠ 30) are not reliably honoured by them → show a warning on export for such entries.

## Google Authenticator "Transfer accounts" (otpauth-migration)
- URI: `otpauth-migration://offline?data=<percent-encoded standard Base64 protobuf>`. Decode: percent-decode → Base64 (accept missing padding, accept URL-safe alphabet) → `MigrationPayload`.
- Schema (reverse-engineered; used by e.g. `krissrex/google-authenticator-exporter`, `dim13/otpauth`, Aegis importer):
```
message MigrationPayload {
  repeated OtpParameters otp_parameters = 1;
  int32 version = 2; int32 batch_size = 3; int32 batch_index = 4; int32 batch_id = 5;
}
message OtpParameters {
  bytes secret = 1;      // raw key bytes (NOT Base32)
  string name = 2;       // account; may be "Issuer:account"
  string issuer = 3;
  Algorithm algorithm = 4;  // 0 UNSPECIFIED, 1 SHA1, 2 SHA256, 3 SHA512, 4 MD5
  DigitCount digits = 5;    // 0 UNSPECIFIED, 1 SIX, 2 EIGHT
  OtpType type = 6;         // 0 UNSPECIFIED, 1 HOTP, 2 TOTP
  int64 counter = 7;
}
```
- No period field → always 30 s. UNSPECIFIED algorithm → SHA1, UNSPECIFIED digits → 6. Skip (and count) HOTP, MD5 and UNSPECIFIED type.
- Wire format needed: tag varint `(field << 3) | wireType`; wire type 0 = varint, 2 = length-delimited; also skip 1 (8 B) and 5 (4 B) for unknown fields. Enums/int32/int64 are varints. ~60 lines hand-written reader with bounds checks (truncated input → typed error).
- Batches: a large export is split into several QR codes, each a self-contained payload with the same `batch_id`, `batch_index` 0..n−1 and `batch_size` n. Each QR can be imported on its own → no accumulation needed; just show "code k of n imported, scan the next".
- Test vector (built with a Python encoder, two TOTP accounts: `Example`/`alice@example.com` secret `JBSWY3DPEHPK3PXP` SHA1/6; `ACME Co`/`bob` secret `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ` SHA256/8; version 1, batch_size 1, batch_index 0, batch_id 123456):
  `otpauth-migration://offline?data=Ci4KCkhlbGxvId6tvu8SEWFsaWNlQGV4YW1wbGUuY29tGgdFeGFtcGxlIAEoATACCioKFDEyMzQ1Njc4OTAxMjM0NTY3ODkwEgNib2IaB0FDTUUgQ28gAigCMAIQARgBIAAowMQH`
  Tests should also cover data with percent-encoded `+ / =` (`%2B %2F %3D`).
- Microsoft Authenticator: no export of third-party TOTP accounts → importing from Microsoft is out of scope. Export to Microsoft works via standard otpauth QR.
- Rejected: `protobuf-javalite` + protobuf Gradle plugin (codegen plugin, version-compat with AGP 8.2/Kotlin 1.9, ~1 MB) for one 7-field message.

## QR scanning / generation
- Choice: **`com.journeyapps:zxing-android-embedded:4.3.0`** (Apache 2.0, bundles `com.google.zxing:core:3.4.1`).
  - `ScanContract` + `ScanOptions` (ActivityResult API) → drop-in, handles CAMERA runtime permission and a denied-permission message itself, includes `BarcodeEncoder` for generating export QR bitmaps.
  - Subclass `CaptureActivity` (`SecureCaptureActivity`) to set FLAG_SECURE and portrait; pass via `ScanOptions.setCaptureActivity`. Restrict to `QR_CODE`, disable beep.
  - Caveats: library is in maintenance mode and uses the Camera1 API (works on API 27–34); merged manifest adds `CAMERA` permission — check `uses-feature` camera is `required="false"`.
- Rejected: CameraX + zxing core (custom analyzer, preview, permission, lifecycle ≈ 150+ lines); ML Kit barcode (Google Play services / proprietary model, user asked for ZXing).
- "Scan from image": SAF `OpenDocument("image/*")` → downscaled `Bitmap` (≤ 2048 px) → `RGBLuminanceSource` → `HybridBinarizer` → `QRCodeReader` (zxing core, already on the classpath). Small, and makes QR flows verifiable on the emulator (push a PNG with `adb push`); emulator camera via virtual scene is fragile to automate.

## Secure notes UX
- Separate tab "Notes": list (title, one-line preview, updated date), newest first; search on title + body; FAB → editor.
- Editor: title field (optional) + multiline body; Save/back. Blank title on save ⇒ auto title = first non-blank line of the body, whitespace collapsed, ≤ 24 chars (cut with `…`) + ` · ` + today `d MMM yyyy` (e.g. `Wifi router admin · 30 Oct 2026`); empty body ⇒ `Note · 30 Oct 2026`. Existing titles are never re-derived. Discard empty new notes (blank title and body).
- Delete from editor/list with Undo snackbar (matches credentials).
- List query must not load full bodies (Room `CursorWindow` 2 MB): project `id, title, substr(body, 1, 200), updatedAt`. Cap body at 100 000 chars.
