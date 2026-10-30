# Review – Passphrase generator

## Round 1
- Commit range reviewed: 4a93f64..f0672f9
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | res/raw/eff_large_wordlist.txt (`drop-down`, `felt-tip`, `t-shirt`, `yo-yo`) | With the default `-` separator, about 0.3 % of 6-word phrases show an extra hyphen, so the word boundaries are ambiguous when someone reads or types the phrase. Entropy is not affected: the words are still drawn uniformly. A real collision would need two different 6-word sequences to produce the same string, which takes another hyphenated split that is also a pair of list words, so the effect is negligible. | No code change. Keep the list unmodified, because filtering would change `EFF_WORDLIST_SIZE` and the entropy constant. The verifier should check the separator AC with a non-hyphen separator, as the dev-log says. |
| 2 | minor | README.md:63 | The credit line doesn't follow the neighbouring `[Name](link) - description` format. | Optional: `- [EFF Large Wordlist](https://www.eff.org/dice) - Passphrase words, © EFF, [CC BY 3.0 US](…)`. |

### Notes (no action)
- Correctness:
  - Words, digit position and digit value all come from `SecureRandom.nextInt(bound)`, so there is no modulo bias and no `kotlin.random`. Nothing is filtered or re-drawn.
  - The entropy math is right: `n·log2(7776)` + `log2(10)` + `log2(n)` for the digit. The position term holds because no EFF word contains a digit, so each position gives a distinct output.
  - The bands are 4 → 51 Fair, 6 → 77 Good, 7 → 90 Strong. 6 words plus a digit gives ≈ 83 bits, Strong, which is honest.
- Loading:
  - The list loads with an `async(IO, LAZY)` on the supervisor app scope and is cached for the process.
  - A parse failure stays inside the `Deferred` and reaches the sheet as `WordlistException : IOException`. It does not crash the app scope.
  - If the user switches off while the list is loading, the cancellation propagates normally and turning the switch on again re-awaits.
- Loader threading (MainActivity → AddCredentialScreen → GeneratorSheet) is acceptable:
  - The codebase passes dependencies to composables only by parameter or through VM factories. It has no `CompositionLocal` and no service locator in leaf composables.
  - Moving the loader into `CredentialViewModel` would still need a parameter to reach the sheet. `LocalContext → PasswordManagerApp` inside the sheet would break the existing pattern.
  - The bound reference `container::loadPassphraseWords` is equality-stable, so it causes no extra effect restarts.
- Tests cover the critical logic and don't test trivia:
  - generator: uniformity by chi-square, draw-to-index mapping, separators, capitalisation, digit placement and spread, word-count bounds
  - entropy
  - `fromBits` boundaries
  - the real list's size, uniqueness and characters, plus synthetic error cases
- Security: nothing is logged and the sheet stays `SecureOn`. The Add/Edit indicator over-rating passphrases is the known gap the brief accepted.
- Licence: the sheet caption and the README credit cover CC BY 3.0 US attribution. The file is bundled unmodified.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass (`testDebugUnitTest` for Passphrase/Wordlist/Strength: 30 tests, 0 failures)
