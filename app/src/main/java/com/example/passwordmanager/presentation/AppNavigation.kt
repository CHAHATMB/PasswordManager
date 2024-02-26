package com.example.passwordmanager.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen {
    HOME,
    ADD,
    FAVORITE
}
sealed class NavigationItem(val route: String, val selectedIcon: ImageVector, val unSelectedIcon: ImageVector, val title: String ) {
    object Home : NavigationItem(Screen.HOME.name, Icons.Filled.Home, Icons.Outlined.Home, "Home")
    object Add: NavigationItem(Screen.ADD.name, Icons.Filled.Add, Icons.Outlined.Add, "Add")
    object Favorite : NavigationItem(Screen.FAVORITE.name, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "Favorite")
}