package com.example.passwordmanager

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.passwordmanager.data.CredentialDatabase
import com.example.passwordmanager.presentation.AddCredentialScreen
import com.example.passwordmanager.presentation.CredentialScreen
import com.example.passwordmanager.presentation.CredentialState
import com.example.passwordmanager.presentation.CredentialViewModel
import com.example.passwordmanager.presentation.FavoriteCredentialScreen
import com.example.passwordmanager.presentation.NavigationItem
import com.example.passwordmanager.ui.theme.PasswordManagerTheme
import com.example.passwordmanager.util.BiometricPromptUtils
import com.example.passwordmanager.util.CryptographyManager
import kotlinx.coroutines.delay
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            CredentialDatabase::class.java,
            "credential.db"
        ).build()
    }

    private val viewModel by viewModels<CredentialViewModel>(
        factoryProducer = {
            object: ViewModelProvider.Factory{
                override fun <T: ViewModel> create(modelClass: Class<T>): T{
                    return CredentialViewModel(database.dao) as T
                }
            }
        }
    )


    // BIOMETRICS SECTION
    @RequiresApi(Build.VERSION_CODES.P)
    private fun showBiometricPromptForDecryption(activity: FragmentActivity) {
            biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(
                   activity
                ){
                    println("biometric success")
                }
            val promptInfo = BiometricPromptUtils.createPromptInfo()
            biometricPrompt.authenticate(promptInfo)
    }

    private lateinit var biometricPrompt: BiometricPrompt


    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val canAuthenticate = BiometricManager.from(applicationContext).canAuthenticate()
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPromptForDecryption(this)

        } else {
            println("Sorry cannot use authentication")
        }

        setContent {
            PasswordManagerTheme {
                val context = LocalContext.current
                val activity = LocalContext.current as FragmentActivity
                val executor = ContextCompat.getMainExecutor(activity)


                var selectedItemIndex by rememberSaveable {
                    mutableStateOf(0)
                }
                var lockState by remember {
                    mutableStateOf(false)
                }

                val state by viewModel.state.collectAsState()
                val navController = rememberNavController()
                // A surface container using the 'background' color from the theme
                Scaffold(modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),bottomBar = {
                    NavigationBar {
                        listOf(NavigationItem.Home,NavigationItem.Add, NavigationItem.Favorite).forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = selectedItemIndex == index,
                                onClick = {
                                    selectedItemIndex = index
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
                                    if( selectedItemIndex == index ){
                                        Icon(imageVector = item.selectedIcon, contentDescription=null)
                                    } else {
                                        Icon(imageVector = item.unSelectedIcon, contentDescription=null)
                                    }
                                })
                        }
                    }
                }) {
                   Box(modifier = Modifier.padding(it)){
                       AppNavigation(navController, state)
                   }
                }
                
                LaunchedEffect(Unit){
                    delay(5000)
                }
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
                CredentialScreen(
                    state = state,
                    navController = navController,
                    onEvent = viewModel::onEvent
                )
            }
            composable(NavigationItem.Add.route) {
                AddCredentialScreen(
                    state = state,
                    navController = navController,
                    onEvent = viewModel::onEvent
                )
            }
            composable(NavigationItem.Favorite.route) {
                FavoriteCredentialScreen(
                    state = state,
                    navController = navController,
                    onEvent = viewModel::onEvent
                )
            }
        }
    }
}

@Composable
fun App(){

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PasswordManagerTheme {
    }
}
