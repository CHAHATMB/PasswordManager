# Verify – Website catalog

- Build commit: 5ab298d. Same screenshot build and vault as 12 (FLAG_SECURE-off patch, reverted afterwards).
- Device: AVD `Pixel_9a`, API 36, emulator-5554.
- Result: **PASS**
- Unit tests: `WebsiteCatalogTest` (17) → 0 failures.
- Not tested: airplane mode (the catalog is bundled; covered by the unit tests).

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | `git` → GitHub first, ≤5 rows, name + domain | Add sheet → Website `git` | PASS: GitHub `github.com`, GitLab `gitlab.com` | 01-add-suggestions-git.png |
| 2 | Scheme stripped | `https://git` | PASS: same list | – |
| 3 | Aliases and banks | `hdfc`, `sbi`, `insta` | PASS: HDFC Bank `hdfc.bank.in`, HDFC Securities, HDFC Life…; State Bank of India `onlinesbi.sbi.bank.in`, SBI Card; Instagram, Swiggy | 02-edit-suggestions-insta.png |
| 4 | Pick fills the domain, list closes, cursor at end | Tap GitHub → type `X` | PASS: `github.com`, then `github.comX` | – |
| 5 | No match → no list | `zzqx` | PASS | – |
| 6 | Free text saves unchanged | `zeta.example` → Save | PASS | – |
| 7 | Edit screen: no list on open; same behaviour on change | Edit zeta → change Website to `insta` | PASS | 02-edit-suggestions-insta.png |
| 8 | Save reachable with list + keyboard open | Add sheet with suggestions | PASS: Save at the bottom remained tappable | – |

## Issues found
- None.
