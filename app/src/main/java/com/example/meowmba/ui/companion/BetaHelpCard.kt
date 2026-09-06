package com.example.meowmba.ui.companion

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meowmba.BuildConfig

/** Only user-reviewed drafts: no automatic telemetry, uploads, or email sending. */
object BetaHelp {
    const val EMAIL = "dekayrekords@gmail.com"
    const val OPT_IN = "https://play.google.com/apps/testing/com.meow.meowmba"
    val invitation = """
        Help test Meow Assistant for Android (Android 12 or newer).
        1. Email $EMAIL with the Google Play email you want added to the closed test.
        2. After access is confirmed, sign in with that same account and join: $OPT_IN
        3. Install from Google Play, try the app, and stay opted in for at least 14 days.
        Please send honest feedback, including anything confusing or broken. No public rating required.
        This is an early beta. Voice/AI models may require large downloads; cloud features may require your own API key.
    """.trimIndent()

    fun feedback(version: String, androidVersion: String, device: String) = """
        Meow Assistant beta feedback
        App: $version
        Android: $androidVersion
        Device: $device

        What I was trying to do:

        Steps to reproduce:

        Expected result:

        What actually happened:

        How often (once / sometimes / always):

        Please remove anything private before sending. No keys, chat history, files, or logs are attached automatically.
    """.trimIndent()
}

@Composable
fun BetaHelpCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    fun launch(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "No compatible app found. Email ${BetaHelp.EMAIL} for help.", Toast.LENGTH_LONG).show()
        }
    }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide beta help & feedback" else "Beta help & feedback") }
            if (expanded) {
                Text("Your first five minutes", style = MaterialTheme.typography.titleSmall)
                Text("1. Tap the background to see tiles.\n2. Open Chat and try ‘Open Chrome’.\n3. Open Files and import a non-sensitive test photo.\n4. Try voice after its download finishes.\n5. Tell us what was confusing or did not work.", style = MaterialTheme.typography.bodyMedium)
                Text("Downloads stuck? Keep Wi-Fi on, check free storage, then retry in Hardware or Voice recognition. App taps not working? Check Android Accessibility settings. You can still use text and app-opening commands without Accessibility.", style = MaterialTheme.typography.bodySmall)
                Text("Closed testing: join with your approved Google Play account and remain opted in for at least 14 days. Joining the email list alone is not the same as opting in.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    val body = BetaHelp.feedback("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", android.os.Build.VERSION.RELEASE, android.os.Build.MODEL)
                    val uri = Uri.parse("mailto:${BetaHelp.EMAIL}?subject=" +
                        Uri.encode("Meow Assistant beta feedback") + "&body=" + Uri.encode(body))
                    launch(Intent(Intent.ACTION_SENDTO, uri))
                }, modifier = Modifier.fillMaxWidth()) { Text("Write feedback") }
                Text("Opens your email app with version and device details only. Review and send it yourself; nothing is sent automatically.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    launch(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, BetaHelp.invitation)
                    }, "Invite a tester"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Share tester instructions") }
                TextButton(onClick = { launch(Intent(Intent.ACTION_VIEW, Uri.parse(BetaHelp.OPT_IN))) }) { Text("Open Play test signup") }
                Text("Feedback: ${BetaHelp.EMAIL}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
