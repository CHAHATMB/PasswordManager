# Verify – Design system and modern UI

- Build commit: 2d444fa (last code commit 61f9c85). Debug via `./gradlew installDebug`. Secure checks (rows 13–14) ran on the unmodified build. For the visual screenshots, FLAG_SECURE (activity) and `SecureFlagPolicy.SecureOn` (add sheet, generator sheet, backup dialogs, `ConfirmDialog`) were switched off in a local edit that was never committed, then restored with `git checkout`.
- Device: AVD `Pixel_9a` (sdk_gphone64_arm64, API 36), headless.
- Result (Round 1): **INCOMPLETE (blocked)**. Nothing failed in what was tested, but the run stopped partway (see Issues #1).
- **Final result (after Round 2): PASS**

## Scenarios
| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| 1 | Setup screen, light + dark | `pm clear`, system light → Setup. `cmd uimode night yes` → same screen | PASS: teal shield and button, outlined fields. Status bar icons visible in both modes | 01-setup-light.png, 02-setup-dark.png |
| 2 | Empty vault state, light + dark | Created vault `correcthorse1` → Home | PASS: "Your vault is empty / Tap + to save your first password.", M3 bar (Passwords, Favorites) + FAB | 03-home-empty-light.png, 04-home-empty-dark.png |
| 3 | Add sheet + strength meter + generator (light) | FAB → website/username, `weak` → "Strength: Weak" (red). Generate → sheet with Strong (green), length slider, switches. Use → field filled, Strong | PASS | 05-add-sheet-weak-light.png, 06-generator-sheet-light.png, 08-add-sheet-generated-light.png |
| 4 | Home list, ItemRow, More actions menu (light) | Added github.com/alice, example.com/bob, gitlab.com/erin (generated). Favourite github. More actions on bob → Show password / Delete. Show → `ex-pass-2` shown | PASS: monogram avatars, Copy + Favourite inline, no purple | 09-home-list-light.png, 10-home-row-menu-light.png, 11-home-list-revealed-light.png |
| 5 | Bar/FAB hidden while keyboard open | Tap search → `mInputShown=true`, no Passwords/Favorites/Add nodes. IME Done → `mInputShown=false`, bar and FAB back | PASS | 12-home-search-keyboard-light.png, 13-home-no-results-keyboard-light.png |
| 6 | Search with no hits → "No results" | Search `zzznomatch` | PASS: "No results / Nothing matches “zzznomatch”." | 13-…, 14-home-no-results-light.png |
| 7 | Favourites list + empty favourites (light) | Favorites tab → github.com only. Unfavourite → "No favourites yet". Re-favourited from Home | PASS | 15-favorites-light.png, 16-favorites-empty-light.png |
| 8 | Detail + Edit; bar hidden on Edit (light) | Tap github.com → Detail (back, favourite, edit, delete in top bar; copy/reveal). Edit → no Passwords/Favorites/Add nodes. Note `edited-note` → Save → shown on Detail | PASS | 17-detail-light.png, 18-edit-light.png, 19-detail-after-edit-light.png |
| 9 | Settings + backup dialogs (light) | Settings: Theme segmented (System/Light/Dark), auto-lock radios, Backup buttons. Export dialog, CSV warning, export password dialog, picker → SAVE → "Backup saved" (728 B file in Downloads) | PASS | 20-settings-light.png, 21–24-backup-*.png |
| 10 | Import backup (regression) | Import backup → `passwords-export.json` → `backup-pass-12` → Import | NOT RUN: the emulator froze here (Issue #1) | – |
| 11 | Dark mode on every screen; Settings → Theme Light/Dark/System applies immediately, survives force-stop, applies on Unlock; no white flash on cold start | – | NOT RUN (blocked) | – |
| 12 | Non-teal wallpaper keeps teal | – | NOT RUN (blocked). Code check only: no `dynamicColor`/`dynamicLightColorScheme` in `app/src/main` | – |
| 13 | FLAG_SECURE: generator sheet and backup dialogs | Unmodified build: generator sheet open → screencap all black. Export master-password dialog and CSV warning dialog open (confirmed by uiautomator) → screencap black except the system bars | PASS | secure-generator-sheet-black.png, secure-backup-dialog-black.png, secure-csv-warning-dialog-black.png |
| 14 | `Color(0x` / `.sp` outside `ui/theme` | `grep -rn "Color(0x" app/src/main/java --include='*.kt' \| grep -v ui/theme` and the same for `[0-9]\.sp` | PASS: both return nothing. Fonts left: Poppins regular/medium/semibold | – |
| 15 | Regression: relaunch + unlock, copy, delete + Undo, sort, "vault is empty" flash after unlock | – | NOT RUN (blocked) | – |

## Issues found
1. **Blocker for verification (environment, not app):** the emulator froze during the backup import. Repro: Settings → Import backup → pick `passwords-export.json` → enter `backup-pass-12` → Import. After that, `uiautomator dump`, `screencap`, `dumpsys window`, `adb shell echo` and `adb emu avd status` all hung with no output. `qemu-system-aarch64` (PID 34709) stayed at 0 % CPU for more than 3 minutes, and `adb devices` still listed `emulator-5554 device`. Restarting the emulator (kill + cold boot) was denied by the tool permission policy, so the run stopped there. The VM froze completely at 0 % host CPU, so the app is an unlikely cause. But it froze while Argon2 was running the import, so when the run continues, repeat row 10 and watch for it. Rows 10–12 and 15 still need to run, plus the dark-mode screenshots of the add/generator/list/favourites/detail/edit/settings/backup screens.
2. Low (probably pre-existing, out of scope): switching system night mode while the add sheet or generator sheet is open recreates the Activity, and the sheet closes, so the typed input is lost. Repro: Home → FAB → type a website → Generate → `adb shell cmd uimode night yes` → back on Home with no sheet. The sheet's open state is not saved across a configuration change.
- Harness notes, not app bugs: Gboard runs in its collapsed floating-toolbar mode on this AVD. With it, `KEYCODE_ESCAPE` doesn't hide the IME, and Back with the IME open leaves the app instead of only closing the keyboard. The toolbar's Done key closes it normally. On the multi-line Note field, Done added a trailing newline to `edited-note`.

## Round 2
- Build commit: 915df11 (app code unchanged since 61f9c85). The emulator (`Pixel_9a`, API 36) was restarted by the user, and the app data survived the restart (vault and 3 credentials still there). Same temporary FLAG_SECURE/`SecureOff` local edit for screenshots, reverted with `git checkout` before the commit. The unmodified build was reinstalled at the end.
- Logcat captured from before the first import to the end of the run (~36k lines): 0 `FATAL EXCEPTION`, 0 `ANR in`, 0 hits for `correcthorse`, `backup-pass`, `gh-secret`, `ex-pass` or `edited-note`.

| # | Acceptance criterion | Steps | Result | Screenshot |
|---|---|---|---|---|
| R1 | Import backup (Round 1 freeze) | Settings → Import backup → `passwords-export.json` → `backup-pass-12` → Import, run twice with logcat running. `adb shell echo` + screencap every 1.5 s during the import all returned within 1 s. Result "Imported: 0, Skipped as duplicate: 3, Invalid: 0" in about 2 s both times | PASS. The freeze did not reproduce. It was the emulator: in Round 1 the guest load average was ~100 (`uptime`: 100.21 / 89.29) with qemu stalled, and it recovered by itself about 10 minutes later | 28-backup-import-result-light.png |
| R2 | No "vault is empty" flash after unlock | `screenrecord` around tapping Unlock (3 credentials). Frames: Unlock → spinner → Home with an empty body (top bar + search only) → list | PASS: no frame shows "Your vault is empty" | 26-unlock-light.png, 27-unlock-transition-frames-no-empty-flash.png |
| R3 | Relaunch → unlock → credentials present | After the emulator restart and after force-stop: unlock with `correcthorse1` → github.com, example.com, gitlab.com listed; github still a favourite, note `edited-note` kept | PASS | 27-…, 32-home-list-dark.png |
| R4 | Theme setting switches immediately | Settings (system light) → Dark → whole app dark at once, `theme_mode=DARK` in `settings.xml`. Light → light. System + `uimode night yes` → dark | PASS | 29-settings-dark.png, 41-settings-theme-light-selected.png, 42-settings-system-follows-dark.png |
| R5 | Theme persists after force-stop and applies on Unlock | `theme_mode=DARK`, `am force-stop`, relaunch → Unlock screen dark | PASS | 40-unlock-dark-after-relaunch.png |
| R6 | Dark mode: main screens readable, status bar icons visible | In-app Dark: Settings, export dialog, CSV warning, Home list, row menu, Favorites, Detail, Edit (no bar/FAB nodes), Add sheet (Weak in red), Generator (Strong in green), No results | PASS: teal accents, no purple, status bar icons light on dark | 29–39-*-dark.png |
| R7 | No white flash on cold start in dark | System dark + app System: `screenrecord` of a cold start → launcher → dark splash → dark Unlock, no white frame | PASS | 43-cold-start-system-dark-frames-no-white-flash.png |
| R7b | Cold start, app Dark + system Light (developer note) | Same with system light → the splash window is light before the dark Unlock screen | As documented in dev-log (window colour follows the system); not a criterion | 44-cold-start-app-dark-system-light-frames.png |
| R8 | Copy | Copy password on github.com, paste (`KEYCODE_PASTE`) into search → `gh-secret-1` | PASS | 45-copy-toast-dark.png |
| R9 | Delete + Undo via More actions | More actions → Delete on github.com → "Deleted github.com / Undo" → Undo → row back, still a favourite, note kept | PASS | 46-delete-undo-snackbar-dark.png, 47-after-undo-dark.png |
| R10 | Sort | Sort toggle → list re-ordered by website (example.com, github.com) | PASS | – |
| R11 | Non-teal wallpaper keeps teal | The AVD's wallpaper is blue/white, and system UI uses blue/lavender dynamic colours (Gboard permission dialog). The app stays teal in every screenshot. A different wallpaper was not set: no clean adb path on this image | PASS (with the existing blue wallpaper) | 44-…, all app screenshots |

Notes:
- Two earlier Delete attempts (gitlab.com, example.com) ran past the 4 s snackbar before my harness tapped Undo (each `uiautomator dump` takes 2–3 s), so those two rows are permanently deleted. Undo itself works (R9). This is the harness, not a regression.
- The floating Gboard toolbar sometimes covers row actions. One tap hit its microphone key and opened a Gboard record-audio prompt, which I dismissed with "Don't allow".

### Issues (final)
- Issue #1 (Round 1 freeze): **resolved, environment**. It did not reproduce in two imports with logcat running; no crash or ANR. The cause was an overloaded or stalled emulator, not the app.
- Issue #2 (low, probably pre-existing, out of scope): a system night-mode change while the add or generator sheet is open recreates the Activity and closes the sheet, so the typed input is lost.
