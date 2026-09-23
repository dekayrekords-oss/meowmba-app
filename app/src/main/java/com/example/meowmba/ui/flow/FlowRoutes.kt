package com.example.meowmba.ui.flow

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.meowmba.core.flow.*
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.desk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Collect only in selected modes. Minute refresh is foreground-only; no model polling. */
@Composable
internal fun FlowLifecycle(vm: CompanionViewModel, driving: Boolean) {
    val flow = vm.flowRuntime ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(flow) { flow.start(); onDispose { flow.stop() } }
    LaunchedEffect(flow, driving) {
        flow.publish(Evidence(EvidenceType.MOVEMENT, System.currentTimeMillis(), "move",
            if (driving) "Driving" else "Stopped",
            mapOf("activity" to if (driving) Activity.DRIVING.name else Activity.STILL.name)))
    }
    LaunchedEffect(flow, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                flow.controller.schedule(immediate = true)
                delay(60_000)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlowHomeRoute(vm: CompanionViewModel, state: CompanionState, paused: Boolean) {
    val flow = vm.flowRuntime ?: return
    val snapshot by flow.snapshot.collectAsState()
    val settings by flow.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var adjust by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        flow.controller.schedule(immediate = true)
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = vm::toggleSettings) { Text("App settings") }
            TextButton(onClick = { add = true }) { Text("Add task") }
            TextButton(onClick = { vm.setAppTheme(AppTheme.COMPANION); vm.setAvatarStyle(AvatarStyle.DESK) }) { Text("Desk") }
        }
        MeowFlowHome(snapshot, settings,
            DateTimeFormatter.ofPattern("EEEE, MMM d").format(Instant.ofEpochMilli(snapshot.at.takeIf { it > 0 } ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault())),
            modifier = Modifier.weight(1f), reduceMotion = paused || state.avatarFpsTarget <= 15,
            speaking = !paused && state.interactionState == InteractionState.SPEAKING,
            onPrimary = { scope.launch { flow.onPrimary(it.intentionId) } },
            onLater = { scope.launch { flow.onLater(it.intentionId) } },
            onInterruptAccept = { scope.launch { flow.onPrimary(it.item.intentionId) } },
            onInterruptDecline = { scope.launch { flow.onLater(it.item.intentionId) } },
            onAdjust = { adjust = true }, onAsk = vm::openChat, onMic = vm::onMicClick)
    }
    if (adjust) ModalBottomSheet(onDismissRequest = { adjust = false }) {
        Column {
            if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                TextButton(onClick = { permission.launch(Manifest.permission.READ_CALENDAR) }) { Text("Connect calendar (optional)") }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable Flow notifications (optional)") }
            }
            FlowAdjustSheet(settings, "Not enough activity yet", snapshot.evening,
                onChange = flow::updateSettings, onDone = { adjust = false })
        }
    }
    if (add) AlertDialog(onDismissRequest = { if (!saving) add = false }, title = { Text("Keep something in mind") },
        text = { Column {
            OutlinedTextField(text, { text = it.take(300) }, label = { Text("Task") }, enabled = !saving)
            Text("Saved on this phone. Done records completion; Later brings it back in an hour.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it) }
        } },
        confirmButton = { TextButton(enabled = text.isNotBlank() && !saving, onClick = {
            saving = true; error = null
            scope.launch {
                try {
                    val now = System.currentTimeMillis()
                    flow.store.upsert(Intention(UUID.randomUUID().toString(), text.trim(), FlowKind.OTHER, "saved this task", now))
                    flow.publish(com.example.meowmba.core.flow.sources.EvidenceMappers.fromCapturedNeed(text.trim(), now))
                    text = ""; add = false
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { error = "Could not save. Please try again." }
                finally { saving = false }
            }
        }) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { add = false }) { Text("Cancel") } })
}

@Composable
internal fun DeskRoute(vm: CompanionViewModel, state: CompanionState, paused: Boolean, modifier: Modifier) {
    val flow = vm.flowRuntime ?: return
    val snap by flow.snapshot.collectAsState()
    val time = Instant.ofEpochMilli(snap.at.takeIf { it > 0 } ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault())
    val facts = remember(snap.at) { DeskFacts(clockHour = time.hour, clockMinute = time.minute,
        night = time.hour >= 19 || time.hour < 6,
        nextEventTime = snap.main?.headline?.take(18),
        monitorLines = listOf(MonitorLine("Meow " + com.example.meowmba.BuildConfig.VERSION_NAME))) }
    val desk = remember(snap, facts) { DeskState.from(snap, facts) }
    DeskHabitat(desk, modifier,
        thermalStatus = com.example.meowmba.core.thermal.ThermalGovernor.currentStatus(),
        fpsTarget = state.avatarFpsTarget, reduceMotion = paused,
        wanderEveryMs = if (state.avatarFpsTarget <= 15) 0 else 26_000,
        lineFor = { obj -> when (obj) {
            DeskObject.TODAY -> snap.main?.headline ?: "Add a task in Meow Flow."
            DeskObject.BUILD -> "Meow " + com.example.meowmba.BuildConfig.VERSION_NAME
            else -> "Your saved tasks are in Meow Flow."
        } },
        onArrive = { vm.setAppTheme(AppTheme.FLOW) })
}
