# Review – Password generator

## Round 1
- Commit range reviewed: e3c8b8a..f25b813
- Verdict: CHANGES_REQUESTED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | major | util/PasswordStrength.kt:43 | A repeat anywhere earlier in the password counts as zero, so the score is capped at (distinct characters × log2 pool). Digits-only output from our own generator is Weak at every length (10 × 3.32 = 33 bits, even for 64 random digits ≈ 212 bits). Random 20-char lowercase (≈ 94 bits) scores Fair about 1 in 3 times (simulated, 2 000 runs). The sheet shows its own preview in red. `correcthorse1` = Weak is an acceptable result for a dictionary phrase, but it comes from this rule (`r`, `c`, `o`, `r`, `e` repeats), not from anything word-aware. | Only discount a character that repeats within a short window of previous characters (e.g. the last 2–3), keeping the ±1 sequence rule. That still catches `aaaa`, `abab` and `abcabc` without capping long random input. Add tests with fixed strings: a 20-digit random-looking string is not Weak, `abababababab` is still Weak. |
| 2 | minor | presentation/LockScreen/SetupScreen.kt:42 | The strength is computed twice, once here and once inside `PasswordStrengthIndicator`. | Fine as is. Optionally pass the computed level into the indicator. |
| 3 | minor | test/.../PasswordGeneratorTest.kt:72 | Hand-rolled try/catch with `throw AssertionError` and `println`. | Use `assertThrows` (JUnit 4.13+), or split into two `@Test(expected = …)` tests like `noSelectedClassIsRejected`. |
| 4 | minor | util/PasswordGenerator.kt:45 | Seeding one character per class and then filling from the union is not exactly uniform over all valid passwords: small pools such as digits are slightly over-represented. The entropy loss is negligible, and the Fisher–Yates shuffle uses the same `SecureRandom`, which is correct. | No change needed. Noted for the record. Rejection sampling would be the exactly uniform alternative. |

Notes (no action):
- Generator: `SecureRandom.nextInt(bound)` gives no modulo bias. The pools have no overlap. Avoid-ambiguous filters every pool. MIN_LENGTH 8 ≥ 4 classes, so the guarantee always fits. Specific sealed exceptions are used. The slider gives 57 positions, matching 8..64. Nothing is logged.
- Carried-over fixes look correct and not hacky:
  - Home: `padding` instead of `contentPadding`.
  - Edit: only the fields scroll and Save is pinned; the bar is hidden only on the `EDIT` route.
  - Detail Edit: uses the same `isCurrent()` guard as Back and Delete.
  - Delete vs lock: the identity check is race-free, because the check, the send and the drain collector all run on Main and `Unlocked` is a plain class, so each unlock makes a new instance.
- Tests use a seeded `SHA1PRNG` on the JVM, so they are deterministic and not flaky. The 1 000-run class-inclusion test at length 8 is meaningful (without the guarantee, ≈ 38 % of runs would miss digits).

### Checklist
- [ ] Correctness / no obvious bugs (finding 1)
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass (`./gradlew assembleDebug testDebugUnitTest`, 95 tests, 0 failures)

## Round 2
- Commit range reviewed: e2fe459..0d2080e
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | util/PasswordStrength.kt:46 | A block of 4 or more characters that repeats is no longer discounted, because the repeat window is 3. `qwerqwerqwer` → Fair (56 bits) and `Passw0rdPassw0rd` → Strong (83 bits). This trade-off comes from the window I suggested in Round 1. The estimator is advisory and has never been dictionary-aware. | No change for this feature. If it matters later, use a proper estimator (zxcvbn-style pattern matching) instead of growing the window. |

Notes (no action):
- Round 1 #1 is fixed:
  - The repeat rule now looks back only `REPEAT_WINDOW` = 3 characters.
  - The sequence rule now fires only on a ±1 run of 3 or more in the same direction.
  - The fix is small and readable, and `index - 2` is reached only when `index > 1`.
  - A Python mirror gives the same scores as the dev log: `abcdefgh` 9.4 bits and `aaaaaaaa` 4.7 bits (both Weak), `12341234` 13.3 bits (Weak), `password` 32.9 bits (Weak).
  - Two-step pairs (`12`) no longer cost anything, so the penalties don't over-fire.
- Round 1 #2: the `strength` overload is used by Setup. The `password` overload is still used by the Generator sheet and the Add screen, so there is no dead code.
- Round 1 #3: the test uses `assertThrows` with the length in the message.
- Tests are deterministic: they use fixed strings, plus the existing generator test with a seeded `SHA1PRNG`. The new cases cover the cases that should not over-fire (two-step pairs, 64 digits, 20 lowercase) as well as `aaaaaaaa`, `abababababab` and `abcdefgh`.
- `correcthorse1` now scores Good. That is acceptable for an estimator with no dictionary, and the verifier steps are updated in the dev log.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Unit tests pass (`./gradlew testDebugUnitTest`, 98 tests, 0 failures)
