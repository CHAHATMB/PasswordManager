# Dev Log – Encrypted notes

## Round 1
- Base commit: ca35b6c (branch `scratch/STRY-11-secure-notes`)
- Commits (review range `ca35b6c..HEAD`):
  - 7533d01 feat: derive a note title from the first line and today's date
  - 47cdf93 test: cover note title derivation and length limits
  - bd808b4 feat: store notes in the vault DB with a 2 to 3 auto-migration
  - d396c00 test: cover note persistence, search and the v2 to v3 migration
  - 3e69874 feat: include notes in the version 2 backup
  - 70404a8 test: cover backup round trip with notes and older backups without them
  - 560c2ae feat: add a placeholder and supporting text to AppTextField and a line limit to ItemRow
  - 8f35b64 feat: add the Notes tab with search, editor, auto title and delete with Undo
  - 8be3ee1 test: cover note save, auto title, discard, undo and lock in the notes view model
  - 07af3f6 fix: keep the title and preview out of NoteSummary.toString
  - plus the `docs:` commit for this log
- Summary of changes:
  - `util/NoteTitle.kt`: `deriveNoteTitle(body, date, locale)` (first non-blank line, whitespace incl. NBSP collapsed, ≤ 24 code points incl. `…`, trailing space trimmed before `…`, ` · d MMM yyyy`; blank → `Note · …`). `limitLength` cuts input without splitting a surrogate pair. Limits: body 100 000, title 200.
  - `data/notes/`: `Note` entity (`toString` omits title/body), `NoteSummary` projection (`substr(body, 1, 200)`), `NoteDao` (upsert/delete, `search` LIKE on title or body newest first, `getById`, `getAll`, `insertSkippingDuplicates` on exact title + body), `NoteRepository` on the shared `whileUnlocked`/`write` helpers, reusing `likePattern()`.
  - `CredentialDatabase` v3, `@AutoMigration(from = 2, to = 3)`, `app/schemas/.../3.json` committed. `AppContainer.noteRepository`.
  - Backup: `BackupNote(title, body, created, updated)`, `BackupContent.notes` (default empty, still version 2). `ImportResult.notesImported/notesDuplicates`; the import dialog lists Authenticator and Notes sections only when present; export dialog text mentions notes.
  - UI: `NavigationItem.Notes` (StickyNote2) as the 4th tab, FAB opens `NOTE_EDITOR/-1`. `NotesScreen`: `SearchField`, `ItemRow` (title, one-line collapsed preview, relative date footer), empty and no-results states. `NoteEditorScreen`: title (placeholder = would-be auto title), multiline body with a counter from 90 000 chars, Save (check) / back arrow / system Back all save if changed; overflow Delete (existing notes only) → list + Undo snackbar. Bottom bar hidden on the editor (`FULL_SCREEN_ROUTES`).
  - `NotesViewModel`: editor state is Compose state in the VM (survives rotation, not in the Bundle); `openEditor(id)` keeps an editor already open for the same note; cleared together with pending Undo on lock. Date/time from an injected `java.time.Clock` + `Locale` provider.
- Decisions / deviations from brief:
  - 24-char limit counts the ellipsis (cut text is 23 + `…`) and counts code points, so emoji count as one. Grapheme clusters (ZWJ emoji, flags) can still be split between code points; harmless, not handled.
  - Title cap of 200 chars (not in brief): the list projection loads the full title, so it is bounded too. Input over a limit is cut, not rejected, so a large paste isn't silently ignored.
  - Clearing the title of an existing note re-derives it on save (the title can't be empty). An unchanged non-blank title is never re-derived.
  - Save closes the editor (same action as Back). No delete from the list rows (brief: editor overflow only).
  - Duplicate notes on import = exact title and body (case-sensitive), unlike credentials.
  - Backup notes are imported as-is (no length validation); the import file size cap still applies.
  - Extra test not in the brief: `NotesViewModelTest` (save/discard/no re-derive/undo/lock), since that is where the auto-title rule is applied.
- Unit tests added: `NoteTitleTest`, `NoteDaoTest`, `NoteRepositoryTest`, `NotesViewModelTest`; `CredentialDatabaseMigrationTest` (v2 → v3 from `2.json`, v1 → v3 now asserts version 3), `BackupCryptoTest` (round trip with notes, v2 payload without `notes` → empty), `BackupRepositoryTest` (notes restore + reimport duplicates, backup without notes).
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS (234 tests, 0 failures), no Kotlin warnings.
- Notes for reviewer/verifier:
  - Upgrade: install over the 10 build without uninstalling; credentials and authenticator accounts must stay, Notes tab empty.
  - A backup from the 10 build (no `notes`) must still import; a v1 backup from 08 too.
  - Unsaved edits are lost if the vault auto-locks while the editor is open (the DB is already closed when the lock is observed); same as the credential edit screen.
  - The editor briefly shows nothing during the pop animation after Save/Back (the editor state is cleared first).
  - Relative dates come from `DateUtils.getRelativeTimeSpanString` (e.g. "5 min. ago", "Oct 3").
  - `assembleRelease` (R8) not run.

## Round 2 (fixes from review/verify)
- Addressed findings:
- Commits:
