# Notes redesign

Status: IN_REVIEW
Backlog ID: 14

## Goal
More room for writing and a lighter Notes tab: the editor has no separate title field (the title is a borderless line at the top that fills itself from the body and can be edited in place), notes show as text-only cards in a grid (default) or list, and search hides behind a top-bar icon.

## Scope
- In:
  - Editor: one borderless title line plus a borderless body that fills the screen. A new note's title follows the body live (`deriveNoteTitle`) until the user types in the title.
  - List rows lose the letter avatar. They become text-only note cards.
  - Top-bar actions on the Notes tab: search (magnifier) and a grid/list toggle. Grid is the default. The choice is stored in `SettingsStore`.
  - Search field only shown after tapping the magnifier, below the top bar. Closing it clears the query. The backend query is unchanged.
- Out:
  - Any change to `deriveNoteTitle`, the DB, the DAO/repository, backup, or the 11 save/discard/delete/Undo/lock rules (except the live title rule below).
  - Pinning, colours, sorting, multi-select, swipe-to-delete, delete from the list.
  - Changes to `ItemRow`/`Monogram` for credentials and the authenticator (they keep their avatars).

## Acceptance criteria
- [ ] Editor shows no outlined "Title"/"Note" fields and no "New note"/"Edit note" heading. A borderless title line (placeholder "Title") sits above a borderless body (placeholder "Note") that takes the remaining height. Back, Save (check) and the Delete overflow work as before.
- [ ] New note: the body gets focus and the keyboard opens. Typing `Wifi router admin` shows `Wifi router admin · <today, d MMM yyyy>` in the title line as real text while typing. Saving keeps that title.
- [ ] Tapping the title line lets the user edit it in place. After that, body edits no longer change it. If the title is cleared to empty, it follows the body again, and a blank title on save is still derived.
- [ ] Opening an existing note: its stored title is shown and is never changed by body edits (same as 11).
- [ ] A new note with an empty body and an untouched title is discarded on Back, as before.
- [ ] Notes tab opens in grid view on a fresh install: 2 columns on a phone in portrait, staggered cards with the title (bold, up to 2 lines), up to 8 preview lines and the relative updated date in the bottom-right. No avatar anywhere.
- [ ] The top-bar toggle switches to list view: full-width cards with the same layout and up to 2 preview lines. The toggle icon shows the layout you would switch to, and its content description says so.
- [ ] The chosen view survives tab switches, lock/unlock and an app restart.
- [ ] No search field is visible by default. Tapping the magnifier shows the search field below the top bar with focus and the keyboard. Results filter as before (title/body, case-insensitive, literal `%`/`_`, "No results" state). The close (X)/Back action hides the field and the full list comes back.
- [ ] Lock while the editor or search is open: nothing visible while locked, and no note text remains after unlock (same as 11).

