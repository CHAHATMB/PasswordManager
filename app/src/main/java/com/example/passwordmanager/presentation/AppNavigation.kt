package com.example.passwordmanager.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen {
    HOME,
    ADD,
    SETTINGS,
    DETAIL,
    EDIT,
    AUTHENTICATOR,
    TOTP_EXPORT,
    NOTES,
    NOTE_EDITOR
}
sealed class NavigationItem(val route: String, val selectedIcon: ImageVector, val unSelectedIcon: ImageVector, val title: String ) {
    object Home : NavigationItem(Screen.HOME.name, Icons.Filled.Key, Icons.Outlined.Key, "Passwords")
    object Add: NavigationItem(Screen.ADD.name, Icons.Filled.Add, Icons.Outlined.Add, "Add")
    object Settings : NavigationItem(Screen.SETTINGS.name, Icons.Filled.Settings, Icons.Outlined.Settings, "Settings")
    object Authenticator : NavigationItem(Screen.AUTHENTICATOR.name, Icons.Filled.Shield, Icons.Outlined.Shield, "Authenticator")
    object Notes : NavigationItem(Screen.NOTES.name, Icons.AutoMirrored.Filled.StickyNote2, Icons.AutoMirrored.Outlined.StickyNote2, "Notes")
}

object CredentialRoutes {
    const val ID_ARG = "credentialId"
    val DETAIL = "${Screen.DETAIL.name}/{$ID_ARG}"
    val EDIT = "${Screen.EDIT.name}/{$ID_ARG}"

    fun detail(id: Int) = "${Screen.DETAIL.name}/$id"
    fun edit(id: Int) = "${Screen.EDIT.name}/$id"
}

object AuthenticatorRoutes {
    val EXPORT = Screen.TOTP_EXPORT.name
}

object NoteRoutes {
    const val ID_ARG = "noteId"
    const val NEW_NOTE_ID = -1
    val EDITOR = "${Screen.NOTE_EDITOR.name}/{$ID_ARG}"

    fun editor(id: Int) = "${Screen.NOTE_EDITOR.name}/$id"
}
