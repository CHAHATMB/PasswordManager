# Dev Log – Onboarding and setup biometrics

## Round 1
- Base commit: b4ada95
- Commits (review range `b4ada95..HEAD`):
  - 986acb9 feat: store whether onboarding was finished
  - a9c7922 test: cover the onboarding flag
  - 2a4cf0d feat: offer fingerprint unlock while creating the vault
  - 41d6f7f test: cover the setup biometric enrolment request
  - de8fdf3 feat: show onboarding before the first vault setup
  - plus the `docs:` commit for this log
- Summary of changes:
  - `SettingsStore.isOnboardingDone` (key `onboarding_done`, default false) in the `settings` prefs.
  - `presentation/Onboarding/OnboardingScreen.kt`: `HorizontalPager` with 4 pages (private `OnboardingPage` list), tonal-circle illustrations from `Icons.Rounded.*` with 1–2 `primary` badge circles, dot indicator, Skip (hidden but keeps its space on page 4), Next / Get started, `BackHandler` only on pages 2–4. Each page scrolls (same `heightIn(min)` pattern as `LockScreenLayout`), so landscape doesn't clip.
  - `MainActivity` NotSetUp branch: `rememberSaveable` `showOnboarding` from the flag; finish writes the flag, then shows `SetupScreen`. Unlocked branch: `LaunchedEffect` + `withResumed` runs `showEnablePrompt()` once if `takeBiometricEnrolmentRequest()`.
  - `SetupScreen`: `isBiometricAvailable` param and a `rememberSaveable` "Unlock with fingerprint" switch row (Settings row style + supporting text), disabled while working. `onSetup` passes `isBiometricAvailable && switch`.
  - `LockViewModel.setup(password, confirmation, enableBiometric)` and `takeBiometricEnrolmentRequest()`; the request is cleared on lock.
- Decisions / deviations from brief:
  - The enrolment request is set right before `session.setup` and cleared if it throws, not set after it returns. `VaultSession.setup` publishes `Unlocked` on `Dispatchers.Default`, so the Unlocked branch's effect could take the request before the setup coroutine resumes on Main and miss it. Observable behaviour matches the brief (only a successful setup leaves it true); validation errors never touch it.
  - Merged debug manifest checked (`processDebugMainManifest`): USE_BIOMETRIC, USE_FINGERPRINT, CAMERA and the dynamic-receiver permission; no INTERNET, so the "no internet access" line is kept.
  - The security page lists the six allowed facts as short centred lines (more than "1–2 lines", since the brief requires those facts). Page 4 uses PhoneAndroid + Lock/Fingerprint badges instead of `ic_shield_lock`, so all pages share one `ImageVector` type.
  - Enable-prompt failures reuse `SettingsViewModel` unchanged: a Keystore failure only sets `isBiometricError` in Settings state, which `refresh()` clears when Settings opens, so nothing is shown after setup.
- Unit tests added (+6; 267 total, 0 failures):
  - `SettingsStoreTest`: onboarding flag defaults to false; true survives a new store.
  - `LockViewModelTest`: success with biometrics → true once, then false; without → false; too short / mismatch → false, then a fixed setup → true; lock clears a pending request; the existing DB-failure test also asserts no request after a failed setup.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS.
- Notes for reviewer/verifier:
  - Not run on a device. Check: onboarding on fresh install, Back on pages 1 and 3, a kill mid-onboarding replays it, Skip then relaunch goes straight to setup, landscape scrolling, and the setup switch flows with `adb -e emu finger touch 1`.
  - Known gap: if the enable prompt is lost on rotation, the request is already consumed, so there is no re-prompt (accepted in the brief).
- Follow-up (main): hidden Skip on the last page uses `clearAndSetSemantics {}` so TalkBack skips it (1db1f5c); sorted the `OnboardingScreen` import in MainActivity (2e68d19).
