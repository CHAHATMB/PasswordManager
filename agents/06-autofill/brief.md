# Autofill service

Status: PLANNED (future: don't start until 01–05 are DONE)
Backlog ID: 06

## Goal
The app can be selected as the system Autofill service. It offers saved logins on matching login forms in apps and in Chrome, and offers to save new logins.

## Scope
- In:
  - An `AutofillService` declared in the manifest with `BIND_AUTOFILL_SERVICE`.
  - Parse the `AssistStructure` to find username and password fields. Use `autofillHints` first, then `inputType` (password variations), then id and hint text heuristics.
  - Matching:
    - Browsers: match `webDomain` against the registrable domain of the stored website.
    - Apps: match the calling package name against an optional stored `appPackage`.
    - Never fill when a web domain is present but doesn't match.
  - When the vault is locked: return an authentication `IntentSender` that opens the unlock flow, then returns the datasets.
  - Unlocked: datasets labelled with website and username.
  - `SaveInfo` with an `onSaveRequest` that stores a new credential, including the package name for apps.
- Out:
  - Inline (keyboard) suggestions.
  - Digital Asset Links verification.
  - Credential Manager / passkeys.
  - Payment and identity fields.
  - Compatibility-mode browsers beyond Chrome.

## Acceptance criteria
- [ ] Settings → System → Autofill service lists the app, and it can be selected.
- [ ] With the vault locked, focusing the username field on a Chrome login page shows an "Unlock" suggestion. Unlocking then fills the username and the password of the saved credential for that domain.
- [ ] A site with no matching credential shows no suggestions.
- [ ] Submitting a login form with new credentials shows the system save prompt. Accepting it adds the credential to the vault, and it is visible after unlock.
- [ ] A test app (or a login form in the app's own debug build) with a matching `appPackage` credential gets fill suggestions.

## Design notes
- Affected files/layers: new `autofill/` package (`VaultAutofillService.kt`, `FieldParser.kt`, `CredentialMatcher.kt`, `AutofillAuthActivity` or reuse of `MainActivity`), `AndroidManifest.xml`, `res/xml/autofill_service.xml`, `Credential` and the DAO (`appPackage`, query by domain or package).
- Data model / migration changes: add a nullable `appPackage` column, which bumps the schema to v2. If the app has been released by then, this needs a real Room migration (D3). Otherwise it is destructive.
- Security considerations:
  - Phishing apps can spoof `webDomain`, so only fill when the domain matches.
  - Never put passwords in dataset presentations.
  - Respect the vault lock and auto-lock.
  - No logging of `AssistStructure` contents.

## Critical logic needing unit tests
- Field classification from hints, input types and ids for representative login form shapes. Use plain data models, not `AssistStructure` directly.
- Domain matching:
  - subdomains (`login.example.com` ↔ `example.com`)
  - lookalikes (`example.com.evil.io` must not match)
  - scheme and path stripping
  - case-insensitivity
- Package matching, and the rule that a web domain takes precedence over the package.

## Dependencies
- 01–03 (vault, session, repository); 02 (lock state for the auth flow).
