# Dev Log – Notes redesign

## Round 1
- Base commit: 4a93f64 (main)
- Commits:
  - 71bace3 docs: add feature 14 brief (the folder was untracked on main; copied in so the log can be committed)
  - b5dc11d feat: store the notes layout setting
  - acd6c1e test: cover the notes layout setting
  - 709e769 feat: let a new note's title follow its body until edited
  - f346f59 test: cover the live note title
  - cb568b2 feat: make the note editor a borderless title and body
  - 795a90a feat: show notes as cards in a grid or list with search behind an icon
- Summary of changes:
  - `SettingsStore`: `NotesLayout { GRID, LIST }`, `notesLayout` StateFlow + `setNotesLayout()`, key `notes_layout`, same pattern as `themeMode`.
  - `NoteEditor.isTitleAuto` (true for a new note). `ChangeBody` sets the derived title while auto (empty for a blank body). `ChangeTitle` sets `isTitleAuto = title.isEmpty()`. `toNoteToSave` unchanged.
  - `NoteEditorScreen`: two borderless M3 `TextField`s (title `titleLarge`, single line, IME Next → body; body fills the rest and keeps the 90 000+ counter). The body gets focus for a new note. Empty top-bar title. `autoTitle` param dropped; `NotesViewModel.autoTitle` is now private.
  - `NotesScreen`: top-bar Search + layout toggle. The search row (`SearchField` + Close) shows only while open (`rememberSaveable` boolean; Back closes; closing clears the query). Staggered grid (Adaptive 160dp, 8 preview lines) or `LazyColumn` (2 lines) of a private `NoteCard`. The preview keeps line breaks; 3+ breaks collapse to 2.
  - `NotesViewModel` takes `SettingsStore`, exposes `layout`, handles `NotesEvent.ChangeLayout`.
  - Orphans removed: `ItemRow.subtitleMaxLines`, `AppTextField.placeholder`/`supportingText`.
- Decisions / deviations from brief:
  - The Search icon is hidden while the search row is open (the row's Close replaces it).
  - A whitespace-only title counts as typed (`isEmpty`, per brief), so it stops tracking. It is still derived on save.
  - The live title uses the date at typing time, so a note saved after midnight keeps the earlier date.
  - The search row shows both `SearchField`'s Clear X (only with text) and the Close X. Clear empties the query; Close hides the row.
- Unit tests added:
  - `SettingsStoreTest`: notes layout defaults to GRID, round-trips, unknown → GRID.
  - `NotesViewModelTest`: the title follows the body and is saved; a blank body gives an empty title and is discarded; a typed title stops tracking and clearing it resumes; an opened note never re-derives. Existing cases are unchanged and still pass under the new rule.
- Build/test result: `./gradlew assembleDebug testDebugUnitTest` → PASS
- Notes for reviewer/verifier:
  - No UI tests. Check on a device: body focus + keyboard on a new note, grid by default on a fresh install, layout survives a restart, search open/close/Back.
  - Two similar X icons appear in the search row when it has text (see decisions). Worth a look on a device.

## Round 2 (fixes from review/verify)
- Addressed findings: review R1 #1, search row now closes with a leading back arrow instead of a trailing X (4651b03).
- Commits:
