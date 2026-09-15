package com.example.meowmba.ui.companion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.meowmba.core.agentic.*
import com.example.meowmba.core.computer.ComputerSetupStatus
import com.example.meowmba.core.config.ComputerPowerState
import com.example.meowmba.ui.theme.Button
import com.example.meowmba.ui.theme.OutlinedButton
import com.example.meowmba.ui.theme.Card
import com.example.meowmba.ui.theme.Surface
import com.example.meowmba.ui.theme.FilterChip

internal object IconsForComputer {
    val phone get()=Icons.Default.PhoneAndroid
    val server get()=Icons.Default.Computer
}

/** Dedicated destination; the hub never expands forms into its own scrolling list. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ComputerToolsScreen(
    onClose: () -> Unit,
    agenticRuntimeV1: Boolean,
    onAgenticRuntimeV1Change: (Boolean) -> Unit,
    connectionProfiles: List<ConnectionProfile>,
    onSaveConnectionProfile: (ConnectionProfile) -> Unit,
    onDeleteConnectionProfile: (String) -> Unit,
    onSaveAgenticCredential: (String,String) -> Unit,
    agenticGoals: List<AgenticGoal>,
    agenticModelConfig: AgenticModelConfig.Snapshot,
    sshCapabilitySummary: String,
    onCreateAgenticGoal: (String) -> Unit,
    onTickAgenticGoal: (String) -> Unit,
    onTickOpenAgenticGoals: () -> Unit,
    onApproveAgenticGoal: (String) -> Unit,
    onRejectAgenticGoal: (String) -> Unit,
    onRefreshAgenticTasks: () -> Unit,
    onSaveAgenticModelConfig: (String,String,String,String) -> Unit,
    computerPhoneProbeSummary: String,
    computerPhoneReady: Boolean,
    computerSetupStatus: ComputerSetupStatus,
    computerStatusDetail: String,
    computerInstallLog: String,
    computerVncUrl: String?,
    computerSavedProfile: ConnectionProfile?,
    computerVerifiedProfile: ConnectionProfile?,
    onComputerSaveProfile: (ConnectionProfile,String) -> Unit,
    onComputerTestConnection: (ConnectionProfile,String) -> Unit,
    onComputerInstall: (ConnectionProfile,String) -> Unit,
    onComputerOpenTerminal: () -> Unit,
    onComputerOpenServerTerminal: (ConnectionProfile) -> Unit,
    onComputerOpenDesktop: (String) -> Unit,
    onRefreshComputerPhone: () -> Unit,
    computerPower: ComputerPowerState,
    onComputerPowerChange: (ComputerPowerState) -> Unit,
    computerIdleMinutes: Int,
    onComputerIdleMinutesChange: (Int) -> Unit,
) {
    val uriHandler=LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    var history by rememberSaveable { mutableStateOf(listOf(ComputerToolsPage.HOME.name)) }
    val page=ComputerToolsPage.valueOf(history.last())
    fun navigate(next:ComputerToolsPage) { if(next!=page) history=history+next.name }
    val back:()->Unit={ if(history.size>1)history=history.dropLast(1) else onClose() }
    BackHandler(onBack=back)
    val scroll=rememberScrollState()
    val drafts=rememberSaveableStateHolder()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latestRefresh by rememberUpdatedState(onRefreshComputerPhone)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_RESUME)latestRefresh() }
        lifecycle.addObserver(observer)
        latestRefresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=back) { Icon(Icons.Default.ArrowBack,contentDescription=if(page==ComputerToolsPage.HOME)"Back to settings" else "Back in Computer") }
                Text(page.title,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(page.setup) drafts.SaveableStateProvider("setup") {
                    YourComputerSettingsSection(page,::navigate,computerPhoneProbeSummary,computerPhoneReady,
                        computerSetupStatus,computerStatusDetail,computerSavedProfile,computerVerifiedProfile,
                        onComputerSaveProfile,onComputerTestConnection,onComputerInstall,onComputerOpenTerminal,
                        onComputerOpenServerTerminal,onRefreshComputerPhone)
                } else when(page) {
                    ComputerToolsPage.HOME -> {
                        Text("Your workspace, connections and tasks—one place to get started.",style=MaterialTheme.typography.bodyMedium)
                        ComputerDestinationCard("Set up Computer",if(computerSavedProfile==null) "Choose this phone or a Linux server." else "Manage ${computerSavedProfile.displayName} or set up this phone.",Icons.Default.Computer) { navigate(ComputerToolsPage.SETUP) }
                        if(computerPhoneReady) Button(onClick=onComputerOpenTerminal,modifier=Modifier.fillMaxWidth()) { Text("Open phone Terminal") }
                        computerSavedProfile?.takeIf { it.type==ConnectionProfileType.SSH }?.let { p ->
                            OutlinedButton(onClick={onComputerOpenServerTerminal(p)},modifier=Modifier.fillMaxWidth()) { Text("Open ${p.displayName}") }
                        }
                        val waiting=agenticGoals.count { it.status==GoalStatus.WAITING_APPROVAL }
                        ComputerDestinationCard("Tasks & approvals",if(waiting>0) "$waiting waiting for your review." else "Create tasks, run the next step and review requests.",Icons.Default.TaskAlt) { navigate(ComputerToolsPage.TASKS) }
                        ComputerDestinationCard("AI for tasks",if(agenticModelConfig.configured) agenticModelConfig.modelName else "Choose the model that plans computer tasks.",Icons.Default.Psychology) { navigate(ComputerToolsPage.MODEL) }
                        ComputerDestinationCard("Saved connections",if(agenticRuntimeV1) "${connectionProfiles.size} saved · add or edit SSH targets." else "Set up the connections used by task automation.",Icons.Default.Link) { navigate(ComputerToolsPage.PROFILES) }
                        ComputerDestinationCard("Power & sleep","${computerPower.name} · choose when Computer rests.",Icons.Default.PowerSettingsNew) { navigate(ComputerToolsPage.POWER) }
                        TextButton(onClick={navigate(ComputerToolsPage.DETAILS)}) { Text("Connection details & logs") }
                    }
                    ComputerToolsPage.TASKS,ComputerToolsPage.MODEL -> {
                        if(page==ComputerToolsPage.TASKS) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Task automation",style=MaterialTheme.typography.titleMedium)
                                    Text("Allow Meow to plan bounded computer tasks.",style=MaterialTheme.typography.bodySmall) }
                                Switch(agenticRuntimeV1,onAgenticRuntimeV1Change,modifier=Modifier.semantics { contentDescription="Task automation" })
                            }
                        }
                        if(agenticRuntimeV1 || page==ComputerToolsPage.MODEL) {
                            AgenticTasksSection(agenticGoals,agenticModelConfig,sshCapabilitySummary,onCreateAgenticGoal,
                                onTickAgenticGoal,onTickOpenAgenticGoals,onApproveAgenticGoal,onRejectAgenticGoal,
                                onRefreshAgenticTasks,onSaveAgenticModelConfig,modelOnly=page==ComputerToolsPage.MODEL)
                        } else Text("Turn on task automation to create and run tasks. Computer setup remains available.",style=MaterialTheme.typography.bodyMedium)
                    }
                    ComputerToolsPage.PROFILES -> {
                        if (agenticRuntimeV1) AgenticProfilesSection(connectionProfiles,onSaveConnectionProfile,onDeleteConnectionProfile,onSaveAgenticCredential)
                        else {
                            Text("Task connections are available when task automation is enabled.")
                            Button(onClick={onAgenticRuntimeV1Change(true)},modifier=Modifier.fillMaxWidth()) { Text("Enable task automation") }
                        }
                    }
                    ComputerToolsPage.POWER -> {
                        Text("Choose when the phone's Linux Computer runs. Remote servers keep their own power settings.")
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            ComputerPowerState.entries.forEach { state ->
                                FilterChip(state==computerPower,{onComputerPowerChange(state)},label={Text(state.name)})
                            }
                        }
                        Text(when(computerPower) {
                            ComputerPowerState.On -> "Keep the Computer session running."
                            ComputerPowerState.Idle -> "Let Computer sleep when it is unused."
                            ComputerPowerState.Off -> "Stop the phone's Computer session."
                        },style=MaterialTheme.typography.bodyMedium)
                        Text("Sleep after ${computerIdleMinutes.coerceIn(1,30)} minutes unused",style=MaterialTheme.typography.titleSmall)
                        Slider(computerIdleMinutes.coerceIn(1,30).toFloat(),{onComputerIdleMinutesChange(it.toInt().coerceIn(1,30))},valueRange=1f..30f,steps=28)
                    }
                    ComputerToolsPage.DETAILS -> {
                        Text("Phone",style=MaterialTheme.typography.titleMedium)
                        Text(computerPhoneProbeSummary,style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick=onRefreshComputerPhone) { Text("Refresh phone status") }
                        Text("Connection",style=MaterialTheme.typography.titleMedium)
                        Text(computerStatusDetail.ifBlank { "No connection check yet." },style=MaterialTheme.typography.bodySmall)
                        Text(sshCapabilitySummary,style=MaterialTheme.typography.bodySmall)
                        if(!computerVncUrl.isNullOrBlank()) OutlinedButton(onClick={onComputerOpenDesktop(computerVncUrl);runCatching { uriHandler.openUri(computerVncUrl) }.onFailure { linkError="Could not open the remote desktop link." }}) { Text("Open remote desktop") }
                        linkError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                        Text("Installation log",style=MaterialTheme.typography.titleMedium)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(computerInstallLog.ifBlank { "No installation log yet." },style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
internal fun ComputerDestinationCard(title:String,description:String,icon:ImageVector,onClick:()->Unit) {
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
