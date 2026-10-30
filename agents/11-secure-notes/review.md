# Review – Encrypted notes

## Round 1
- Commit range reviewed: ca35b6c..a34384b
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | util/NoteTitle.kt `shorten` | Counting code points keeps surrogate pairs whole, but grapheme clusters (ZWJ emoji, flags, decomposed accents) can still be cut mid-cluster before the `…`. This is cosmetic and documented in the dev-log. | Optional: cut on `BreakIterator.getCharacterInstance(locale)` boundaries. It's a few lines and works on the JVM. |
| 2 | minor | presentation/Notes/NotesViewModel.kt `ChangeBody` / data/backup/ExportFormat.kt `toNote` | Imported notes aren't length-checked. A crafted backup with a body over 100 000 chars is stored, and the first keystroke in the editor then cuts it to 100 000. The rest of the body is lost without a warning. Files from this app never hit this. | Cut or skip oversized notes on import, or don't apply `limitLength` when the stored body is already over the limit. |
| 3 | minor | presentation/Notes/NotesViewModel.kt `CloseEditor` / `openEditor` | The save is launched asynchronously and `session.write` doesn't serialise anything. If the user reopens the same note within milliseconds, `getById` can read the pre-save row. The window is tiny. | None now. If it is ever seen, keep the save `Job` and join it in `openEditor`. |
| 4 | minor | presentation/Notes/NotesViewModel.kt `state` (`WhileSubscribed(5000)`) | If the Notes tab was left more than 5 s before the lock, the upstream has already stopped. The cached `NotesState` (titles and 200-char previews) then stays in VM memory while locked, because it isn't rendered and is only replaced after the next unlock. `CredentialViewModel` and `AuthenticatorViewModel` have the same pre-existing shape, so this is consistent, not new. | Later, for all three VMs: map the vault state into the flow so a lock always resets the value, or drop the cache on lock. |
| 5 | minor | presentation/Notes/NotesState.kt `NoteEditor`, `NotesEvent.ChangeTitle/ChangeBody/RestoreNote` | Data-class `toString` includes the note's title and body. Nothing logs them today. `Note`, `NoteSummary` and `BackupNote` already override `toString`; `NoteEditor` is the one the editor holds. | Optional: override `NoteEditor.toString` the same way. |
| 6 | minor | presentation/Notes/NotesScreen.kt `NoteItem` | The relative date is remembered per `updatedAt`, so "5 min. ago" doesn't advance while the list stays on screen. | Acceptable. |
| 7 | minor | dev-log "Unsaved edits are lost on auto-lock" | Acceptable. It matches the credential edit screen, and the DB is already closed when the lock is observed. One cheap improvement would be a best-effort save on `ON_STOP` while the vault is still unlocked, which would cover the background-timeout case. | Follow-up only if users hit it. |

### Notes on the checked areas
- **Title derivation:**
  - It takes the first non-blank line (`lineSequence` handles `\n`, `\r\n` and `\r`), trims it with `isWhitespace`, and collapses `\s` and `\p{Z}`, so NBSP is handled. It cuts to 23 + `…` with trailing space trimmed, then adds ` · d MMM yyyy` in the device locale. A blank body gives `Note · …`.
  - The title is only derived when the trimmed title is empty. Because the stored title is loaded into the field, both typed and auto titles are never re-derived on body edits, and this is tested.
  - Clearing the title of an existing note re-derives it with today's date. That is sensible: the title can't be empty, and the placeholder previews the result.
  - The 200-char title cap is cheap (one constant, one call). It bounds the list projection against a large paste, so keeping it is justified.
- **Save:**
  - Save, the back arrow and system Back all go through `CloseEditor`, and the `isCurrent` guard blocks double taps.
  - A new note with a blank title and blank body is dropped. An unchanged existing note isn't re-saved, so `updatedAt` stays the same.
  - An existing note emptied to blank is kept as `Note · <date>` with an empty body. That is acceptable: there's no implicit delete, and Delete is one tap away.
- **Delete and Undo:** the stored original is deleted. Undo upserts it with the same id (tested). The channel is drained on lock, and the send after the delete is skipped if the session changed. The snackbar host goes away with VaultShell.
- **Search:**
  - `likePattern()` is reused, the query uses `ESCAPE '\'` on title OR body, and results are ordered by `updatedAt DESC, id DESC`. The preview is `substr(body, 1, 200)` (characters, tested).
  - LIKE ignores case for ASCII only, the same as credentials.
- **Migration:**
  - The change is `@AutoMigration(2→3)` plus the committed `3.json`, and `1.json`/`2.json` are untouched.
  - The tests build v1 and v2 DBs from the exported schemas, then open them with the current Room, which validates the schema. They assert the version, that credentials and the TOTP code survive, and that the Note table is empty and writable.
- **Backup:**
  - `BackupContent.notes` defaults to empty, so a v2 file without `notes` decodes (tested), and the v1 array path is unchanged.
  - Duplicates are exact title+body, both against the DB and within the file, in one `@Transaction`. Re-importing reports every note as a duplicate (tested). The import dialog lists sections only when they are present.
- **Lock:** `editor = null` and the Undo queue is drained on any non-Unlocked state (tested). The list comes from `whileUnlocked`, so an active collector gets an empty list at once. See item 4 for the cached value.
- **Security:**
  - Notes live only in the SQLCipher DB and the encrypted backup. There's no `Log` or `println` in app code.
  - The editor is VM Compose state, not `rememberSaveable`, and the nav argument is only the id. FLAG_SECURE is set on the window (`MainActivity:138`), and the overflow popup only shows "Delete".
  - There are no notifications, share intents or clipboard use.
- **Patterns and style:**
  - The repository, VM, events, Undo channel and lock collector mirror 09 and `CredentialViewModel` (`addDraft`).
  - The UI reuses `SearchField`, `ItemRow`, `EmptyState`, `AppTopBar` and `AppTextField` (new optional params `placeholder`, `supportingText` and `subtitleMaxLines`, with defaults unchanged) plus `Spacing`. There are no hardcoded colours or sp.
  - The bottom bar has 4 tabs. `FULL_SCREEN_ROUTES` hides the bar and FAB on Edit, Export and the note editor. The FAB `when` routes Notes to `NOTE_EDITOR/-1`.
- **Tests:** they cover the title rules (24/25 limit, emoji, locale, blank), the DAO (round trip, preview, order, search, wildcards, duplicates), the repository (locked → `Locked`, relaunch), the VM (save, discard, no re-derive, unchanged, limits, rotation, missing note, Undo, lock), migration and backup. The VM test is justified, because the auto-title rule lives there. None are needless.
- **Commits:** conventional and one logical change each. `560c2ae` bundles two small component parameters needed by the same screen, which is fine.
- **Build:** `./gradlew assembleDebug testDebugUnitTest assembleRelease` → BUILD SUCCESSFUL, 234 tests, 0 failures. The R8 release build also passes.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass
