# Dev Log – Website catalog

## Round 1
- Base commit: 28bee1d
- Commits:
  - 8514474 feat: add offline website catalog with ranked suggestions
  - d2bf40d test: cover website catalog integrity and ranking
  - f691c40 feat: suggest catalog websites under the Website field
- Summary of changes:
  - `util/WebsiteCatalog.kt`: `Website`, `WebsiteCatalog.entries`, `suggest(query, limit)`, `normalize(query)`, `MAX_SUGGESTIONS`. Tiers 0–4 as in the brief, stable sort keeps catalog order.
  - `util/WebsiteCatalogEntries.kt`: 288 entries (global, then India: banks, UPI, investing/insurance, shopping, food/travel, telecom/OTT, gov.in, jobs/news/property).
  - `AddCredentialScreen.kt`: private `WebsiteField` with focus tracking and an inline `Surface` + `ListItem` list; tap fills the domain. Add sheet, Add and Edit routes all get it via `TextFields`.
- Decisions / deviations from brief:
  - Added a `TextFieldValue` overload of `AppTextField` (the String one is unchanged). The String-based field keeps the old cursor offset when the text is replaced, so "cursor at end after a tap" needs a selection. `WebsiteField` keeps the selection locally and falls back to "cursor at end" if the draft text changes from outside.
  - `normalize` is public so the UI can hide the list when the text equals a suggestion's domain; `suggest` itself still returns the exact-domain match (tier 0).
  - Word-start matching splits on space, `-`, `.`, so `com` also matches names like "Booking.com" (tier 4, harmless).
- Unit tests added: `WebsiteCatalogTest` (18 tests): size 200–300, non-blank names, unique domains, host regex/no `www.`, alias hygiene; blank/scheme/`www.`/case normalisation, alias, domain-prefix and word-start matches, no substrings, exact > prefix (`vi` → Vi over Vimeo), name > domain prefix (`mail` → Mailchimp over Gmail), catalog-order ties, no duplicates, limit cap.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS
- Notes for reviewer/verifier:
  - Not run on a device yet. Check focus stays in the field after a tap (clickable rows are not focusable in touch mode) and the list sits above the keyboard inside the scroll column.
  - Edit mode with a non-catalog website (e.g. "My Bank") shows matches once focused; that is intended (the list only hides on an exact domain).
  - Catalog domains were written from knowledge, not verified online; a few (e.g. `enps.nsdl.com`, `abha.abdm.gov.in`) are worth a spot check.

## Round 1 fixes (review round 1)
- Commits:
  - 249debc fix: update stale catalog domains
  - f330db9 fix: align catalog tests with new hosts and trim overlap
- Addressed:
  - #1: 24 domestic banks moved to `.bank.in`. Each host was checked through the old site's redirect, a TLS cert, or a live response on the new host. SBI is `onlinesbi.sbi.bank.in`. SBI Card (an NBFC) and the foreign banks are unchanged.
  - #2 HBO Max `hbomax.com`. #3 `threads.com`. #4 `enps.nps-proteantech.in`. #5 Skype removed. #6 `microsoft365.com`. The catalog now has 287 entries.
  - #7: the `git` test asserts only `first()`, and `exactDomainIsFirst` is merged into the normalisation test (17 tests).
  - #8: left as is. The fallback is deliberate and harmless.
- Gap: Yes Bank stays on `yesbank.in`. I could not confirm its `.bank.in` host: neither the old nor the candidate hosts responded, and search found no URL.
- Build/test: `./gradlew assembleDebug testDebugUnitTest --tests '*WebsiteCatalog*'` → PASS

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
