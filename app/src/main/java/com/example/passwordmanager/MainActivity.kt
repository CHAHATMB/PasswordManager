package com.example.passwordmanager

import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.withResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.passwordmanager.data.settings.ThemeMode
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.presentation.Authenticator.AuthenticatorEvent
import com.example.passwordmanager.presentation.Authenticator.AuthenticatorScreen
import com.example.passwordmanager.presentation.Authenticator.AuthenticatorViewModel
import com.example.passwordmanager.presentation.Authenticator.TotpExportScreen
import com.example.passwordmanager.presentation.Authenticator.TotpExportViewModel
import com.example.passwordmanager.presentation.AuthenticatorRoutes
import com.example.passwordmanager.presentation.AddScreen.AddCredentialScreen
import com.example.passwordmanager.presentation.DetailScreen.CredentialDetailScreen
import com.example.passwordmanager.presentation.HomeScreen.CredentialScreen
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.CredentialRoutes
import com.example.passwordmanager.presentation.CredentialState
import com.example.passwordmanager.presentation.CredentialViewModel
import com.example.passwordmanager.presentation.LockScreen.LockViewModel
import com.example.passwordmanager.presentation.LockScreen.SetupScreen
import com.example.passwordmanager.presentation.LockScreen.UnlockScreen
import com.example.passwordmanager.presentation.NavigationItem
import com.example.passwordmanager.presentation.NoteRoutes
import com.example.passwordmanager.presentation.Onboarding.OnboardingScreen
import com.example.passwordmanager.presentation.Notes.NoteEditorScreen
import com.example.passwordmanager.presentation.Notes.NotesEvent
import com.example.passwordmanager.presentation.Notes.NotesScreen
import com.example.passwordmanager.presentation.Notes.NotesViewModel
import com.example.passwordmanager.presentation.SettingsScreen.BACKUP_FILE_NAME
import com.example.passwordmanager.presentation.SettingsScreen.BACKUP_MIME_TYPE
import com.example.passwordmanager.presentation.SettingsScreen.BackupEvent
import com.example.passwordmanager.presentation.SettingsScreen.BackupPicker
import com.example.passwordmanager.presentation.SettingsScreen.IMPORT_MIME_TYPES
import com.example.passwordmanager.presentation.SettingsScreen.SettingsScreen
import com.example.passwordmanager.presentation.SettingsScreen.SettingsViewModel
import com.example.passwordmanager.presentation.components.rememberIsKeyboardOpen
import com.example.passwordmanager.ui.theme.AppTheme
import com.example.passwordmanager.util.BiometricPromptUtils
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import javax.crypto.Cipher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val QR_IMAGE_MIME_TYPE = "image/*"
private val FULL_SCREEN_ROUTES = setOf(CredentialRoutes.EDIT, AuthenticatorRoutes.EXPORT, NoteRoutes.EDITOR)
private val FAB_ROUTES = setOf(NavigationItem.Home.route, NavigationItem.Authenticator.route, NavigationItem.Notes.route)

class MainActivity : FragmentActivity() {

    private val lockViewModel by viewModels<LockViewModel> { LockViewModel.Factory }
    private val viewModel by viewModels<CredentialViewModel> { CredentialViewModel.Factory }
    private val settingsViewModel by viewModels<SettingsViewModel> { SettingsViewModel.Factory }
    private val authenticatorViewModel by viewModels<AuthenticatorViewModel> { AuthenticatorViewModel.Factory }
    private val notesViewModel by viewModels<NotesViewModel> { NotesViewModel.Factory }

    private val autoLockObserver get() = (application as PasswordManagerApp).container.autoLockObserver
    private val settingsStore get() = (application as PasswordManagerApp).container.settingsStore
    private val loadPassphraseWords get() = (application as PasswordManagerApp).container::loadPassphraseWords

