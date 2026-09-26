package com.example.meowmba.ui.onboarding

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun HomeLauncherSetup(onContinue: () -> Unit) {
    val context = LocalContext.current
    val role = remember { context.getSystemService(RoleManager::class.java) }
    var selected by remember { mutableStateOf(role.isRoleHeld(RoleManager.ROLE_HOME)) }
    var error by remember { mutableStateOf(false) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        selected = role.isRoleHeld(RoleManager.ROLE_HOME)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) selected = role.isRoleHeld(RoleManager.ROLE_HOME)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Make Meow your home screen?", style = MaterialTheme.typography.headlineMedium)
        Text(if (selected) "Meow is already your home-screen launcher." else
            "Optional: replace your current launcher with Meow's avatar, app drawer and shortcuts. Android will ask you to choose Meow. Your apps and data stay on the phone.")
        Text("You can switch back anytime in Android Settings → Apps → Default apps → Home app.")
        if (!selected) Button(onClick = {
            error = runCatching {
                chooser.launch(if (role.isRoleAvailable(RoleManager.ROLE_HOME))
                    role.createRequestRoleIntent(RoleManager.ROLE_HOME) else Intent(Settings.ACTION_HOME_SETTINGS))
            }.isFailure
        }) { Text("Set up home screen") }
        if (error) Text("Open Android Settings → Apps → Default apps → Home app to choose Meow.")
        OutlinedButton(onClick = onContinue) { Text(if (selected) "Continue" else "Not now") }
    }
}
