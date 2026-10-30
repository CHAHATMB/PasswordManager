# Review – Credential management

## Round 1
- Commit range reviewed: 8762249..d8adffb
- Verdict: APPROVED

### Findings
| # | Severity (blocker/major/minor) | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| 1 | minor | presentation/LockScreen/LockScreenLayout.kt:29-34 | `verticalScroll` gives the Column an unbounded max height, so `Arrangement.spacedBy(16.dp, Alignment.CenterVertically)` has no spare space to centre in. The Setup and Unlock content is now top-aligned instead of centred. | Wrap the content in `BoxWithConstraints` and give the scrolling Column `heightIn(min = maxHeight)`, or have the verifier accept top alignment. |
| 2 | minor | MainActivity.kt:154, presentation/CredentialViewModel.kt:59 | A delete that is buffered in the channel while the shell is not collecting (a lock right after Delete) shows Undo after the next unlock. Double-tapping Delete on detail sends two deletes and two Snackbars. Both are documented and harmless. | Optional: debounce the detail Delete, for example by calling `close()` first and ignoring the click once the entry is no longer resumed. |
| 3 | minor | util/SecureClipboard.kt:63-71,93 | If the process dies within 30 s (likely while the user is pasting in another app under memory pressure), the timer is lost and the password stays on the clipboard. On API 33+ the system clears it after about an hour; below 33 it stays indefinitely. Treating an unreadable clip as ours can also wipe another app's newer clip. Both are documented trade-offs of the approach in the brief. | No action this round. Consider a WorkManager or AlarmManager fallback later if this matters. |
| 4 | minor | presentation/LockScreen/LockViewModel.kt:104 | The comment says "the next lock retries". The retry actually happens on the next biometric attempt or launch, when the prompt is due again. | Reword the comment to "the next biometric attempt". |
| 5 | minor | presentation/CredentialEvent.kt:8,12,21 | New events use `val`, but the touched `DeleteCredential`, `SaveCredential` and `FavoriteCredential` still use `var` fields. The event classes are now inconsistent. | Make the event fields `val` while this file is being changed. |
| 6 | minor | test/.../CredentialRepositoryTest.kt `favouriteToggleLeavesTheOriginalUnchanged...` | `assertFalse(original.isFav)` and `assertNotEquals(original, toggled)` are guaranteed by `val` and the data class. Only the favourites-query part of the test checks real behaviour. | Optional: drop the two trivial assertions. |
| 7 | minor | commits 8b183e5, f488631..4b14c72 | 8b183e5 (search) also adds `getFavorites` and `getById`, which are first used two and four commits later. From f488631 to 4b14c72, Detail's Edit button navigates to `EDIT/{id}`, which is not registered until 1082357, so tapping it crashes at those commits. HEAD is correct, and history must not be rewritten. | No action. Keep each commit's added API and UI wired within that commit. |

Notes (no action):
- Search: the LIKE escaping is correct (`ESCAPE '\'`, and the order of `\`, `%`, `_` is right). SQLite LIKE ignores case for ASCII only, so non-ASCII letters are matched case-sensitively. That is fine for websites and usernames.
- Clipboard: the sensitive flag is set on 33+ and through the literal key on 27–32. `clearPrimaryClip()` is used on 28+ and an empty clip on 27. The timer runs on `applicationScope`, not the ViewModel scope. `clearNow()` touches only a pending copy, so a cold-start `Locked` never clears the user's clip. A newer copy cancels the old job. Writing the clipboard from the background is allowed on 10+.
- Lock: the shell, the NavHost and the edit-form state are disposed on lock, so decrypted values on the Detail and Edit screens do not survive it. The nav saved state holds only the id. The Snackbar shows only the website.
- Edit keeps `id`, `dataCreate` and `isFav` through `editing.copy(...)`, and the plain `remember` keeps the password out of the saved-state Bundle. Restore upserts the identical entity.
- There are no `Log` or `println` calls in `app/src/main`. Test output prints the clipboard state without its text.
- All six carried-over items are addressed.
- Per-commit compilation was not re-run here (only the dev-log's claim). HEAD builds.

### Checklist
- [x] Correctness / no obvious bugs (minors only)
- [x] Simple, no over-engineering, matches codebase style
- [x] Security (no plaintext secrets, no logging of secrets, key handling)
- [x] Critical business logic has unit tests; no needless tests (DAO search, edit and restore, the cancellation mapping, and the clipboard timer and ownership checks, all with virtual time)
- [x] Commit messages follow convention and are concise (all subjects 72 characters or fewer, no bodies; see finding 7)
- [x] Build + unit tests pass (`./gradlew assembleDebug testDebugUnitTest`: 81 tests, 0 failures)