    // Registered on the activity, not in the vault's composition, so a result still arrives after a
    // lock during the trip (an export then deletes the empty file the picker created).
    private val saveBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) {
        onBackupPicked(BackupEvent.SaveBackup(it))
    }
    private val openBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        onBackupPicked(BackupEvent.OpenBackup(it))
    }
    private val openCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        onBackupPicked(BackupEvent.ImportCsv(it))
    }
    private val scanQrCodeLauncher = registerForActivityResult(ScanContract()) { result ->
        val alternative = result.originalIntent?.getStringExtra(EXTRA_SCANNER_ALTERNATIVE)
            ?.let { name -> ScannerAlternative.entries.find { it.name == name } }
        when (alternative) {
            // The vault may have locked while the scanner was open; don't open the picker over the lock screen.
            ScannerAlternative.Gallery -> if (lockViewModel.vaultState.value is VaultState.Unlocked) openImagePicker()
            ScannerAlternative.ManualEntry -> authenticatorViewModel.onEvent(AuthenticatorEvent.OpenManualEntry)
            null -> {
                val isCameraPermissionMissing = result.originalIntent?.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION) == true
                authenticatorViewModel.onEvent(AuthenticatorEvent.QrScanned(result.contents, isCameraPermissionMissing))
            }
        }
    }
    private val scanImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        autoLockObserver.endAppTrip()
        authenticatorViewModel.onEvent(AuthenticatorEvent.ImagePicked(it))
    }

    private lateinit var biometricPrompt: BiometricPrompt
    // Not saved across recreation: after a rotation mid-prompt the result is simply ignored.
    private var isEnablingBiometric = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        // Created in onCreate so the prompt reconnects to this callback after a configuration change.
        biometricPrompt = BiometricPromptUtils.createBiometricPrompt(this, ::onBiometricAuthenticated)
        setContent {
            val themeMode by settingsStore.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(darkTheme = darkTheme) {
                val vaultState by lockViewModel.vaultState.collectAsState()
                val lockState by lockViewModel.state.collectAsState()
                // Each branch owns its own composition, so a state change drops the whole
                // navigation back stack and sheet state of the previous branch.
                when (vaultState) {
                    VaultState.NotSetUp -> {
                        var showOnboarding by rememberSaveable { mutableStateOf(!settingsStore.isOnboardingDone) }
                        if (showOnboarding) {
                            OnboardingScreen(onFinish = {
                                settingsStore.isOnboardingDone = true
                                showOnboarding = false
                            })
                        } else {
                            val context = LocalContext.current
                            val isBiometricAvailable = remember { BiometricPromptUtils.canAuthenticate(context) }
                            SetupScreen(state = lockState, isBiometricAvailable = isBiometricAvailable, onSetup = lockViewModel::setup)
                        }
                    }
                    VaultState.Locked -> {
                        LaunchedEffect(Unit) {
                            // Waits for RESUMED: after a screen-off lock the prompt appears once the user is back.
                            lifecycle.withResumed { lockViewModel.takeBiometricUnlockCipher()?.let(::showUnlockPrompt) }
                        }
                        UnlockScreen(state = lockState, onUnlock = lockViewModel::unlock)
                    }
                    is VaultState.Unlocked -> {
                        LaunchedEffect(Unit) {
                            lifecycle.withResumed { if (lockViewModel.takeBiometricEnrolmentRequest()) showEnablePrompt() }
                        }
                        VaultShell()
                    }
                }
            }
        }
    }

    private fun showUnlockPrompt(cipher: Cipher) {
        isEnablingBiometric = false
        biometricPrompt.authenticate(BiometricPromptUtils.unlockPromptInfo(), BiometricPrompt.CryptoObject(cipher))
    }

    private fun showEnablePrompt() {
        val cipher = settingsViewModel.newBiometricCipher() ?: return
        isEnablingBiometric = true
        biometricPrompt.authenticate(BiometricPromptUtils.enablePromptInfo(), BiometricPrompt.CryptoObject(cipher))
    }

    private fun openBackupPicker(picker: BackupPicker) {
        autoLockObserver.expectAppTrip()
        try {
            when (picker) {
                BackupPicker.SaveBackup -> saveBackupLauncher.launch(BACKUP_FILE_NAME)
                BackupPicker.OpenBackup -> openBackupLauncher.launch(IMPORT_MIME_TYPES)
                BackupPicker.OpenCsv -> openCsvLauncher.launch(IMPORT_MIME_TYPES)
            }
        } catch (e: ActivityNotFoundException) {
            autoLockObserver.endAppTrip()
            settingsViewModel.onBackupEvent(BackupEvent.Dismiss)
        }
    }

    // The scanner runs in this process, so it is not an app trip for the auto-lock.
    private fun openQrScanner() {
        val options = ScanOptions()
            .setCaptureActivity(SecureCaptureActivity::class.java)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setBeepEnabled(false)
            .setPrompt("Scan the QR code shown by the website")
            // A denied permission comes back as a result, and the Authenticator tab explains it.
            .addExtra(Intents.Scan.SHOW_MISSING_CAMERA_PERMISSION_DIALOG, false)
        scanQrCodeLauncher.launch(options)
    }

    private fun openImagePicker() {
        autoLockObserver.expectAppTrip()
        try {
            scanImageLauncher.launch(arrayOf(QR_IMAGE_MIME_TYPE))
        } catch (e: ActivityNotFoundException) {
            autoLockObserver.endAppTrip()
        }
    }

    private fun onBackupPicked(event: BackupEvent) {
        autoLockObserver.endAppTrip()
        settingsViewModel.onBackupEvent(event)
    }

    private fun onBiometricAuthenticated(cipher: Cipher) {
        val vaultState = lockViewModel.vaultState.value
        if (isEnablingBiometric && vaultState is VaultState.Unlocked) {
            settingsViewModel.enableBiometric(cipher)
        } else if (!isEnablingBiometric && vaultState == VaultState.Locked) {
            lockViewModel.unlockWithBiometric(cipher)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun VaultShell() {
        val addDraft = viewModel.addDraft
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        val state by viewModel.state.collectAsState()
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val snackbarHostState = remember { SnackbarHostState() }
        val isKeyboardOpen = rememberIsKeyboardOpen()
        LaunchedEffect(Unit) {
            // collectLatest: a newer delete replaces the pending Undo instead of queueing behind it.
            viewModel.deletedCredentials.collectLatest { credential ->
                val result = snackbarHostState.showSnackbar(
                    message = "Deleted ${credential.website}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.onEvent(CredentialEvent.RestoreCredential(credential))
                }
            }
        }
        LaunchedEffect(Unit) {
            notesViewModel.deletedNotes.collectLatest { note ->
                val result = snackbarHostState.showSnackbar(
                    message = "Deleted ${note.title}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    notesViewModel.onEvent(NotesEvent.RestoreNote(note))
                }
            }
        }
        // Hidden on the full-screen forms and while typing (e.g. Home search): with adjustResize the bar and
        // FAB would ride above the keyboard.
        val isBottomBarVisible = currentRoute !in FULL_SCREEN_ROUTES && !isKeyboardOpen
        val isFabVisible = isBottomBarVisible && currentRoute in FAB_ROUTES
        Scaffold(modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (isBottomBarVisible) NavigationBar {
                    listOf(NavigationItem.Home, NavigationItem.Authenticator, NavigationItem.Notes).forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.route){
                                    popUpTo(navController.graph.findStartDestination().id){
                                        this@navigate.launchSingleTop = true
                                    }
                                }
                            },
                            label = {
                                Text(text = item.title)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unSelectedIcon,
                                    contentDescription = null
                                )
                            })
                    }
                }
            },
            floatingActionButton = {
                if (isFabVisible) FloatingActionButton(onClick = {
                    when (currentRoute) {
                        NavigationItem.Authenticator.route -> openQrScanner()
                        NavigationItem.Notes.route -> navController.navigate(NoteRoutes.editor(NoteRoutes.NEW_NOTE_ID)) { launchSingleTop = true }
                        else -> viewModel.onEvent(CredentialEvent.OpenAddSheet)
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            },
        ) {

            if( addDraft != null )
                ModalBottomSheet(
                sheetState = sheetState,
                onDismissRequest = {
                    viewModel.onEvent(CredentialEvent.CloseAddSheet)
                },
                properties = ModalBottomSheetDefaults.properties(securePolicy = SecureFlagPolicy.SecureOn),

            ){
                AddCredentialScreen(
                    draft = addDraft,
                    onDraftChange = { viewModel.onEvent(CredentialEvent.ChangeAddDraft(it)) },
                    onEvent = viewModel::onEvent,
                    loadPassphraseWords = loadPassphraseWords,
                    onSaved = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { viewModel.onEvent(CredentialEvent.CloseAddSheet) }
                    }
                )
            }

           Box(modifier = Modifier.padding(it)){
               AppNavigation(navController, state)
           }
        }
    }

    @Composable
    private fun AppNavigation(
        navController: NavHostController,
        state: CredentialState
    ) {
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Home.route
        ) {
            composable(NavigationItem.Home.route) {
                val searchText by viewModel.searchText.collectAsState()
                val favoritesOnly by viewModel.favoritesOnly.collectAsState()
                val sort by viewModel.sort.collectAsState()
                CredentialScreen(
                    state = state,
                    searchText = searchText,
                    favoritesOnly = favoritesOnly,
                    sort = sort,
                    navController = navController,
                    onEvent = viewModel::onEvent,
                    onLock = lockViewModel::lock
                )
            }
            composable(NavigationItem.Add.route) {
                AddCredentialScreen(
                    onEvent = viewModel::onEvent,
                    loadPassphraseWords = loadPassphraseWords,
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(NavigationItem.Authenticator.route) {
                val authenticatorState by authenticatorViewModel.state.collectAsState()
                AuthenticatorScreen(
                    state = authenticatorState,
                    dialog = authenticatorViewModel.dialog,
                    onEvent = authenticatorViewModel::onEvent,
                    onScanQrCode = ::openQrScanner,
                    onScanImage = ::openImagePicker,
                    onExport = { navController.navigate(AuthenticatorRoutes.EXPORT) }
                )
            }
            composable(NavigationItem.Notes.route) {
                val notesState by notesViewModel.state.collectAsState()
                val searchText by notesViewModel.searchText.collectAsState()
                val notesLayout by notesViewModel.layout.collectAsState()
                NotesScreen(
                    state = notesState,
                    searchText = searchText,
                    layout = notesLayout,
                    onEvent = notesViewModel::onEvent,
                    onOpen = { id -> navController.navigate(NoteRoutes.editor(id)) }
                )
            }
            composable(
                NoteRoutes.EDITOR,
                arguments = listOf(navArgument(NoteRoutes.ID_ARG) { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt(NoteRoutes.ID_ARG) ?: return@composable
                // Like Detail: a second tap on Back, Save or Delete lands after this entry is gone; ignore it.
                val isCurrent = { navController.currentBackStackEntry?.id == entry.id }
                val close = { navController.popBackStack(NoteRoutes.EDITOR, inclusive = true) }
                LaunchedEffect(id) { if (!notesViewModel.openEditor(id) && isCurrent()) close() }
                notesViewModel.editor?.let { editor ->
                    NoteEditorScreen(
                        editor = editor,
                        onEvent = notesViewModel::onEvent,
                        onClose = {
                            if (isCurrent()) {
                                notesViewModel.onEvent(NotesEvent.CloseEditor)
                                close()
                            }
                        },
                        onDelete = {
                            if (isCurrent()) {
                                notesViewModel.onEvent(NotesEvent.DeleteNote)
                                close()
                            }
                        }
                    )
                }
            }
            composable(AuthenticatorRoutes.EXPORT) { entry ->
                // Scoped to this entry, so leaving the screen discards the accounts and QR bitmaps.
                val exportViewModel = viewModel<TotpExportViewModel>(viewModelStoreOwner = entry, factory = TotpExportViewModel.Factory)
                val step by exportViewModel.state.collectAsState()
                TotpExportScreen(
                    step = step,
                    onEvent = exportViewModel::onEvent,
                    onClose = {
                        if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
                    }
                )
            }
            composable(
                CredentialRoutes.DETAIL,
                arguments = listOf(navArgument(CredentialRoutes.ID_ARG) { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt(CredentialRoutes.ID_ARG) ?: return@composable
                val credential by remember(id) { viewModel.credential(id) }.collectAsState(initial = null)
                // A second tap on Back, Edit or Delete lands after this entry is no longer current;
                // ignore it so it neither pops Home, opens Edit twice, nor deletes (and offers Undo) twice.
                val isCurrent = { navController.currentBackStackEntry?.id == entry.id }
                val close = { navController.popBackStack(CredentialRoutes.DETAIL, inclusive = true) }
                credential?.let {
                    CredentialDetailScreen(
                        credential = it,
                        onEvent = viewModel::onEvent,
                        onBack = { if (isCurrent()) close() },
                        onEdit = { if (isCurrent()) navController.navigate(CredentialRoutes.edit(id)) },
                        onDelete = {
                            if (isCurrent()) {
                                close()
                                viewModel.onEvent(CredentialEvent.DeleteCredential(it))
                            }
                        }
                    )
                }
            }
            composable(
                CredentialRoutes.EDIT,
                arguments = listOf(navArgument(CredentialRoutes.ID_ARG) { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt(CredentialRoutes.ID_ARG) ?: return@composable
                val credential by remember(id) { viewModel.credential(id) }.collectAsState(initial = null)
                credential?.let {
                    AddCredentialScreen(
                        onEvent = viewModel::onEvent,
                        loadPassphraseWords = loadPassphraseWords,
                        onSaved = { navController.popBackStack(CredentialRoutes.EDIT, inclusive = true) },
                        editing = it
                    )
                }
            }
            composable(NavigationItem.Settings.route) {
                LaunchedEffect(Unit) { settingsViewModel.refresh() }
                val settingsState by settingsViewModel.state.collectAsState()
                val context = LocalContext.current
                val isBiometricAvailable = remember { BiometricPromptUtils.canAuthenticate(context) }
                SettingsScreen(
                    state = settingsState,
                    isBiometricAvailable = isBiometricAvailable,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onAutoLockTimeoutChange = settingsViewModel::setAutoLockTimeout,
                    onBiometricToggle = { enabled ->
                        if (enabled) showEnablePrompt() else settingsViewModel.disableBiometric()
                    },
                    onBackupEvent = settingsViewModel::onBackupEvent,
                    onPickBackupFile = ::openBackupPicker
                )
            }
        }
    }
}
