# Verify – Vault list filters

- Build commit: 5ab298d. Debug APK with a temporary, uncommitted FLAG_SECURE-off patch, used only for screenshots. It was reverted afterwards and the unmodified APK was reinstalled. Same vault as 15 (master password `correcthorse1`, fingerprint enrolled).
- Device: AVD `Pixel_9a`, API 36, emulator-5554.
- Result: **PASS** (1 cosmetic issue)
- Unit tests: `CredentialViewModelTest` (7), `CredentialDaoTest` (12), `CredentialRepositoryTest` (10), `VaultSessionTest` (14), `CredentialDatabaseMigrationTest` (3) → 0 failures.
- Not re-run: upgrade install over the 11 build (covered by `CredentialDatabaseMigrationTest`), and the FLAG_SECURE block (the screenshot build had it disabled; the reinstalled build shows `SECURE` in the window flags).

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Bottom bar has no Favourites tab | Unlock | PASS: Passwords, Authenticator, Notes only | 01-list-revealed-password.png |
| 2 | Narrower search + heart + sort on one line; no sort in top bar | Passwords tab | PASS: top bar has Settings and Lock only | 01-list-revealed-password.png |
| 3 | Sort: Date added / Name (A–Z) / Recently modified | Add zeta, alpha, middle → each sort option | PASS: zeta, alpha, middle → alpha, middle, zeta → middle, alpha, zeta. The selected item is marked "Selected" | – |
| 4 | Editing an older credential moves it up; favouriting does not | Recently modified → edit zeta's note → back; then ⋮ → Add to favourites on alpha | PASS: zeta moved to the top; alpha stayed in place after favouriting | – |
| 5 | Heart filter, combined with search | Heart on → only alpha; search `zeta` → "No results" | PASS: content description switches to "Show all passwords" | – |
| 6 | Row eye reveals and re-hides (tap, ~30 s, lock, leaving the screen) | Eye on zeta → tap again; eye → wait 31 s; eye → Lock → unlock; eye → Notes tab → back | PASS: `ZetaPass123` shown, "Hide password" label; hidden in each case | 01-list-revealed-password.png |
| 7 | ⋮ → Add/Remove from favourites; detail heart follows | alpha ⋮ → Add → reopen ⋮ → open detail | PASS: menu reads "Remove from favourites"; detail heart reads "Remove from favourites" | – |

## Issues found
1. **Cosmetic:** the narrower search field wraps its placeholder onto two lines ("Search website or / username"), making the field taller than the icons next to it. Repro: Passwords tab with an empty search, Pixel 9a portrait. Suggest a shorter placeholder ("Search") or `maxLines = 1`. Long sites are truncated in the row ("middle.examp…"), which is fine.
- Note: the sort and filter reset after a process restart (out of scope per the brief).
