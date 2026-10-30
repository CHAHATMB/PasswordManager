# Review – Onboarding and setup biometrics

## Round 1
- Commit range reviewed: b4ada95..39bcf8b
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | presentation/Onboarding/OnboardingScreen.kt (page 4 `lines`) | Six centred `bodyMedium` sentences are dense next to the 1-sentence pages 1–3, and centred multi-line text is hard to scan. Every claim is true (no INTERNET in merged manifest, SQLCipher, `Argon2Mode.ARGON2_ID`, auto-lock + FLAG_SECURE, Keystore, password-encrypted backups), so this is about layout, not content. | Optional: show the six facts as a left-aligned list with small leading icons, or shorten the Argon2id line ("Your master password is never stored and can't be recovered."). Check on device in landscape. |
| 2 | minor | presentation/Onboarding/OnboardingScreen.kt (Skip `TextButton`) | On page 4 Skip is `alpha(0f)` + disabled, but it stays in the semantics tree, so TalkBack still announces "Skip, disabled". | When `isLastPage`, add `.clearAndSetSemantics {}` (or `semantics { invisibleToUser() }`) to the modifier. |
| 3 | minor | MainActivity.kt (imports) | `Onboarding.OnboardingScreen` import sits between `NoteRoutes` and `Notes.*`, out of alphabetical order. | Move it after the `Notes.*` imports. |

Checked and OK:
- Onboarding shows only in the `NotSetUp` branch and only while the flag is unset. An existing vault is never `NotSetUp`, so upgrade users never see it. Skip and Get started both call `onFinish`, which writes the flag before hiding the pager. A kill mid-onboarding replays it (flag not written); `rememberSaveable` + the saveable pager state keep the page across rotation. `BackHandler` is enabled only for pages 2–4, so Back on page 1 closes the app.
- Enrolment request set before `session.setup`: correct and the simplest fix. `openVault` publishes `Unlocked` from `Dispatchers.IO`, so the Unlocked branch's `LaunchedEffect` can take the request before the setup coroutine resumes on Main. Setting it after `setup` returns could miss it. Validation errors never touch it, and any throw from `setup` (including the `check`) clears it before `Unlocked` can be published.
- No stale request or key-less enable: a lock (including the lock-again path in `openVault` when a lock was requested during setup) makes the collector clear the flag. A prompt still open across a lock does nothing, because `onBiometricAuthenticated` requires `Unlocked`. Cancel, error and Keystore failure (`newBiometricCipher()` → null) leave biometrics off and the vault open. The `isBiometricError` that a Keystore failure sets is cleared by `refresh()` when Settings opens. `enableBiometric` wraps the in-memory VK through the existing `VaultSession` path, so there is no new key handling.
- `isBiometricAvailable && enableBiometric` in `SetupScreen` is not redundant. The switch is `rememberSaveable` but availability is recomputed, so it guards against a fingerprint being removed across recreation.
- The setup switch row matches the Settings biometric row style, is off by default and is disabled while working.
- Tests cover the critical logic without trivia: the flag default and its persistence; enrolment true-once, off, invalid-then-valid, lock clears it, and the DB-failure path. The existing failure test was changed to `enableBiometric = true`, which covers the catch-and-clear branch.
- Commits: Conventional Commits, short subjects, one logical change each, no bodies.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass (`testDebugUnitTest --tests '*LockViewModelTest' --tests '*SettingsStoreTest'`: 22 tests, 0 failures)
