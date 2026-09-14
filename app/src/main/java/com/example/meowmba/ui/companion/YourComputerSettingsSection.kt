package com.example.meowmba.ui.companion

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.meowmba.ui.theme.Button
import androidx.compose.material3.MaterialTheme
import com.example.meowmba.ui.theme.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.agentic.ConnectionProfileType
import com.example.meowmba.core.computer.ComputerProfileMarks
import com.example.meowmba.core.computer.ComputerSetupStatus
import com.example.meowmba.core.computer.TermuxComputerLinks
import com.example.meowmba.core.computer.toHumanSentence
import com.example.meowmba.core.config.ComputerPowerState
import java.util.UUID

/**
 * Settings category **Your Computer** — phone-first Termux/PRoot, optional server.
 *
 * Does not own first-run onboarding screens; shares [TermuxComputerLinks] with them.
 */
@Composable
fun YourComputerSettingsSection(
    phoneProbeSummary: String = "Checking this phone…",
    phoneReady: Boolean = false,
    status: ComputerSetupStatus = ComputerSetupStatus.NotSetUp,
    statusDetail: String = "",
    installLog: String = "",
    computerPower: ComputerPowerState = ComputerPowerState.Idle,
    vncUrl: String? = null,
    savedComputerProfile: ConnectionProfile? = null,
    onSaveComputerProfile: (ConnectionProfile, passwordOrEmpty: String) -> Unit = { _, _ -> },
    onTestConnection: (ConnectionProfile, passwordOrEmpty: String) -> Unit = { _, _ -> },
    onInstallComputer: (ConnectionProfile, passwordOrEmpty: String) -> Unit = { _, _ -> },
    onOpenTerminal: () -> Unit = {},
    onOpenDesktop: (String) -> Unit = {},
    onPowerChange: (ComputerPowerState) -> Unit = {},
    onConfirmInstallPrompt: () -> Unit = {},
    showInstallPrompt: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var displayName by remember(savedComputerProfile?.id) {
        mutableStateOf(savedComputerProfile?.displayName ?: "My Computer")
    }
    var host by remember(savedComputerProfile?.id) { mutableStateOf(savedComputerProfile?.host.orEmpty()) }
    var port by remember(savedComputerProfile?.id) {
        mutableStateOf((savedComputerProfile?.port ?: 22).toString())
    }
    var username by remember(savedComputerProfile?.id) {
        mutableStateOf(savedComputerProfile?.username.orEmpty())
    }
    var credentialRef by remember(savedComputerProfile?.id) {
        mutableStateOf(savedComputerProfile?.credentialRef?.ifBlank { "computer_main" } ?: "computer_main")
    }
    var workspacePath by remember(savedComputerProfile?.id) {
        mutableStateOf(
            savedComputerProfile?.workspaceRoot
                ?: ComputerProfileMarks.DEFAULT_REMOTE_WORKSPACE,
        )
    }
    var authMode by remember { mutableStateOf("password") } // password | key
    var secret by remember { mutableStateOf("") }
    var profileType by remember(savedComputerProfile?.id) {
        mutableStateOf(savedComputerProfile?.type ?: ConnectionProfileType.SSH)
    }

    fun draftProfile(): ConnectionProfile = ConnectionProfile(
        id = savedComputerProfile?.id ?: UUID.randomUUID().toString(),
        displayName = displayName.trim().ifBlank { "My Computer" },
        type = profileType,
        host = host.trim(),
        port = port.toIntOrNull() ?: if (profileType == ConnectionProfileType.HTTPS_RUNTIME) 8766 else 22,
        username = username.trim().ifBlank { "user" },
        credentialRef = credentialRef.trim().ifBlank { "computer_main" },
        workspaceRoot = workspacePath.trim().ifBlank { null },
        pinHostKeyOnFirstConnect = true,
        hostKeyFingerprint = savedComputerProfile?.hostKeyFingerprint,
    )

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your Computer", style = MaterialTheme.typography.titleLarge)
        Text(
            TermuxComputerLinks.HOWTO_PHONE_SHORT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(status = status, detail = statusDetail)

        // ---- 1) On this phone ----
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("1 · On this phone", style = MaterialTheme.typography.titleMedium)
            Text(
                phoneProbeSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                TermuxComputerLinks.TERMUX_BOOTSTRAP_STEPS,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { openUrl(TermuxComputerLinks.TERMUX_FDROID) }) {
                    Text(TermuxComputerLinks.BUTTON_INSTALL_TERMUX)
                }
                OutlinedButton(onClick = { openUrl(TermuxComputerLinks.TERMUX_GITHUB_RELEASES) }) {
                    Text("GitHub releases")
                }
                OutlinedButton(onClick = { openUrl(TermuxComputerLinks.TERMUX_NETBIRD) }) {
                    Text(TermuxComputerLinks.BUTTON_NETBIRD)
                }
                OutlinedButton(onClick = {
                    val launch = context.packageManager
                        .getLaunchIntentForPackage(TermuxComputerLinks.TERMUX_PACKAGE)
                        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (launch != null) {
                        runCatching { context.startActivity(launch) }
                    } else {
                        openUrl(TermuxComputerLinks.TERMUX_FDROID)
                    }
                }) {
                    Text(TermuxComputerLinks.BUTTON_OPEN_TERMUX)
                }
            }
            TextButton(onClick = { openUrl(TermuxComputerLinks.TERMUX_FDROID) }) {
                Text(TermuxComputerLinks.BUTTON_SETUP_GUIDE + " (F-Droid page)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenTerminal, enabled = phoneReady || status == ComputerSetupStatus.Ready) {
                    Text("Open Terminal")
                }
                PowerRow(computerPower = computerPower, onPowerChange = onPowerChange)
            }
        }

        Spacer(Modifier.height(4.dp))

        // ---- 2) Optional server ----
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("2 · Linux server (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                TermuxComputerLinks.SERVER_HOWTO_SHORT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auth", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                OutlinedButton(onClick = { authMode = "password" }) {
                    Text(if (authMode == "password") "Password ✓" else "Password")
                }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { authMode = "key" }) {
                    Text(if (authMode == "key") "Key ref ✓" else "Key ref")
                }
            }
            OutlinedTextField(
                value = credentialRef,
                onValueChange = {
                    credentialRef = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }
                },
                label = { Text("Credential ref") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = {
                    Text(if (authMode == "password") "Password (SecureStorage)" else "Private key material (SecureStorage)")
                },
                singleLine = authMode == "password",
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = workspacePath,
                onValueChange = { workspacePath = it },
                label = { Text("Workspace path (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        profileType = ConnectionProfileType.SSH
                    },
                ) { Text(if (profileType == ConnectionProfileType.SSH) "SSH ✓" else "SSH") }
                OutlinedButton(
                    onClick = {
                        profileType = ConnectionProfileType.HTTPS_RUNTIME
                        if (port == "22") port = ComputerProfileMarks.WORKER_PORT.toString()
                    },
                ) {
                    Text(
                        if (profileType == ConnectionProfileType.HTTPS_RUNTIME) {
                            "HTTPS runtime ✓"
                        } else {
                            "HTTPS runtime"
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val p = draftProfile()
                OutlinedButton(onClick = { onSaveComputerProfile(p, secret) }) { Text("Save") }
                Button(onClick = { onTestConnection(p, secret) }) { Text("Test") }
                Button(
                    onClick = { onInstallComputer(p, secret) },
                    enabled = profileType == ConnectionProfileType.SSH,
                ) { Text("Install Computer") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenTerminal) { Text("Open Terminal") }
                if (!vncUrl.isNullOrBlank()) {
                    OutlinedButton(onClick = { onOpenDesktop(vncUrl); openUrl(vncUrl) }) { Text("Open Desktop") }
                }
            }
        }

        if (showInstallPrompt) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    TermuxComputerLinks.INSTALL_PROMPT,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onConfirmInstallPrompt) { Text("Install Meow Computer") }
            }
        }

        if (installLog.isNotBlank()) {
            Text("Install log", style = MaterialTheme.typography.titleSmall)
            val scroll = rememberScrollState()
            Text(
                installLog,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 220.dp)
                    .verticalScroll(scroll)
                    .padding(8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

@Composable
private fun StatusCard(status: ComputerSetupStatus, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            when (status) {
                ComputerSetupStatus.NotSetUp -> "Status · Not set up"
                ComputerSetupStatus.Connected -> "Status · Connected"
                ComputerSetupStatus.Installing -> "Status · Installing"
                ComputerSetupStatus.Ready -> "Status · Ready"
                ComputerSetupStatus.Error -> "Status · Error"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            status.toHumanSentence(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detail.isNotBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PowerRow(
    computerPower: ComputerPowerState,
    onPowerChange: (ComputerPowerState) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Power", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onPowerChange(ComputerPowerState.On) }) {
            Text(if (computerPower == ComputerPowerState.On) "On ✓" else "On")
        }
        TextButton(onClick = { onPowerChange(ComputerPowerState.Idle) }) {
            Text(if (computerPower == ComputerPowerState.Idle) "Idle ✓" else "Idle")
        }
        TextButton(onClick = { onPowerChange(ComputerPowerState.Off) }) {
            Text(if (computerPower == ComputerPowerState.Off) "Off ✓" else "Off")
        }
    }
}
