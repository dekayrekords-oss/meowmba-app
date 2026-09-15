package com.example.meowmba.ui.companion

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.agentic.ConnectionProfile
import com.example.meowmba.core.agentic.ConnectionProfileType
import com.example.meowmba.core.computer.ComputerProfileMarks
import com.example.meowmba.core.computer.ComputerSetupStatus
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.FilterChip
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.core.computer.TermuxComputerLinks
import java.util.UUID

/** One setup step at a time; non-secret drafts survive rotation, credentials stay in memory. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun YourComputerSettingsSection(
    step: ComputerToolsPage,
    onNavigate: (ComputerToolsPage) -> Unit,
    phoneProbeSummary: String,
    phoneReady: Boolean,
    status: ComputerSetupStatus,
    statusDetail: String,
    savedComputerProfile: ConnectionProfile?,
    verifiedProfile: ConnectionProfile?,
    onSaveComputerProfile: (ConnectionProfile, String) -> Unit,
    onTestConnection: (ConnectionProfile, String) -> Unit,
    onInstallComputer: (ConnectionProfile, String) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenServerTerminal: (ConnectionProfile) -> Unit,
    onRefreshPhone: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sourceId = savedComputerProfile?.id
    val draftId = rememberSaveable(sourceId) { sourceId ?: UUID.randomUUID().toString() }
    var displayName by rememberSaveable(sourceId) { mutableStateOf(savedComputerProfile?.displayName ?: "My Computer") }
    var host by rememberSaveable(sourceId) { mutableStateOf(savedComputerProfile?.host.orEmpty()) }
    var port by rememberSaveable(sourceId) { mutableStateOf((savedComputerProfile?.port ?: 22).toString()) }
    var username by rememberSaveable(sourceId) { mutableStateOf(savedComputerProfile?.username.orEmpty()) }
    var credentialRef by rememberSaveable(sourceId) { mutableStateOf(savedComputerProfile?.credentialRef?.ifBlank { "computer_main" } ?: "computer_main") }
    var workspace by rememberSaveable(sourceId) { mutableStateOf(savedComputerProfile?.workspaceRoot ?: ComputerProfileMarks.DEFAULT_REMOTE_WORKSPACE) }
    var type by rememberSaveable(sourceId) { mutableStateOf(savedComputerProfile?.type ?: ConnectionProfileType.SSH) }
    var authMode by rememberSaveable { mutableStateOf("password") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var secret by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val busy = status == ComputerSetupStatus.Checking || status == ComputerSetupStatus.Installing
    val validPort = port.toIntOrNull()?.let { it in 1..65535 } == true
    val addressValid = host.isNotBlank() && !host.any { it.isWhitespace() } && validPort
    val authValid = (type != ConnectionProfileType.SSH || username.isNotBlank()) && credentialRef.isNotBlank()
    val profile = ConnectionProfile(draftId, displayName.trim().ifBlank { "My Computer" }, type,
        host.trim(), port.toIntOrNull() ?: 22, username.trim(), credentialRef.trim(),
        hostKeyFingerprint = savedComputerProfile?.hostKeyFingerprint,
        workspaceRoot = workspace.trim().ifBlank { ComputerProfileMarks.DEFAULT_REMOTE_WORKSPACE },
        pinHostKeyOnFirstConnect = savedComputerProfile?.pinHostKeyOnFirstConnect ?: true)
    val checked = sameComputerConnection(profile, verifiedProfile) &&
        status in listOf(ComputerSetupStatus.Connected, ComputerSetupStatus.Ready) && secret.isEmpty()

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { notice = "Could not open the link. Try again after choosing a browser." }
    }
    fun openTermux() {
        val launch=context.packageManager.getLaunchIntentForPackage(TermuxComputerLinks.TERMUX_PACKAGE)
        if(launch == null) openUrl(TermuxComputerLinks.TERMUX_FDROID)
        else runCatching { context.startActivity(launch) }.onFailure { notice="Could not open Termux." }
    }

    Column(verticalArrangement=Arrangement.spacedBy(14.dp), modifier=Modifier.fillMaxWidth()) {
        when(step) {
            ComputerToolsPage.SETUP -> {
                Text("Where should your Computer run?", style=MaterialTheme.typography.titleLarge)
                Text("Choose one to get started. You can add the other later.", style=MaterialTheme.typography.bodyMedium)
                ComputerDestinationCard("This phone", "Use Termux for a Linux workspace on your phone.", IconsForComputer.phone) {
                    onNavigate(ComputerToolsPage.PHONE_INSTALL)
                }
                ComputerDestinationCard("A Linux server", savedComputerProfile?.let { "Continue with ${it.displayName}, or edit its connection." }
                    ?: "Connect a computer you own using SSH.", IconsForComputer.server) { onNavigate(ComputerToolsPage.SERVER_ADDRESS) }
            }
            ComputerToolsPage.PHONE_INSTALL -> {
                StepHint("Phone setup",1,3)
                Text("Install Termux", style=MaterialTheme.typography.titleLarge)
                Text("Termux provides the Linux environment. Return here once it is installed.")
                Text(if(phoneReady) "Termux is available on this phone." else "Termux has not been detected yet.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={openUrl(TermuxComputerLinks.TERMUX_FDROID)},modifier=Modifier.fillMaxWidth()) { Text("Get Termux") }
                TextButton(onClick={openUrl(TermuxComputerLinks.TERMUX_GITHUB_RELEASES)}) { Text("Alternative: GitHub download") }
                OutlinedButton(onClick=onRefreshPhone,modifier=Modifier.fillMaxWidth()) { Text("Check installation") }
                Button(onClick={onNavigate(ComputerToolsPage.PHONE_PREPARE)},enabled=phoneReady,modifier=Modifier.fillMaxWidth()) { Text("Continue") }
            }
            ComputerToolsPage.PHONE_PREPARE -> {
                StepHint("Phone setup",2,3)
                Text("Prepare Linux",style=MaterialTheme.typography.titleLarge)
                Text("Copy these commands, paste them in Termux, and wait for installation to finish. This downloads Linux packages.")
                Card { Text(TermuxComputerLinks.BOOTSTRAP_COMMANDS,Modifier.padding(14.dp),style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace)) }
                Button(onClick={clipboard.setText(AnnotatedString(TermuxComputerLinks.BOOTSTRAP_COMMANDS));notice="Commands copied."},modifier=Modifier.fillMaxWidth()) { Text("Copy setup commands") }
                OutlinedButton(onClick={openTermux()},modifier=Modifier.fillMaxWidth()) { Text("Open Termux") }
                TextButton(onClick={advanced=!advanced}) { Text(if(advanced) "Hide connection help" else "Help connecting Termux") }
                if(advanced) Text("If Meow cannot connect, allow Termux's RUN_COMMAND access and its external-app setting. The Terminal reports whether it reached Linux or only the phone sandbox.",style=MaterialTheme.typography.bodySmall)
                Button(onClick={onNavigate(ComputerToolsPage.PHONE_OPEN)},modifier=Modifier.fillMaxWidth()) { Text("I finished these steps") }
            }
            ComputerToolsPage.PHONE_OPEN -> {
                StepHint("Phone setup",3,3)
                Text("Try your Computer",style=MaterialTheme.typography.titleLarge)
                Text("Open Terminal to check the connection and start working. Installing Termux alone does not confirm that Linux is ready.")
                Button(onClick=onOpenTerminal,modifier=Modifier.fillMaxWidth()) { Text("Open phone Terminal") }
                OutlinedButton(onClick=onRefreshPhone,modifier=Modifier.fillMaxWidth()) { Text("Check phone again") }
                Text(phoneProbeSummary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ComputerToolsPage.SERVER_ADDRESS -> {
                StepHint("Server setup",1,3)
                Text("Which server?",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(displayName,{displayName=it},label={Text("Connection name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(host,{host=it},label={Text(if(type==ConnectionProfileType.SSH) "Server address" else "Server URL")},
                    placeholder={Text(if(type==ConnectionProfileType.SSH) "my-server or 192.168.1.10" else "https://server.example")},singleLine=true,modifier=Modifier.fillMaxWidth())
                TextButton(onClick={advanced=!advanced}) { Text(if(advanced) "Hide advanced connection options" else "Advanced connection options") }
                if(advanced) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(type==ConnectionProfileType.SSH,{type=ConnectionProfileType.SSH;if(port=="18766")port="22"},label={Text("SSH")})
                        FilterChip(type==ConnectionProfileType.HTTPS_RUNTIME,{type=ConnectionProfileType.HTTPS_RUNTIME;if(port=="22")port="18766"},label={Text("HTTP worker")})
                    }
                    OutlinedTextField(port,{port=it.filter(Char::isDigit).take(5)},label={Text("Port")},isError=!validPort,singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(workspace,{workspace=it},label={Text("Workspace folder")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    if(type==ConnectionProfileType.HTTPS_RUNTIME) Text("Use the full worker URL reachable from this phone. This is a separate connection from the desktop bridge.",style=MaterialTheme.typography.bodySmall)
                }
                Button(onClick={advanced=false;onNavigate(ComputerToolsPage.SERVER_SIGN_IN)},enabled=addressValid&&!busy,modifier=Modifier.fillMaxWidth()) { Text("Continue") }
            }
            ComputerToolsPage.SERVER_SIGN_IN -> {
                StepHint("Server setup",2,3)
                Text("Sign in",style=MaterialTheme.typography.titleLarge)
                Text(host,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(type==ConnectionProfileType.SSH) {
                    OutlinedTextField(username,{username=it},label={Text("Username")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(authMode=="password",{authMode="password"},label={Text("Password")})
                        FilterChip(authMode=="key",{authMode="key"},label={Text("Private key")})
                    }
                }
                OutlinedTextField(secret,{secret=it},label={Text(if(type==ConnectionProfileType.HTTPS_RUNTIME) "Access token" else if(authMode=="key") "Private key" else "Password")},
                    visualTransformation=PasswordVisualTransformation(),singleLine=authMode!="key",maxLines=5,modifier=Modifier.fillMaxWidth())
                Text("Leave blank to reuse a saved credential. New credentials are stored encrypted when you save or test.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={advanced=!advanced}) { Text(if(advanced) "Hide saved credential options" else "Saved credential options") }
                if(advanced) OutlinedTextField(credentialRef,{credentialRef=it.filter { c->c.isLetterOrDigit()||c=='_'||c=='-' }},label={Text("Saved credential name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Button(onClick={advanced=false;onNavigate(ComputerToolsPage.SERVER_REVIEW)},enabled=authValid&&!busy,modifier=Modifier.fillMaxWidth()) { Text("Review connection") }
            }
            ComputerToolsPage.SERVER_REVIEW -> {
                StepHint("Server setup",3,3)
                Text(profile.displayName,style=MaterialTheme.typography.titleLarge)
                Text(if(type==ConnectionProfileType.SSH) "${profile.username}@${profile.host}:${profile.port}" else profile.host)
                Text("Workspace: ${profile.workspaceRoot}",style=MaterialTheme.typography.bodySmall)
                Text(if(checked) "Connection checked successfully." else "Test the connection before installing a server workspace.",style=MaterialTheme.typography.bodyMedium)
                Button(onClick={onTestConnection(profile,secret);secret=""},enabled=addressValid&&authValid&&!busy,modifier=Modifier.fillMaxWidth()) {
                    Text(if(status==ComputerSetupStatus.Checking) "Checking connection…" else "Save & test connection")
                }
                OutlinedButton(onClick={onSaveComputerProfile(profile,secret);secret="";notice="Save requested. Test the connection when you are ready."},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Save for later") }
                if(type==ConnectionProfileType.SSH) {
                    Text("Optional: Install Computer uploads the workspace installer to this server and runs it there.",style=MaterialTheme.typography.bodySmall)
                    Button(onClick={onInstallComputer(profile,"")},enabled=checked&&!busy,modifier=Modifier.fillMaxWidth()) { Text(if(status==ComputerSetupStatus.Installing) "Installing…" else "Install Computer") }
                    OutlinedButton(onClick={onOpenServerTerminal(profile)},enabled=checked&&!busy,modifier=Modifier.fillMaxWidth()) { Text("Open server Terminal") }
                }
                if(statusDetail.isNotBlank()) {
                    Text(if(status==ComputerSetupStatus.Error) "Connection needs attention" else "Connection status",style=MaterialTheme.typography.titleSmall)
                    Text(statusDetail,style=MaterialTheme.typography.bodySmall,color=if(status==ComputerSetupStatus.Error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> Unit
        }
        notice?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun StepHint(name:String,step:Int,total:Int) {
    Text("$name · Step $step of $total",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
    LinearProgressIndicator(progress={step.toFloat()/total},modifier=Modifier.fillMaxWidth())
}