## Design notes
- Affected files/layers:
  - `presentation/Notes/NotesState.kt`: `NoteEditor` gains `isTitleAuto: Boolean`, which is true for a new note and false for an opened one. The editor events get the title rule (see below). Add `NotesLayout` here or in settings (see below).
  - `presentation/Notes/NotesViewModel.kt`:
    - `ChangeBody`: when `isTitleAuto`, set `title = if (body.isBlank()) "" else autoTitle(body)`.
    - `ChangeTitle`: set `isTitleAuto = title.isEmpty()`.
    - The VM takes `SettingsStore` (factory from `container.settingsStore`) and exposes `layout: StateFlow<NotesLayout>`, plus an event such as `NotesEvent.ChangeLayout(layout)`.
    - `toNoteToSave` is unchanged. Because the auto title stays `""` while the body is blank, the discard rule still holds.
  - `presentation/Notes/NoteEditorScreen.kt`:
    - Replace both `AppTextField`s with M3 `TextField`s that have transparent container and indicator colours.
    - The title is `singleLine`, uses `typography.titleLarge`, and `ImeAction.Next` moves focus to the body.
    - The body keeps the 90 000+ counter as supporting text.
    - Request focus on the body for a new note only (`FocusRequester` in a `LaunchedEffect`).
    - The top bar title is `""`.
    - Drop the `autoTitle` parameter. The placeholder is now a static "Title".
  - `presentation/Notes/NotesScreen.kt`:
    - Top bar actions: a search icon (`Icons.Rounded.Search`) and the layout toggle (`Icons.Rounded.ViewAgenda` for "List view", `Icons.Rounded.GridView` for "Grid view"). They live in the right-hand `actions` slot. That slot is the M3 convention, and `AppTopBar` already provides it.
    - The search row is shown under `AppTopBar` with `SearchField` and a close `IconButton` only while `isSearchOpen`. Keep that flag in `rememberSaveable`, initialised to `searchText.isNotEmpty()`. Closing sends `OnSearchTextChange("")`. A `BackHandler(enabled = isSearchOpen)` closes it. Opening requests focus.
    - Grid: `LazyVerticalStaggeredGrid(StaggeredGridCells.Adaptive(160.dp))`. List: the existing `LazyColumn`. Both use the same new private `NoteCard`.
    - `NoteCard` is a `Surface(onClick, shapes.medium, surfaceContainerLow)` like `ItemRow`. It shows the title, the preview, and the date right-aligned at the bottom. It takes a `previewMaxLines` parameter: 8 in the grid, 2 in the list.
    - The preview keeps its line breaks (trim it, and collapse 3+ newlines to 2) so grid cards read like notes. Keep the existing `DateUtils` relative date.
    - Empty and "No results" states are unchanged.
  - `data/settings/SettingsStore.kt`: add `enum class NotesLayout { GRID, LIST; DEFAULT = GRID }` with `notesLayout: StateFlow` and `setNotesLayout()`, using the same pattern as `themeMode` (key `notes_layout`, unknown value falls back to the default).
  - `MainActivity.kt`: collect `notesViewModel.layout` and pass it to `NotesScreen`. Remove `autoTitle = …` from `NoteEditorScreen`.
  - Orphans to remove: `ItemRow.subtitleMaxLines` (only Notes used it), and `AppTextField`'s `placeholder`/`supportingText` if nothing else uses them after the editor change. `ItemRow.footer` stays because the authenticator uses it.
- Decisions:
  - **Avatar replaced with nothing.** Text-only cards: a letter of a free-text title carries no information (unlike a website initial), and the space goes to the title and preview. The cards keep their surface colour, so they still read as separate items.
  - **Staggered grid.** Preview lengths vary a lot. A fixed grid would either cut long notes or leave short cards half empty. Adaptive 160dp gives 2 columns on phones and more in landscape or on tablets, with no extra code.
  - **Live auto title.** The title is the real derived text, not a placeholder, so the user can tap it and edit it in place, as the requirement asks. It is derived from the first non-blank character, with no "few words" threshold: it is simpler, and `deriveNoteTitle` already handles short text.
  - **Clearing the title resumes auto-tracking.** This is the same rule as on save, so there is no hidden state.
  - **Existing notes never re-derive** while their title is non-empty. This keeps the guarantee from 11.
  - The search-open flag is UI state, not VM state. The query itself stays in the VM, as before.
- Data model / migration changes: none. There is one new `SharedPreferences` key in the existing `settings` file.
- Security considerations:
  - The layout preference is non-sensitive and plain `SharedPreferences` (D-settings). It must not contain note data.
  - `isTitleAuto` lives in `NoteEditor`, which is already cleared on lock. `rememberSaveable` holds only a boolean, never the query or note text.
  - FLAG_SECURE and the lock behaviour from 11 are unchanged.

## Critical logic needing unit tests
- `NotesViewModelTest`, auto title:
  - A new note's title follows body changes, and a blank body gives an empty title.
  - After `ChangeTitle`, body edits keep the title. Clearing the title resumes tracking.
  - An opened existing note never re-derives.
  - A new note with an empty body is still discarded.
  - Update any existing cases that relied on a blank title until save.
- `SettingsStoreTest`: `notesLayout` defaults to GRID, round-trips both values, and an unknown stored value falls back to GRID. Add these to the existing class, matching the `themeMode` cases.
- No UI tests.

## Dependencies
- 11 (Notes tab, `deriveNoteTitle`, `NotesViewModel`), 07 (tokens, `AppTopBar`, `SearchField`), 02 (`SettingsStore`). No overlap with 12/13 files except `MainActivity.kt` (different routes).
