package com.example.meowmba.ui.companion

import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Surface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.meowmba.ui.theme.AlertDialog
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
    model.startsWith("SM-F7", ignoreCase = true) && displayId == 1 &&
    widthDp in 1..600 && heightDp in 1..600

/** Opt-in for compact cover candidates. Samsung display 1 is verified on the
 * Flip 6 and carries FLAG_PRESENTATION; generic candidates exclude that flag.
 * Tall/main-display cover screens already use the standard avatar-first layout. */
internal fun useCoverAvatarLayout(
    enabled: Boolean, displayId: Int, coverCandidate: Boolean, widthDp: Int, heightDp: Int,
): Boolean = enabled && coverCandidate && displayId != 0 && widthDp in 1..600 && heightDp in 1..600

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
internal fun FlipCoverSetupCard(showOnAllDevices: Boolean = false) {
    val samsungFlip = android.os.Build.MANUFACTURER.equals("samsung", true) &&
        android.os.Build.MODEL.startsWith("SM-F7", true)
    if (!showOnAllDevices && !samsungFlip) return
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
            Text(if (samsungFlip) "1. Turn on Cover-screen avatar in Settings → Phone & device.\n\n" +
                "2. Install Samsung Good Lock and its MultiStar module if your One UI version requires them.\n\n" +
                "3. In MultiStar, open I love Galaxy Foldable, then Launcher widget or Enable Launcher, and select this Meow app (Meow Assistant Preview for test builds).\n\n" +
                "4. Add the launcher widget to your cover screen, then open Meow from it.\n\n" +
                "Availability and labels depend on your Flip model and One UI version. This does not replace the system cover home or lock screen."
            else "Enable Meow in your manufacturer's external-display or continue-apps settings, if available, then open it on the cover screen. Compact built-in secondary screens use the cover layout; tall cover screens retain the standard avatar view.\n\nNot every foldable allows third-party cover apps. This toggle cannot unlock that support or replace the system lock screen. It does not enable the microphone, camera or always-on display.")
            if (error) Text("No browser is available. Open Good Lock from the Galaxy Store.")
        } },
        confirmButton = { if (samsungFlip) TextButton(onClick = {
            error = runCatching { context.startActivity(android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.samsung.com/sg/support/mobile-devices/explore-new-cover-screen-widgets-on-the-galaxy-z-flip5/")
            )) }.isFailure
        }) { Text("Samsung setup guide") } },
        dismissButton = { TextButton(onClick = { showHelp = false }) { Text("Done") } },
    )
}
