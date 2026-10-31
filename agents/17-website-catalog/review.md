# Review – Website catalog

## Round 1
- Commit range reviewed: 28bee1d..4d5cea1
- Verdict: CHANGES_REQUESTED

### Findings
| # | Severity | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | major | util/WebsiteCatalogEntries.kt:166-190 | Indian banks moved to `.bank.in` by RBI mandate (deadline 31 Oct 2025). Old hosts only redirect now. SBI retail banking is `onlinesbi.sbi.bank.in` (also `sbi.bank.in`), HDFC is `hdfc.bank.in`, and Union Bank is `unionbankofindia.bank.in`. Storing the legacy host gives stale data and will break domain matching for autofill (06). | Re-check each domestic bank entry (SBI to AU, plus SBI Card, IDBI and others) against its official site and switch to the `.bank.in` host. Keep the old host as an alias only if the alias is useful for search. Foreign banks (SC, HSBC, DBS) are exempt. |
| 2 | major | util/WebsiteCatalogEntries.kt:49 | The service is "HBO Max" again (since 9 Jul 2025) and `max.com` redirects to `hbomax.com`. | Change it to `Website("HBO Max", "hbomax.com", listOf("max", "hbo"))`. |
| 3 | major | util/WebsiteCatalogEntries.kt:24 | Threads moved to `threads.com` on 24 Apr 2025, and `threads.net` now redirects. | Use `threads.com`. |
| 4 | major | util/WebsiteCatalogEntries.kt:301 | The NPS entry uses `enps.nsdl.com`, a legacy NSDL host. Protean's current eNPS SOP (v1.2, Mar 2026) points to `enps.nps-proteantech.in`. | Use `enps.nps-proteantech.in`. `abha.abdm.gov.in` checks out. |
| 5 | minor | util/WebsiteCatalogEntries.kt:83 | Skype was shut down in May 2025, so the entry is dead. | Remove it. |
| 6 | minor | util/WebsiteCatalogEntries.kt:16 | `office.com` now redirects to `microsoft365.com`. | Use `microsoft365.com`. |
| 7 | minor | WebsiteCatalogTest.kt | 18 tests is slightly heavy. `exactDomainIsFirst` overlaps `caseSchemeAndWwwAreIgnored` and `exactMatchRanksAbovePrefix`. `gitSuggestsGitHubFirst` asserts the whole list, so it will break when any new `git*` entry is added. | Merge `exactDomainIsFirst` into the normalisation test. Assert only `first()` for `git`. |
| 8 | minor | AddCredentialScreen.kt WebsiteField | When the draft changes from outside, `value` is rebuilt every recomposition while `field` keeps the stale text. This is harmless, but the double source of truth is subtle. | Optional: sync with `if (field.text != website) field = TextFieldValue(...)`, or accept it as is. |

### Notes (no action)
- Ranking matches the brief: trim, lowercase, one scheme and then `www.` are stripped, tiers run exact > name prefix > domain prefix > alias prefix > later name word, `sortedBy` is stable, the cap is 5, and each entry appears once.
- UX: the list shows only while the field is focused. It hides when the normalised text equals a returned domain, so picking a suggestion or opening an edit form shows nothing. Add and edit both go through `TextFields`. Picking a suggestion puts the cursor at the end.
- The `AppTextField` TextFieldValue overload is justified. The String variant keeps the old selection when the text is replaced, so the cursor would land mid-text. Calling `OutlinedTextField` inline instead would duplicate the field's styling.
- No network use: the catalog is compiled in, there is no INTERNET permission, and the query is not logged.
- Commit messages are short Conventional Commits with no prose bodies.

### Checklist
- [x] Correctness / no obvious bugs (code), [ ] catalog data (findings 1–4)
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (offline, nothing logged)
- [x] Critical business logic has unit tests; slight redundancy (finding 7)
- [x] Commit messages follow convention and are concise
- [ ] Build + unit tests pass (not re-run by reviewer; dev reports PASS)

## Round 2
- Commit range reviewed: fd1a61b..18cd410 (249debc, f330db9, 18cd410)
- Verdict: CHANGES_REQUESTED

### Findings
| # | Severity | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | major | util/WebsiteCatalogEntries.kt:183 | Yes Bank still uses `yesbank.in`. Its `.bank.in` host is `yes.bank.in`. The probe failed because the server resets HTTP/2 requests without a browser User-Agent. With a browser UA over HTTP/1.1, `yesbank.in` and `www.yesbank.in` both return 301 to `https://www.yes.bank.in/`, which returns 200. The TLS cert is issued to O=YES BANK LIMITED, CN=yes.bank.in. So `yesbank.in` works, but only as a redirect, the same legacy-host case as Round 1 #1. Autofill domain matching (06) will miss the host where users actually log in. | `Website("Yes Bank", "yes.bank.in", listOf("yes"))`. The alias is optional. Update the dev-log gap note. |
| 2 | minor | commit f330db9 | The change only touches tests but is typed `fix:`. Conventional Commits allows this, but the repo uses `test:` for test-only changes (d2bf40d). | No action now. Use `test:` next time. |

### Round 1 resolution
- #1 resolved, except Yes Bank (finding 1 above). I probed all 23 other domestic bank hosts. Each one returns 200, returns 301/302 to its own `www.`/path variant, or sits behind a WAF 403 with a matching cert (`bankofindia.bank.in`, CN=bankofindia.bank.in, O=Bank of India). Keeping SBI Card on `sbicard.com` (an NBFC) and leaving the foreign banks alone is correct.
- #2 `hbomax.com` returns 301 to `www.hbomax.com`. Resolved.
- #3 `threads.com` returns 301 to `www.threads.com`. Resolved.
- #4 `enps.nps-proteantech.in` returns 301 to `/eNPS/NationalPensionSystem.html`. Resolved.
- #5 Skype removed. #6 `microsoft365.com` returns 302 to `m365.cloud.microsoft`, Microsoft's own redirect, so it is acceptable.
- #7 resolved. `git` asserts only `first()`, and the `normalize` assertion was merged into the normalisation test. The dropped `domains("https://www.github.com").first()` check is still covered by the scheme/`www.` cases plus `exactMatchRanksAbovePrefix`.
- #8 left as is. That is accepted, since the finding was optional.
- Tests were updated for the new SBI host (`aliasesMatch`, `domainPrefixMatches`). No new issues in code or tests.
- Commit messages are short Conventional Commits with no bodies.

### Checklist
- [x] Correctness / no obvious bugs (code), [ ] catalog data (finding 1)
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (offline, nothing logged)
- [x] Unit tests updated and redundancy trimmed
- [x] Commit messages follow convention (finding 2 is a nit)
- [ ] Build + unit tests pass (not re-run by reviewer; dev reports PASS)

## Round 3 (orchestrator)
- Commit reviewed: 0dc56e7 (Yes Bank → yes.bank.in, one-line change, catalog tests pass)
- Verdict: APPROVED
