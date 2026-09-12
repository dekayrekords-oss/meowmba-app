package com.example.meowmba.ui.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.computer.TermuxComputerLinks
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.setup.SetupWiring

/**
 * First-run and Settings → Start setup walkthrough.
 *
 * Shown by [com.example.meowmba.MainActivity] when
 * [SetupWiring.needsOnboarding] is true, or on demand from Settings.
 *
 * One idea per screen. Large type. Kid-friendly words. Computer (phone) first.
 * Brain / image / Neural / keys stay in Settings — not here.
 */
private enum class FirstRunPage {
    Hi,
    GetTermux,
    CopyCommands,
    IDidThat,
    Mic,
    OptionalServer,
    ServerFields,
    Done,
}

@Composable
fun SetupWizardRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onEnablePermissions: () -> Unit = {},
    onOpenChat: () -> Unit = onFinished,
    onOpenTerminal: () -> Unit = onFinished,
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(FirstRunPage.Hi.name) }
    val current = remember(page) {
        runCatching { FirstRunPage.valueOf(page) }.getOrDefault(FirstRunPage.Hi)
    }
    var copied by rememberSaveable { mutableStateOf(false) }
    var termuxOk by remember { mutableStateOf(termuxInstalled(context)) }
    val storage = remember { SecureStorageManager(context) }
    var host by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    fun go(next: FirstRunPage) {
        page = next.name
    }

    fun finish() {
        SetupWiring.markOnboardingDone(context)
        onFinished()
    }

    fun back() {
        when (current) {
            FirstRunPage.Hi -> finish()
            FirstRunPage.GetTermux -> go(FirstRunPage.Hi)
            FirstRunPage.CopyCommands -> go(FirstRunPage.GetTermux)
            FirstRunPage.IDidThat -> go(FirstRunPage.CopyCommands)
            FirstRunPage.Mic -> go(FirstRunPage.IDidThat)
            FirstRunPage.OptionalServer -> go(FirstRunPage.Mic)
            FirstRunPage.ServerFields -> go(FirstRunPage.OptionalServer)
            FirstRunPage.Done -> go(FirstRunPage.OptionalServer)
        }
    }

    BackHandler { back() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            if (current != FirstRunPage.Done) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { back() }, enabled = current != FirstRunPage.Hi) {
                        Text("Back")
                    }
                    TextButton(onClick = { finish() }) { Text("Skip") }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (current) {
                    FirstRunPage.Hi -> KidPage(
                        title = "Hi.",
                        body = "This is your helper. It lives on this phone.",
                        primary = "Let’s go",
                        onPrimary = { go(FirstRunPage.GetTermux) },
                    )
                    FirstRunPage.GetTermux -> GetTermuxPage(
                        termuxOk = termuxOk,
                        onRefresh = { termuxOk = termuxInstalled(context) },
                        onGetFdroid = { openUrl(context, TermuxComputerLinks.TERMUX_FDROID) },
                        onGetGithub = { openUrl(context, TermuxComputerLinks.TERMUX_GITHUB_RELEASES) },
                        onGetNetBird = { openUrl(context, TermuxComputerLinks.TERMUX_NETBIRD) },
                        onOpenTermux = {
                            termuxOk = termuxInstalled(context)
                            if (!openTermux(context)) {
                                // Not installed yet — send them to the app list so they can finish install.
                                openAppLauncher(context)
                            }
                        },
                        onInstalled = {
                            termuxOk = termuxInstalled(context)
                            openTermux(context) // best-effort; still continue
                            go(FirstRunPage.CopyCommands)
                        },
                        onShowApps = { openAppLauncher(context) },
                    )
                    FirstRunPage.CopyCommands -> CopyCommandsPage(
                        copied = copied,
                        onCopy = {
                            copyText(context, TermuxComputerLinks.BOOTSTRAP_COMMANDS)
                            copied = true
                        },
                        onOpenTermux = {
                            termuxOk = termuxInstalled(context)
                            if (!openTermux(context)) openAppLauncher(context)
                        },
                        onNext = { go(FirstRunPage.IDidThat) },
                    )
                    FirstRunPage.IDidThat -> KidPage(
                        title = if (termuxOk) "Termux is on this phone." else "Almost done.",
                        body = if (termuxOk) {
                            "Nice. We found Termux. You can open it again if you need to."
                        } else {
                            "Open Termux, paste the words, then come back. Or open your apps if you still need to install it."
                        },
                        primary = "I did that",
                        onPrimary = {
                            termuxOk = termuxInstalled(context)
                            go(FirstRunPage.Mic)
                        },
                        secondary = TermuxComputerLinks.BUTTON_OPEN_TERMUX,
                        onSecondary = {
                            termuxOk = termuxInstalled(context)
                            if (!openTermux(context)) openAppLauncher(context)
                        },
                        tertiary = TermuxComputerLinks.BUTTON_SHOW_APPS,
                        onTertiary = { openAppLauncher(context) },
                    )
                    FirstRunPage.Mic -> KidPage(
                        title = "Can we use the mic?",
                        body = "Then you can talk to your helper. You can say no.",
                        primary = "Allow",
                        onPrimary = {
                            onEnablePermissions()
                            go(FirstRunPage.OptionalServer)
                        },
                        secondary = "Not now",
                        onSecondary = { go(FirstRunPage.OptionalServer) },
                    )
                    FirstRunPage.OptionalServer -> KidPage(
                        title = "Have a computer on the internet?",
                        body = "You do not need one. This phone is enough.",
                        primary = "No, I’m done",
                        onPrimary = { go(FirstRunPage.Done) },
                        secondary = "Yes",
                        onSecondary = { go(FirstRunPage.ServerFields) },
                    )
                    FirstRunPage.ServerFields -> ServerFieldsPage(
                        host = host,
                        user = user,
                        password = password,
                        onHost = { host = it },
                        onUser = { user = it },
                        onPassword = { password = it },
                        onSave = {
                            if (host.isNotBlank()) storage.savePreference("onboard_remote_host", host.trim())
                            if (user.isNotBlank()) storage.savePreference("onboard_remote_user", user.trim())
                            if (password.isNotBlank()) storage.saveApiKey("onboard_remote", password)
                            go(FirstRunPage.Done)
                        },
                        onLater = { go(FirstRunPage.Done) },
                    )
                    FirstRunPage.Done -> KidPage(
                        title = "You’re ready.",
                        body = "Chat with your helper, or open Terminal.",
                        primary = "Chat",
                        onPrimary = {
                            SetupWiring.markOnboardingDone(context)
                            onOpenChat()
                        },
                        secondary = "Terminal",
                        onSecondary = {
                            SetupWiring.markOnboardingDone(context)
                            onOpenTerminal()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GetTermuxPage(
    termuxOk: Boolean,
    onRefresh: () -> Unit,
    onGetFdroid: () -> Unit,
    onGetGithub: () -> Unit,
    onGetNetBird: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstalled: () -> Unit,
    onShowApps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Your Computer is this phone.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (termuxOk) {
                "Termux is ready. Tap Open Termux, then come back."
            } else {
                "First, get a free app called Termux. Use a big button — not the Play Store. After you install it, open it from here."
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        KidPrimaryButton(TermuxComputerLinks.BUTTON_GET_TERMUX, onGetFdroid)
        OutlinedButton(
            onClick = onGetGithub,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(TermuxComputerLinks.BUTTON_GITHUB_DOWNLOAD, style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            onClick = onGetNetBird,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(TermuxComputerLinks.BUTTON_NETBIRD, style = MaterialTheme.typography.titleMedium) }
        KidPrimaryButton(TermuxComputerLinks.BUTTON_OPEN_TERMUX) {
            onRefresh()
            onOpenTermux()
        }
        OutlinedButton(
            onClick = onInstalled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(TermuxComputerLinks.BUTTON_I_INSTALLED, style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = onShowApps) { Text(TermuxComputerLinks.BUTTON_SHOW_APPS) }
        TextButton(onClick = {
            onRefresh()
            onInstalled()
        }) { Text("I already have it") }
    }
}

@Composable
private fun CopyCommandsPage(
    copied: Boolean,
    onCopy: () -> Unit,
    onOpenTermux: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "Open Termux and paste this.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "This makes a tiny Linux on your phone.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            TermuxComputerLinks.BOOTSTRAP_COMMANDS,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                lineHeight = 28.sp,
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        KidPrimaryButton(TermuxComputerLinks.BUTTON_OPEN_TERMUX, onOpenTermux)
        KidPrimaryButton(if (copied) "Copied!" else "Copy", onCopy)
        TextButton(onClick = onNext) { Text("Next") }
    }
}

@Composable
private fun ServerFieldsPage(
    host: String,
    user: String,
    password: String,
    onHost: (String) -> Unit,
    onUser: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSave: () -> Unit,
    onLater: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Add that computer later if you want.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Name, user, and secret. Or skip.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = host,
            onValueChange = onHost,
            label = { Text("Computer name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user,
            onValueChange = onUser,
            label = { Text("Your name on it") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("Secret word") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        KidPrimaryButton("Save", onSave)
        TextButton(onClick = onLater) { Text("Add later") }
    }
}

@Composable
private fun KidPage(
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiary: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        KidPrimaryButton(primary, onPrimary)
        if (secondary != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(secondary, style = MaterialTheme.typography.titleMedium) }
        }
        if (tertiary != null && onTertiary != null) {
            TextButton(onClick = onTertiary) { Text(tertiary) }
        }
    }
}

@Composable
private fun KidPrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Launch Termux if installed. Returns true when a launch intent was fired. */
private fun openTermux(context: Context): Boolean {
    val launch = context.packageManager
        .getLaunchIntentForPackage(TermuxComputerLinks.TERMUX_PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (launch != null) {
        runCatching { context.startActivity(launch) }
        return true
    }
    // Fallback explicit main activity for some Termux builds.
    val explicit = Intent(Intent.ACTION_MAIN).apply {
        setPackage(TermuxComputerLinks.TERMUX_PACKAGE)
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(explicit)
        true
    }.getOrDefault(false)
}

/** Leave setup for the home/app list so the user is never trapped. */
private fun openAppLauncher(context: Context) {
    val home = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(home) }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("setup", text))
}

private fun termuxInstalled(context: Context): Boolean {
    return runCatching {
        context.packageManager.getPackageInfo(TermuxComputerLinks.TERMUX_PACKAGE, 0)
        true
    }.getOrDefault(false)
}
