package com.example.passwordmanager

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.presentation.AddScreen.AddCredentialScreen
import com.example.passwordmanager.presentation.DetailScreen.CredentialDetailScreen
import com.example.passwordmanager.presentation.HomeScreen.CredentialScreen
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.CredentialRoutes
import com.example.passwordmanager.presentation.CredentialState
import com.example.passwordmanager.presentation.CredentialViewModel
import com.example.passwordmanager.presentation.FavoriteCredentialScreen
import com.example.passwordmanager.presentation.LockScreen.LockViewModel
import com.example.passwordmanager.presentation.LockScreen.SetupScreen
import com.example.passwordmanager.presentation.LockScreen.UnlockScreen
import com.example.passwordmanager.presentation.NavigationItem
import com.example.passwordmanager.presentation.SettingsScreen.SettingsScreen
import com.example.passwordmanager.presentation.SettingsScreen.SettingsViewModel
import com.example.passwordmanager.ui.theme.PasswordManagerTheme
import com.example.passwordmanager.ui.theme.shapes.BottomNavCustomShape
import com.example.passwordmanager.util.BiometricPromptUtils
import javax.crypto.Cipher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val lockViewModel by viewModels<LockViewModel> { LockViewModel.Factory }
    private val viewModel by viewModels<CredentialViewModel> { CredentialViewModel.Factory }
    private val settingsViewModel by viewModels<SettingsViewModel> { SettingsViewModel.Factory }

    private lateinit var biometricPrompt: BiometricPrompt
    // Not saved across recreation: after a rotation mid-prompt the result is simply ignored.
    private var isEnablingBiometric = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        // Created in onCreate so the prompt reconnects to this callback after a configuration change.
        biometricPrompt = BiometricPromptUtils.createBiometricPrompt(this, ::onBiometricAuthenticated)
        setContent {
            PasswordManagerTheme {
                val vaultState by lockViewModel.vaultState.collectAsState()
                val lockState by lockViewModel.state.collectAsState()
                // Each branch owns its own composition, so a state change drops the whole
                // navigation back stack and sheet state of the previous branch.
                when (vaultState) {
                    VaultState.NotSetUp -> SetupScreen(state = lockState, onSetup = lockViewModel::setup)
                    VaultState.Locked -> {
                        LaunchedEffect(Unit) {
                            // Waits for RESUMED: after a screen-off lock the prompt appears once the user is back.
                            lifecycle.withResumed { lockViewModel.takeBiometricUnlockCipher()?.let(::showUnlockPrompt) }
                        }
                        UnlockScreen(state = lockState, onUnlock = lockViewModel::unlock)
                    }
                    is VaultState.Unlocked -> VaultShell()
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
        var isSheetOpen by remember {
            mutableStateOf(false)
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        val state by viewModel.state.collectAsState()
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val snackbarHostState = remember { SnackbarHostState() }
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
        Scaffold(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // Hidden on Edit: with adjustResize the bar and FAB would ride above the keyboard.
                if (currentRoute != CredentialRoutes.EDIT) MyBottomAppBarWithFab(
                    content = {
                        NavigationBar {
                            listOf(NavigationItem.Home, NavigationItem.Favorite).forEach { item ->
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
                                        if( isSelected ){
                                            Icon(imageVector = item.selectedIcon, contentDescription=null)
                                        } else {
                                            Icon(imageVector = item.unSelectedIcon, contentDescription=null)
                                        }
                                    })
                            }
                        }
                    },
                    floatingActionButtonContent = {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    },
                    modifier = Modifier.navigationBarsPadding(),
                    onFabClick = { isSheetOpen = true }
                )
            },
        ) {

            if( isSheetOpen )
                ModalBottomSheet(
                sheetState = sheetState,
                onDismissRequest = {
                    isSheetOpen = false
                },
                properties = ModalBottomSheetDefaults.properties(securePolicy = SecureFlagPolicy.SecureOn),

            ){
                AddCredentialScreen(
                    onEvent = viewModel::onEvent,
                    onSaved = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { isSheetOpen = false }
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
                CredentialScreen(
                    state = state,
                    searchText = searchText,
                    navController = navController,
                    onEvent = viewModel::onEvent,
                    onLock = lockViewModel::lock
                )
            }
            composable(NavigationItem.Add.route) {
                AddCredentialScreen(
                    onEvent = viewModel::onEvent,
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(NavigationItem.Favorite.route) {
                val favorites by viewModel.favorites.collectAsState()
                FavoriteCredentialScreen(
                    state = favorites,
                    navController = navController,
                    onEvent = viewModel::onEvent
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
                    onAutoLockTimeoutChange = settingsViewModel::setAutoLockTimeout,
                    onBiometricToggle = { enabled ->
                        if (enabled) showEnablePrompt() else settingsViewModel.disableBiometric()
                    }
                )
            }
        }
    }
}

@Composable
fun MyBottomAppBarWithFab(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    floatingActionButtonContent: @Composable () -> Unit,
    onFabClick: ()-> Unit
) {
    Box(modifier = modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp)
        .background(Color.Transparent)) {
        FloatingActionButton(
            onClick = { onFabClick() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-32).dp)
                .padding(bottom = 16.dp), // Adjust padding as needed
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 16.dp
            ),
            content = floatingActionButtonContent
        )
        Box(
            modifier = Modifier
                .graphicsLayer {
                    shadowElevation = 8.dp.toPx()
                    shape = BottomNavCustomShape(100f)
                    clip = true
                }
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .offset(y = (-4.dp)) // Match FAB elevation
                .fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.secondary) // Transparent background
        ){
            content()
        }
    }
}
