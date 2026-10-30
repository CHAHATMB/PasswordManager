# Codebase Audit (baseline, commit a328dad)

Paths below are relative to `app/src/main/java/com/example/passwordmanager/` unless noted.

## Tech stack
- AGP 8.2.2, Gradle wrapper 8.2, Kotlin 1.9.0, KSP `1.9.21-1.0.15` (mismatched with Kotlin 1.9.0), Compose compiler ext 1.5.1.
- Compose BOM 2023.08.00 (Material3); `material-icons-extended:1.6.1` pinned outside the BOM.
- Room 2.6.1 (runtime + ktx, compiler via KSP **and** a redundant `annotationProcessor`).
- Navigation Compose 2.7.7. Lifecycle runtime 2.7.0, `lifecycle-viewmodel-ktx:2.4.0` (older than runtime). activity-compose 1.8.2, appcompat 1.6.1.
- androidx.biometric 1.1.0. Gson **2.1** (2011, only used by dead `CryptographyManager`).
- compileSdk 34, targetSdk 34, minSdk 27. Java/JVM target 1.8. `isMinifyEnabled = false`.
- No DI framework. No version catalog (`gradle/libs.versions.toml` absent); versions inline in `app/build.gradle.kts` (some as local `val`s).
- Single module `:app`, root `build.gradle.kts` only declares plugins.

## Architecture as it exists
- Packages: `data/` (entity, DAO, DB), `presentation/` (VM, state, event, nav items, screens in `HomeScreen/`, `AddScreen/`, `components/`, and `FavoriteCredentialScreen.kt` loose in `presentation/`), `ui/theme/` (+ `shapes/`), `util/` (crypto, biometrics, empty helpers).
- No repository/domain layer: `CredentialViewModel` talks to `CredentialDao` directly.
- DB built in `MainActivity` via `by lazy Room.databaseBuilder(... "credential.db")` (MainActivity.kt:68-74) — no Application class / singleton.
- ViewModel created with `by viewModels` + anonymous `ViewModelProvider.Factory` with unchecked cast (MainActivity.kt:76-84). One VM shared by all screens; `state` and `viewModel::onEvent` passed down as params.
- State/event pattern (MVI-ish):
  - `CredentialEvent` sealed interface (presentation/CredentialEvent.kt): `SortCredentail` (typo), `DeleteCredential`, `SaveCredential`, `FavoriteCredential`, `OnSearchTextChange`, `OnToogleSearch` (typo). Data classes use `var`.
  - `CredentialState` is a plain **class** (not data class) holding `List<Credential>` plus Compose `MutableState<String>` form fields (website/username/password/note, plus unused `isFav`, `iv`) and two hand-written `copy` overloads (CredentialState.kt:17-23).
  - VM: `isSortedByDateCreate` → `flatMapLatest` DAO flow → `combine(_state, sort, credentials)` → `stateIn(WhileSubscribed(5000))`. UI collects with `collectAsState()` (not lifecycle-aware).
  - Form fields are written directly by the UI (`state.website.value = it`, AddCredentialScreen.kt:79,93,105,117), bypassing events.

## Data model
- `Credential` @Entity (data/Credential.kt), table `credential`: `id: Int` (PK autoGenerate), `website`, `username`, `password` (plaintext String), `dataCreate: Long` (typo for dateCreated), `var isFav: Boolean = false`, `note: String`. Commented-out `iv`/`ivBlob`/`passwordBlob` columns (lines 18-24).
- Custom `equals`/`hashCode` only compare id/username/password/isFav (lines 26-38).
- DAO (data/CredentialDao.kt): `upsertCredential` (@Upsert, suspend), `deleteCredential` (@Delete, suspend), `getOrderedByWebsite()` and `getOrderedByDateCreated()` (Flow), `getFavoriteCredential()` (Flow, `WHERE isFav = true`, unused).
- DB: `CredentialDatabase` version **1**, `abstract val dao`, no `exportSchema` config/schema dir, no migrations, no `fallbackToDestructiveMigration`. Any schema change (e.g. encrypted columns) needs a migration plan from v1.

## Screens and navigation
- Routes: `NavigationItem` sealed class (presentation/AppNavigation.kt) with `Home`, `Add`, `Favorite`; routes are `Screen` enum names. NavHost lives in a private composable inside `MainActivity` (MainActivity.kt:214-244), start = Home.
- Shell (MainActivity.kt:92-207): `Scaffold` with custom bottom bar `MyBottomAppBarWithFab` (NavigationBar with Home/Favorite + centered FAB, notched `BottomNavCustomShape`). Selected tab tracked by `selectedItemIndex` (rememberSaveable), not derived from back stack.
- FAB opens a `ModalBottomSheet` containing `AddCredentialScreen`. The `Add` route is registered but unreachable from UI.
- Home (`presentation/HomeScreen/CredentialScreen.kt`): title bar with lock toggle (biometric to reveal) and sort toggle (date ↔ website); `LazyColumn` of `CredentialItem` (website, username, password only when unlocked; buttons: "lock" icon, favorite toggle, delete).
- Add (`presentation/AddScreen/AddCredentialScreen.kt`): 4 `OutlinedTextField`s (website, username, password, note), button labelled "Added" → `SaveCredential` then navigate Home.
- Favorite (`presentation/FavoriteCredentialScreen.kt`): same list, filters in UI by `isFav`, always locked (passwords never shown), sort button.

