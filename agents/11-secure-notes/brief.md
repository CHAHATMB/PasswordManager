# Encrypted notes

Status: PLANNED
Backlog ID: 11

## Goal
A "Notes" tab for any number of private text notes, stored in the encrypted vault, with search, create, edit and delete. A note gets a sensible title automatically (start of the text + today's date) that the user can change.

## Scope
- In:
  - Data: `Note` entity (`id`, `title`, `body`, `createdAt`, `updatedAt`), `NoteDao`, `NoteRepository` (same `whileUnlocked`/`write` pattern). `CredentialDatabase` version 3 with `@AutoMigration(from = 2, to = 3)` (D11); commit `3.json`.
    - List/search query returns a projection `NoteSummary(id, title, preview = substr(body, 1, 200), updatedAt)` ordered by `updatedAt DESC`; search = LIKE on title or body (reuse `likePattern()`); full body only via `getById`.
  - Title derivation, pure function `deriveNoteTitle(body, date, locale)` (research § Secure notes): first non-blank line, whitespace collapsed, ≤ 24 chars (cut + `…`), then ` · d MMM yyyy`; empty/blank body → `Note · d MMM yyyy`. Applied on save only when the title field is blank; an existing title is never re-derived.
  - UI (07 tokens/components): bottom-nav tab "Notes" (icon `Icons.Rounded.StickyNote2`/`Description`); `SearchField`; list of `ItemRow`s (title, one-line preview, relative/short updated date); empty state. FAB → editor.
  - Editor screen: title field (placeholder shows the would-be auto title), multiline body (normal text keyboard), Save in the top bar; Back saves if changed (no silent loss) and discards a new empty note. Body limit 100 000 chars with a counter near the limit. Delete in the editor's overflow → back to list + Undo snackbar (same as credentials).
  - Backup (D12): add `notes` to `BackupContent` (default empty list, still version 2); export/import round trip; duplicate = same title and body; `ImportResult`/UI message includes notes.
- Out:
  - Rich text/markdown, attachments, checklists, folders/tags, pinning, sorting options, per-note locking, sharing/exporting a single note.

## Acceptance criteria
- [ ] Notes tab in the bottom bar; empty state when there are no notes.
- [ ] New note with blank title and body `Wifi router admin\npassword is on the box` → saved title `Wifi router admin · <today, d MMM yyyy>`; list shows title and preview.
- [ ] Long first line (> 24 chars) → title truncated with `…` before the date. Blank title + empty body → nothing saved (new note discarded).
- [ ] Typed title `Bank PINs` is kept; editing the body later does not change the title.
- [ ] Edit a note → `updatedAt` changes and the note moves to the top.
- [ ] Search matches title and body text (case-insensitive); `%` and `_` match literally; no hits → "no results" state.
- [ ] Delete → removed, Undo restores it; after lock the Undo is not offered.
- [ ] 60+ notes scroll smoothly; a note of ~100 000 chars saves and reopens; the list still loads.
- [ ] Lock/relaunch: notes persist; nothing visible while locked; screenshots blocked.
- [ ] Upgrade install over the 10 build: credentials and authenticator accounts preserved (auto-migration 2→3).
- [ ] Export backup → wipe → setup → import: credentials, authenticator accounts and notes restored; importing again reports notes as duplicates. A backup from the 10 build (no `notes` field) still imports.

## Design notes
- Affected files/layers: `data/notes/{Note,NoteDao,NoteRepository}.kt` (new), `util/NoteTitle.kt` (new, pure), `data/CredentialDatabase.kt` (entity, v3), `data/backup/{ExportFormat,BackupCrypto,BackupRepository}.kt`, `AppContainer.kt`, `presentation/Notes/` (list, editor, `NotesViewModel`), `presentation/AppNavigation.kt`, `MainActivity.kt` (tab + FAB action, hide bar on editor like Edit).
- Date for the title comes from an injected `Clock`/`LocalDate` provider and the device `Locale`, so tests are deterministic.
- Data model / migration changes: new table `Note`; DB v2 → v3 via `@AutoMigration`; backup v2 gains optional `notes`.
- Security considerations:
  - Notes only in SQLCipher DB and encrypted backup; no notes in logs, notifications, share intents or the clipboard.
  - List shows previews (they are vault content): same protection as credentials — FLAG_SECURE, cleared on lock.
  - Editor state (body string) must be cleared when the vault locks (VaultShell composition is dropped on lock; make sure the VM drops its editor state too).

## Critical logic needing unit tests
- `deriveNoteTitle`: normal first line, leading blank lines, whitespace collapsing, exactly 24 / 25 chars, emoji/surrogate pair not split, empty body, fixed date + `Locale.US` → exact string.
- `NoteDao`/`NoteRepository`: insert/update/delete round trip, summary projection (preview length, order by `updatedAt`), search on title/body with escaped wildcards, locked → `VaultException.Locked`.
- `BackupCrypto`: v2 round trip with notes; v2 file without `notes` decodes to an empty list.
- No UI tests.

## Dependencies
- 07 (tokens/components, nav bar), 09 (backup v2 `BackupContent`, DB v2 / auto-migration setup).
