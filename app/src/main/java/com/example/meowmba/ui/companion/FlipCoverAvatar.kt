package com.example.meowmba.ui.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** SM-F7xx identifies the Flip family, not the Fold's tall outer display.
 * Check both display and window dimensions so ordinary split-screen windows
 * on the main display do not accidentally enter the cover layout. */
internal fun isFlipCoverWindow(
    manufacturer: String, model: String, displayId: Int, widthDp: Int, heightDp: Int,
): Boolean = manufacturer.equals("samsung", ignoreCase = true) &&
    model.startsWith("SM-F7", ignoreCase = true) && displayId != 0 &&
    widthDp in 1..600 && heightDp in 1..600

/** Reuses the existing avatar/motion runtime. Shared approval and other
 * overlays remain in CompanionScreen above this compact base surface. */
@Composable
internal fun FlipCoverAvatar(
    state: CompanionState,
    onMicClick: () -> Unit,
    avatar: @Composable () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (state.isMicEnabled) "Meow · Microphone enabled" else "Meow · Microphone off",
                style = MaterialTheme.typography.labelSmall,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { avatar() }
            state.captionText?.takeIf { it.isNotBlank() }?.let { answer ->
                Text(
                    answer,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 64.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onMicClick) { Text("Microphone") }
        }
    }
}

@Composable
internal fun FlipCoverSetupCard() {
    if (!android.os.Build.MANUFACTURER.equals("samsung", true) ||
        !android.os.Build.MODEL.startsWith("SM-F7", true)) return
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showHelp = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Set up cover-screen avatar")
    }
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text("Meow on your cover screen") },
        text = { Column {
            Text("1. Install Samsung Good Lock and its MultiStar module.\n\n" +
                "2. In MultiStar, open I love Galaxy Foldable, then Launcher widget or Enable Launcher, and select Meow.\n\n" +
                "3. Add the launcher widget to your cover screen, then open Meow from it. Your selected avatar will fit the smaller display.\n\n" +
                "Availability and labels depend on your Flip model and One UI version.")
            if (error) Text("No browser is available. Open Good Lock from the Galaxy Store.")
        } },
        confirmButton = { TextButton(onClick = {
            error = runCatching { context.startActivity(android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.samsung.com/sg/support/mobile-devices/explore-new-cover-screen-widgets-on-the-galaxy-z-flip5/")
            )) }.isFailure
        }) { Text("Samsung setup guide") } },
        dismissButton = { TextButton(onClick = { showHelp = false }) { Text("Done") } },
    )
}