## Encryption and biometrics (end to end)
- **Passwords are stored and displayed in plaintext.** No encryption on any code path. README.md claims AES-256 encryption and "no passwords stored in plain text" — false today.
- `util/CryptoManager.kt`: AES/CBC/PKCS7 with a **random in-memory key generated per instance** (lines 21-25), never persisted to Keystore (keyStore field unused). Instantiated in `CredentialViewModel.kt:21` and top-level `HomeScreen/CredentialScreen.kt:50`, but no method is called (calls commented out in VM:74-82 and CredentialScreen.kt:126).
- `util/CryptographyManager.kt`: Keystore AES/GCM with `setUserAuthenticationRequired(true)` — closer to correct design, but entirely unused (only an unused import at MainActivity.kt:63).
- Biometrics: `util/BiometricPromptsUtils.kt` (`BiometricPromptUtils` object) builds prompt + PromptInfo; used only by `showBiometricForAuthentication` (CredentialScreen.kt:167-188) to flip a local `lockState` that reveals all passwords on Home. No `CryptoObject`, no app lock on launch, no lock on background, errors only logged/println.
- Manifest: `USE_BIOMETRIC` permission; `allowBackup="true"` with template (empty) backup rules → plaintext DB is included in cloud/device backups.

## Tests
- `app/src/test/.../ExampleUnitTest.kt` (2+2) and `app/src/androidTest/.../ExampleInstrumentedTest.kt` (package name) — templates only.
- Test deps: `junit:4.13.2`; androidTest: androidx junit 1.1.5, espresso 3.5.1, compose ui-test-junit4. No coroutines-test, Turbine, Robolectric, room-testing, or mocking library.

## Bugs, smells, dead code
Bugs
- CredentialScreen.kt:131-139 — first item button shows a Lock icon but sends `DeleteCredential` (two delete buttons, one disguised).
- AddCredentialScreen.kt:140-153 + MainActivity.kt:188-197 — saving does not close the bottom sheet (`isSheetOpen` never reset); it navigates Home underneath.
- CredentialViewModel.kt:83-87 — `SaveCredential` ignores the event payload and reads `state.value` form fields instead; no validation (empty credentials can be saved).
- CredentialViewModel.kt:33-36 + CredentialState.kt:17-19 — every combine emission builds a new `CredentialState` via custom `copy(credentials)`, which recreates **fresh** form `MutableState`s; typed Add-form text is lost whenever the list/sort/state flow re-emits. `_state` content is effectively ignored except as a trigger.
- CredentialViewModel.kt:99-106 — `FavoriteCredential` mutates the entity in place (`var isFav`) and then writes a manually-refreshed, date-sorted list into `_state` that `combine` discards (dead). In-place mutation + partial `equals` risks stale UI.
- CredentialViewModel.kt:38-62 — search block is broken/dead: `_countriesList` is a `String`, `if (text.isBlank()) countries` result discarded; no UI consumes `isSearching`/`searchText`/`countriesList`.
- CredentialDao.kt:24 — `WHERE isFav = true`: `TRUE` literal needs SQLite 3.23+ (API 30+); minSdk 27. Use `= 1`. (Currently unused.)
- FavoriteCredentialScreen.kt:60-71 — iterates all credentials and emits empty items for non-favorites (spacing gaps, wasted work); should use DAO query or filtered list.
- CredentialScreen.kt:59 — `lockState` is local `remember`; lost on navigation/config change; reveals all passwords at once.
- MainActivity.kt:119-129 — tab selection not synced with back stack (system back desyncs highlight); no `saveState/restoreState`.
- CryptoManager.kt: `encryptCipher` is a getter creating a new cipher each access, so `encryptCipher.iv` after `doFinal` is a different IV (lines 71-74, 96-105, 130-133); `decryptString*` return `ByteArray.toString()` (119, 126); `encryptString5` ignores input (130); `encrypt` writes sizes as one byte (73, 75); `decryptString10` CBC decrypt without IV (151); hardcoded "secret" strings (142, 149); key not persisted.
- CryptographyManager.kt:57-74 — shadowed `cipher` means returned cipher is never initialized; 92 decrypts with hardcoded key "chahat"; 97 returns zero-filled wrapper on error; 101-109 debug method in interface.
- Many `println` of IVs/plaintext/errors in crypto and biometric code (sensitive logging).

