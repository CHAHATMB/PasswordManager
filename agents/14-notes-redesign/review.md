# Review – Notes redesign

## Round 1
- Commit range reviewed: 4a93f64..ad46d02 (71bace3..ad46d02)
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | presentation/Notes/NotesScreen.kt (search row) | With text in the field there are two adjacent X icons: `SearchField`'s Clear and the row's Close. They look identical but do different things. | Replace the trailing Close `IconButton` with a leading `Icons.AutoMirrored.Rounded.ArrowBack` ("Close search") before the field. This is the M3 expanded-search pattern (back arrow on the left, clear X on the right) and matches the editor's Back. `SearchField` stays unchanged. |
| 2 | minor | presentation/Notes/NoteEditorScreen.kt (`LaunchedEffect(Unit)`) | On rotation the effect runs again and moves focus to the body, even if the user was typing in the title of a new note. | Optional: keep the focus request, or key it with `rememberSaveable { mutableStateOf(false) }` so it only runs once. A device check is enough. |
| 3 | minor | presentation/Notes/NotesViewModel.kt (lock collector) | The search query survives lock and unlock. Because `isSearchOpen` starts as `searchText.isNotEmpty()`, the row comes back open with the old query. This is the same as in 11 and is not note text, so it is out of scope. | Optional follow-up: clear `_searchText` in the lock branch next to `editor = null`. |
| 4 | minor | test/.../NotesViewModelTest.kt `openedNoteTitleDoesNotFollowTheBody` | Largely overlaps `derivedTitleOfASavedNoteIsNotReDerivedWhenTheBodyChanges` and `typedTitleIsKept…`. The `isTitleAuto == false` assertion is the only new check. | Fine to keep. No action needed. |

Checked and OK:
- The auto title follows the body only while `isTitleAuto`. A blank body gives `""`, so the discard rule holds. A typed title stops tracking. Clearing it resumes tracking, and the title is still derived on save. An opened note starts with `isTitleAuto = false`. `toNoteToSave`, delete, Undo and lock clearing are unchanged.
- Closing search (Close or Back) clears the query. `rememberSaveable` holds only a boolean.
- `notesLayout` uses the `themeMode` pattern. An unknown value falls back to GRID. It holds no note data.
- Orphans are removed (`subtitleMaxLines`, `AppTextField.placeholder`/`supportingText`). `autoTitle` is now private.

### Checklist
- [x] Correctness / no obvious bugs
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests
- [x] Commit messages follow convention and are concise
- [x] Build + unit tests pass (`testDebugUnitTest --tests '*Notes*' --tests '*SettingsStore*'`)
