package com.example.passwordmanager.presentation.HomeScreen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.example.passwordmanager.presentation.CredentialEvent
import com.example.passwordmanager.presentation.CredentialState
import com.example.passwordmanager.util.BiometricPromptUtils
import com.example.passwordmanager.util.CryptoManager

val cryptoManager = CryptoManager()

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun CredentialScreen(
    state: CredentialState,
    navController: NavController,
    onEvent: (CredentialEvent)->Unit
) {
    var lockState by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current as FragmentActivity
    Scaffold (
        topBar = {
            Row (modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(MaterialTheme.colorScheme.onPrimary)
                .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ){
                Text(text = "Password Manager",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = {
                    if(lockState){
                        lockState = !lockState
                    } else {
                        showBiometricForAuthentication(context) {
                            lockState = !lockState
                        }
                    }
                }) {

                    if( !lockState ){
                        Icon(imageVector = Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(35.dp), tint = MaterialTheme.colorScheme.secondary)
                    } else {
                        Icon(imageVector = Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(35.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                IconButton(onClick = { onEvent(CredentialEvent.SortCredentail) }) {
                    Icon(imageVector = Icons.Rounded.Sort, contentDescription = null, modifier = Modifier.size(35.dp), tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    ){
        LazyColumn(contentPadding = it,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement =  Arrangement.spacedBy(16.dp)
        ){
            items(state.credentials.size){
                CredentialItem( state=state, index=it, lockState,onEvent = onEvent)
            }
        }
    }
}

@Composable
fun CredentialItem(state: CredentialState, index: Int, lockState: Boolean, onEvent: (CredentialEvent) -> Unit) {
    Row(modifier= Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .padding(12.dp)
    ){
        Column(modifier=Modifier.weight(1f)){
            Text(text = state.credentials.get(index = index).website, fontSize=18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier= Modifier.height(8.dp))
            Text(text = state.credentials.get(index = index).username, fontSize=18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier= Modifier.height(8.dp))
            if(lockState){
//                val password = cryptoManager.decryptString5(iv = state.credentials.get(index=index).ivBlob, encryptedBytes = state.credentials.get(index = index).passwordBlob)
                Text(text = state.credentials.get(index=index).password, fontSize=18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Row(){
            IconButton(onClick = { onEvent(
                CredentialEvent.DeleteCredential(
                    state.credentials.get(
                        index = index
                    )
                )
            )}) {
                Icon(imageVector = Icons.Rounded.Lock, contentDescription=null, modifier = Modifier.size(24.dp))
            }
                IconButton(onClick = {
                    onEvent(CredentialEvent.FavoriteCredential(state.credentials.get(index = index)))
                }) {
                    if( state.credentials.get(index = index).isFav ) {
                        Icon(imageVector = Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(imageVector = Icons.Rounded.FavoriteBorder, contentDescription=null, modifier = Modifier.size(24.dp))
                    }
                }

            IconButton(onClick = { onEvent(
                CredentialEvent.DeleteCredential(
                    state.credentials.get(
                        index = index
                    )
                )
            )}) {
                Icon(imageVector = Icons.Rounded.Delete, contentDescription=null, modifier = Modifier.size(24.dp))
            }
        }

    }
}


// BIOMETRICS SECTION
@RequiresApi(Build.VERSION_CODES.P)
private fun showBiometricForAuthentication(activity: FragmentActivity, onSucess: ()->Unit) {

    lateinit var biometricPrompt: BiometricPrompt
    val canAuthenticate = BiometricManager.from(activity).canAuthenticate()
    if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
        biometricPrompt =
            BiometricPromptUtils.createBiometricPrompt(
                activity
            ){
                println("biometric success ${it.authenticationType}")
                onSucess()
            }
        val promptInfo = BiometricPromptUtils.createPromptInfo()
        biometricPrompt.authenticate(promptInfo)

    } else {
        println("Sorry cannot use authentication")
//            activity.toast("failed")
    }

}