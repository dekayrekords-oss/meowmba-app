package com.example.meowmba.ui.companion

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.computer.TermuxComputerLinks
import com.example.meowmba.core.computer.TermuxSetupSupport

@Composable
internal fun rememberTermuxSetupSupport(): State<TermuxSetupSupport> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(TermuxSetupSupport.detect(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state.value = TermuxSetupSupport.detect(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}

/** Same edition guidance in onboarding and Computer; never suggest replacing an installed APK. */
@Composable
internal fun TermuxInstallGuidance(support: TermuxSetupSupport, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var checks by remember { mutableIntStateOf(0) }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { error = "Could not open the download page. Choose a browser and retry." }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(when (support) {
            TermuxSetupSupport.MISSING -> "Get the compatible Termux edition"
            TermuxSetupSupport.AUTOMATIC -> "Termux detected · automatic setup supported"
            TermuxSetupSupport.GUIDED -> "Termux detected · guided setup needed"
        }, style = MaterialTheme.typography.titleMedium)
        if (support == TermuxSetupSupport.MISSING) {
            Text(TermuxComputerLinks.INSTALL_GUIDANCE)
            Button(onClick = { open(TermuxComputerLinks.TERMUX_FDROID) }, modifier = Modifier.fillMaxWidth()) { Text("Get Termux from F-Droid (recommended)") }
            TextButton(onClick = { open(TermuxComputerLinks.TERMUX_GITHUB_RELEASES) }) { Text("Alternative: official GitHub releases") }
            Text("On GitHub, use the apt-android-7 universal APK under Assets. Do not mix Termux or add-on APKs from different sources. Google Play's separate edition does not support this automatic connection on all versions.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(TermuxComputerLinks.EXISTING_GUIDANCE)
            Text(if (support == TermuxSetupSupport.GUIDED) "Continue to guided setup. No command-permission prompt is needed for this edition."
                else "Continue to setup and approve Termux command access when asked. Installing Termux alone does not mean Ubuntu or OpenCode is connected.")
        }
        OutlinedButton(onClick = { onRefresh(); checks++ }, modifier = Modifier.testTag("termux_check_installation")) { Text("Check installation") }
        // Checking used to redraw the same screen, so a missing Termux looked like a dead button.
        if (checks > 0) Text(termuxCheckResult(support, checks), color = if (support == TermuxSetupSupport.MISSING)
            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("termux_check_result"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

internal fun termuxCheckResult(support: TermuxSetupSupport, checks: Int): String {
    val again = if (checks > 1) " (checked again)" else ""
    return when (support) {
        TermuxSetupSupport.MISSING -> "Termux not found$again. Install it, open it once, then check again."
        TermuxSetupSupport.AUTOMATIC -> "Termux found$again. Tap Continue."
        TermuxSetupSupport.GUIDED -> "Termux found$again (guided setup edition). Tap Continue."
    }
}