Smells / dead code
- MainActivity.kt:94-107 unused `context`, `executor`, `lockState`; 204-206 no-op `LaunchedEffect { delay(5000) }`; 149-184, 291-307 commented blocks; unused imports (e.g. CryptographyManager).
- `util/Constant.kt` empty class; `util/UtilityFunction.kt` extension functions declared inside a class (unusable), unused.
- `presentation/components/TextFireldComponent.kt` (filename typo): `TextFieldAndLabel`, `SearchField` unused outside a public `shows()` preview; hardcoded `Color(0xFFEEF0F2)` duplicates `Neutral` in Color.kt.
- `res/font/dd.xml` empty font family. `CredentialState.isFav`/`iv` unused. VM `combine` param `isSortedByDateCreate` unused. `else -> {}` on sealed `when` (VM:119). `_state`/`state`/`dao` declared `var` and `_state` public (VM:19,32-33).
- `flatMapLatest` used without `@OptIn(ExperimentalCoroutinesApi)`; `BiometricManager.canAuthenticate()` no-arg deprecated (CredentialScreen.kt:171).
- `@RequiresApi(P)` on `onCreate`/screens while minSdk is 27; `@RequiresApi(M)` on CryptoManager — inconsistent and unnecessary.
- Nested `Scaffold`s (shell + each screen); inline `fontSize`/`fontWeight` everywhere instead of `MaterialTheme.typography`.
- Add button: `.background` before `.clip`, `Arrangement.End` on a non-full-width Row (no effect), label "Added". Password field not masked. `SaveCredential.note: String?` vs entity non-null.
- Biometric prompt strings are typos/placeholders ("Auntication for encryp and decrypt", negative button "Navigation button texts") (BiometricPromptsUtils.kt:43-47). No `strings.xml` usage — all UI text hardcoded.
- Build: KSP/Kotlin version mismatch; duplicate Room compiler config; ancient Gson; lifecycle version skew; icons artifact outside BOM.
- `gradlew` is committed without the exec bit (git mode 100644), so `./gradlew` fails; use `sh ./gradlew` or fix with `git update-index --chmod=+x gradlew` (not done).

## Conventions to follow
- Package root `com.example.passwordmanager`; layers `data/`, `presentation/`, `ui/theme/`, `util/`. Screen subpackages are PascalCase (`HomeScreen`, `AddScreen`) — keep consistent or agree on a rename up front.
- Naming: `*Screen` composables taking `(state, navController, onEvent)`; events named `CredentialEvent.<Verb><Noun>`; DAO methods `upsertX/deleteX/getOrderedByX`.
- New UI actions should go through `CredentialEvent` → `CredentialViewModel.onEvent`, with state exposed as `StateFlow<CredentialState>`. Prefer an immutable data-class state over the current `MutableState` fields when touching it.
- Coroutines: `viewModelScope.launch` for DAO writes; DAO reads as `Flow`.
- Theme: wrap in `PasswordManagerTheme` (dynamic color on by default for API 31+, so custom Purple palette only shows on <31). Use `MaterialTheme.colorScheme` roles (`primary` for titles, `secondary` for top-bar icons and bottom bar, `secondaryContainer`/`onSecondaryContainer` for list cards, `onPrimary` top-bar background).
- Typography: `Typography` overrides only `bodyLarge` (default font) and `labelMedium` (Poppins Medium). `poppinsFontFamily` (ui/theme/Type.kt:11-21) declares `Font()`s **without weights**, so `FontWeight.SemiBold` etc. do not map to the right TTF — fix by adding weights before relying on Poppins.
- Icons: `Icons.Rounded.*` in lists/top bars, Filled/Outlined pairs for nav items. Cards: `RoundedCornerShape(10.dp)`, 12dp padding, 16dp list spacing.

## Baseline build
- `./gradlew assembleDebug testDebugUnitTest` → `permission denied`: `gradlew` is committed as mode 100644. Ran `sh ./gradlew assembleDebug testDebugUnitTest` instead → **BUILD SUCCESSFUL** (2m39s first run, which also auto-installed SDK Platform 34). 1 unit test (ExampleUnitTest) passed.
- Warnings: Room "schema export directory not provided" (CredentialDatabase.kt:7); unchecked cast MainActivity.kt:80; unused vars MainActivity.kt:94,96,105; `!!` CredentialState.kt:18; missing ExperimentalCoroutinesApi opt-in CredentialViewModel.kt:24; unused expr VM:52; redundant else VM:119; unused `navController` FavoriteCredentialScreen.kt:31, CredentialScreen.kt:56; deprecated `Icons.Rounded.Sort` (use AutoMirrored) FavoriteCredentialScreen.kt:49, CredentialScreen.kt:94; deprecated `canAuthenticate()` CredentialScreen.kt:171; CryptoManager.kt:129,142,149 unused; shadowed `cipher` CryptographyManager.kt:63.
