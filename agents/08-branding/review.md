# Review – Branding: Vaultly

## Round 1
- Commit range reviewed: 915df11..f36c138
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | res/values/themes.xml:12, res/values-night/themes.xml:12 | Splash background is the neutral `background_light/dark` with a teal icon circle, not a teal "brand background" as the brief words it. Deviation is reasoned (no colour jump into the lock screen, teal still shown) and the dark variant matches the dark theme. | Accept; verifier confirms it reads as "teal shield" on cold start. |
| 2 | minor | res/values(-night)/themes.xml | Splash follows system night mode, not the in-app theme setting (same caveat as 07's window background). | None for now; note in verify. |
| 3 | minor | util/SecureClipboard.kt:88 | Clips copied by the 07 build (label "Password Manager") aren't recognised as ours after upgrade, so a pending auto-clear from the old process is lost. Only affects a clip live across the upgrade install. | Accept. |

### Notes
- Data identifiers untouched: `applicationId`/package, `VAULT_DB_NAME`, vault AAD `passwordmanager-vault-v*`, keystore `KEY_ALIAS`, `BACKUP_FORMAT = "pm-export"`. Only display strings and the default export file name changed.
- `installSplashScreen()` precedes `super.onCreate()`; FLAG_SECURE and `setRecentsScreenshotEnabled(false)` still set before `setContent`; `postSplashScreenTheme` restores `Theme.PasswordManager`.
- Icon: glyph spans x 34.8–73.2, y 30.3–77.7 dp on the 108 dp canvas, centred; farthest point ≈ 24 dp from centre, inside the 33 dp safe-zone radius. `ic_launcher` and `ic_launcher_round` both declare `monochrome`. Deleting the webp mipmaps is safe with minSdk 27.
- Attribution comment (source URL + Apache 2.0) present in both glyph XMLs. Duplicated path accepted.
- `brand_teal` matches light `primary` (`0xFF006A60`); no new hardcoded colours/sp in Compose. Lock header uses 07 typography/colour tokens.
- Remaining "PasswordManager" hits are internal class/style names; "Password Manager" hits refer to Google Password Manager (CSV import). Favorites/Settings titles keeping tab names is fine.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass (`./gradlew assembleDebug testDebugUnitTest`)
