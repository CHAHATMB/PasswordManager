package com.example.passwordmanager

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.example.passwordmanager.presentation.AddScreen.AddCredentialScreen
import com.example.passwordmanager.presentation.HomeScreen.CredentialScreen
import com.example.passwordmanager.presentation.CredentialState
import com.example.passwordmanager.presentation.CredentialViewModel
import com.example.passwordmanager.presentation.FavoriteCredentialScreen
import com.example.passwordmanager.presentation.NavigationItem
import com.example.passwordmanager.ui.theme.PasswordManagerTheme
import com.example.passwordmanager.ui.theme.shapes.BottomNavCustomShape
import com.example.passwordmanager.util.CryptographyManager
import kotlinx.coroutines.delay

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


    @OptIn(ExperimentalMaterial3Api::class)
    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PasswordManagerTheme {
                val context = LocalContext.current
                val activity = LocalContext.current as FragmentActivity
                val executor = ContextCompat.getMainExecutor(activity)
                var isSheetOpen by rememberSaveable {
                    mutableStateOf(false)
                }
                val sheetState = rememberModalBottomSheetState()

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
                    .background(MaterialTheme.colorScheme.background),
                    bottomBar = {
                        MyBottomAppBarWithFab(
                            content = {
                                NavigationBar {
                                    listOf(NavigationItem.Home, NavigationItem.Favorite).forEachIndexed { index, item ->
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
                            },
                            floatingActionButtonContent = {
                                Icon(Icons.Filled.Add, contentDescription = "Add")
                            },
                            modifier = Modifier.navigationBarsPadding(),
                            onFabClick = { isSheetOpen = true }
                        )
                        // region :: nav block
//                    NavigationBar {
//                        listOf(NavigationItem.Home,NavigationItem.Add, NavigationItem.Favorite).forEachIndexed { index, item ->
//                            NavigationBarItem(
//                                selected = selectedItemIndex == index,
//                                onClick = {
//                                    selectedItemIndex = index
//                                    navController.navigate(item.route){
//                                        popUpTo(navController.graph.findStartDestination().id){
//                                            this@navigate.launchSingleTop = true
//                                        }
//                                    }
//                                },
//                                label = {
//                                    Text(text = item.title)
//                                },
//                                icon = {
//                                    if( selectedItemIndex == index ){
//                                        Icon(imageVector = item.selectedIcon, contentDescription=null)
//                                    } else {
//                                        Icon(imageVector = item.unSelectedIcon, contentDescription=null)
//                                    }
//                                })
//                        }
//                        }
//                    },
//                    floatingActionButton = {
//                        FloatingActionButton(
//                            onClick = { /* Handle FAB click */ },
//                            content = {
//
//
//                                Icon(Icons.Filled.Add, contentDescription = "Add") // Replace with your icon
//                            }
//                        )
                        // endregion :: nav block
                    },
                ) {

                    if( isSheetOpen )
                        ModalBottomSheet(
                        sheetState = sheetState,
                        onDismissRequest = {
                            isSheetOpen = false
                        },

                    ){
                        AddCredentialScreen(state = state, navController = navController, onEvent = viewModel::onEvent)
                    }

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

    @RequiresApi(Build.VERSION_CODES.P)
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
//                .height(56.dp)
//                .size(width = ButtonDefaults.MinWidth, height = 56.dp) // Adjust size as needed
                .background(color = MaterialTheme.colorScheme.secondary) // Transparent background
        ){
            content()
        }
    }
}

// region ::previe_code
//@Composable
//@Preview(showBackground = true)
//fun show(){
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(100.dp)
//            .graphicsLayer {
//                shadowElevation = 8.dp.toPx()
//                shape = BottomNavCustomShape(100f)
//                clip = true
//            }
//            .background(color = Color.Black) // Transparent background
//    )
//}
// endregion ::previe_code