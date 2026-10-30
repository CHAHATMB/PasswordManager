# Website catalog

Status: PLANNED
Backlog ID: 17

## Goal
While typing in the Website field on add/edit, the user sees up to 5 matching popular services from a bundled offline catalog. Tapping one fills the field, so they don't have to type the full domain.

## Scope
- In:
  - A bundled catalog of about 200–300 entries: well-known global services plus India-popular ones (banks, UPI and payments, shopping, food, travel, telecom, OTT, gov.in portals).
  - Pure Kotlin matching and ranking.
  - A suggestion list under the Website field in the shared add/edit form.
- Out:
  - Network lookups and favicons or logos.
  - User-added catalog entries, and learning from the user's existing credentials.
  - A separate title/name field. The form has only `Website`, which doubles as the row title (see the decisions below).
  - Room tables and Android-app package matching (autofill, 06).

## Acceptance criteria
- [ ] Add sheet: typing `git` in Website shows a list under the field, with GitHub (`github.com`) first and at most 5 rows. Each row shows the name and the domain.
- [ ] Matching ignores case and strips a leading scheme and `www.`. `GIT`, `https://git` and `www.git` give the same list as `git`.
- [ ] Aliases match. `insta` shows Instagram, `sbi` shows State Bank of India (`onlinesbi.sbi`) and `prime` shows Amazon Prime Video.
- [ ] Tapping a suggestion sets Website to the entry's domain (for example `github.com`). The list disappears and the cursor stays in the field at the end of the text.
- [ ] No list when the field is empty, when it isn't focused, when nothing matches (for example `zzqx`), or when the text already equals a suggestion's domain.
- [ ] Free text still works. Typing `myintranet.local` and saving stores it unchanged.
- [ ] Edit screen: the same behaviour. Opening the edit form of a `github.com` item shows no list until the user changes the text.
- [ ] The Save button and the other fields remain reachable while the list is shown, with the keyboard open (imePadding, per 03).
- [ ] Airplane mode: suggestions still appear. The app has no INTERNET use for this feature.

## Design notes
- Affected files/layers:
  - New `util/WebsiteCatalog.kt`:
    - `data class Website(val name: String, val domain: String, val aliases: List<String> = emptyList())`.
    - `object WebsiteCatalog` with `val entries: List<Website>` and `fun suggest(query: String, limit: Int = MAX_SUGGESTIONS): List<Website>`.
    - Pure Kotlin with no Android types, like `util/PasswordGenerator.kt` and `util/totp/`. If the list makes the file unwieldy, put the entries in a sibling file such as `util/WebsiteCatalogEntries.kt`.
  - `presentation/AddScreen/AddCredentialScreen.kt` (`TextFields`):
    - Track Website field focus with `Modifier.onFocusChanged`.
    - Compute `remember(website) { WebsiteCatalog.suggest(website) }` and render the rows directly under the field, inside the scrolling column.
    - On tap, call `onDraftChange(draft.copy(website = it.domain))`.
    - The add sheet, the `Add` route and the `Edit` route all use `TextFields` (MainActivity.kt:319, 355, 462), so add and edit both get the feature with no other screen changes.
  - The `AppTextField` signature is unchanged; the focus modifier goes through `modifier`.
  - No ViewModel or event changes. Suggestions are derived UI state from `draft.website` (pure and cheap: about 250 entries).
- Decisions:
  - **Storage: a Kotlin `listOf(...)`**, not a JSON asset.
    - No I/O, no async loading, no parse-error path, and typos are caught at compile time.
    - About 250 three-field constructors stay well under the JVM method-size limit.
    - We don't need kotlinx.serialization here. Rejected Room (static read-only data) and an assets JSON (adds loading and error handling for no gain).
  - **Data shape:**
    - `name`: display name, for example "GitHub".
    - `domain`: lowercase host with no scheme, path or `www.`.
    - `aliases`: optional lowercase extra search terms, for example `["sbi", "state bank"]` or `["twitter"]` for X.
    - Subdomains are allowed where the service lives there (for example `drive.google.com`). Domains are unique.
  - **What gets filled:** the domain, not the display name.
    - This matches `CsvImporter.websiteOf` (it stores the host) and what 06 autofill will need for domain matching.
    - There is no title field, and adding one means a schema change and migration, which is out of scope.
    - So the brief's "fill the title if empty" collapses to "fill Website with the domain".
  - **Matching:**
    - Normalise the query: trim, lowercase, then strip `http://`, `https://` and `www.`.
    - A blank result gives an empty list.
    - Tiers, lower is better:
      - 0: exact match on name, domain or alias.
      - 1: name starts with the query.
      - 2: domain starts with the query.
      - 3: an alias starts with the query.
      - 4: a later word of the name starts with the query (word boundary on space, `-` or `.`; for example `video` matches "Amazon Prime Video").
    - No substring or fuzzy matching.
    - Within a tier, keep catalog order. Write the catalog roughly in popularity order and use a stable sort.
    - Each entry appears once, at its best tier. Cap at `MAX_SUGGESTIONS = 5`.
    - Hide the list when the normalised query equals an entry's domain (the user just picked it, or edit mode).
  - **UX: an inline list**, a `Column` of `ListItem` rows (headline = name, supporting = domain) inside a `Surface` or card.
    - Rejected `ExposedDropdownMenuBox`. On material3 1.2.1 its menu popup takes focus from an editable text field, which closes the keyboard while typing. It is also a separate window inside the FLAG_SECURE bottom sheet.
    - Use theme tokens (`Spacing`, `MaterialTheme.shapes`). Rows are clickable with at least a 48dp touch target.
- Data model / migration changes: none.
- Security considerations:
  - Fully offline. No network, no favicon fetch, no analytics.
  - The catalog is public, non-secret data compiled into the APK.
  - Don't log the typed query. Matching runs in memory only.

## Critical logic needing unit tests
- `WebsiteCatalogTest`, integrity of `WebsiteCatalog.entries`:
  - The size is between 200 and 300.
  - Names are non-blank.
  - Domains are unique.
  - Every domain matches a lowercase host regex (labels `[a-z0-9-]`, no leading or trailing `-`, a TLD of 2 or more letters, so `.co.in`, `.gov.in` and `.sbi` pass) and has no scheme, path or `www.` prefix.
  - Aliases are lowercase, non-blank and unique within an entry.
  - Failure messages name the offending entry.
- `WebsiteSuggestionsTest` (or the same class, `suggest(...)`), run against the real catalog:
  - A blank or whitespace query gives an empty list.
  - Case-insensitive.
  - Scheme and `www.` are stripped.
  - Prefix match on name, domain and alias.
  - Word-start match within the name.
  - No substring matches (for example `ithub` returns nothing for GitHub).
  - Exact match ranks above prefix, and name prefix ranks above domain prefix.
  - Ties keep catalog order.
  - No duplicates.
  - The result is capped at 5 (and at `limit` when one is passed).
  - An unknown query gives an empty list.

## Dependencies
- 03 (the shared add/edit form `TextFields`) and 07 (theme tokens). Done.
- Parallel features 12 and 16 may also touch `AddCredentialScreen.kt` or `GeneratorSheet`. The Website-field change is local, so expect at most a trivial cherry-pick conflict.
