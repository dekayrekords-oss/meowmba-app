package com.example.meowmba.ui.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.meowmba.BuildConfig
import com.example.meowmba.core.flow.*
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.desk.*
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun DeskRoute(vm: CompanionViewModel, state: CompanionState, paused: Boolean, modifier: Modifier,
    interactive: Boolean = true) {
    val flow = vm.flowRuntime ?: return
    val snap by flow.snapshot.collectAsState()
    val intentions by flow.store.intentions.collectAsState()
    val now = snap.at.takeIf { it > 0 } ?: System.currentTimeMillis()
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val tasks = intentions.filter { it.isOpenAt(now) }
    val weather = state.ambientWeather ?: state.weather
    val project = state.projects.firstOrNull()
    val facts = DeskFacts(clockHour = time.hour, clockMinute = time.minute,
        night = weather?.let { !it.isDay } ?: (time.hour >= 19 || time.hour < 6),
        projectTitle = project?.name?.take(18).orEmpty(), projectStatus = project?.kindLabel.orEmpty(),
        pinnedNote = listOf("Meow", BuildConfig.VERSION_NAME),
        weatherLine = weather?.let { "${it.temperatureF}° ${it.condition.name.lowercase().replace('_', ' ')}" },
        nextEventTime = tasks.firstOrNull()?.text?.take(12), nextEventWords = if (tasks.isEmpty()) emptyList() else listOf("Tap for tasks"),
        monitorLines = listOf(MonitorLine("Meow ${BuildConfig.VERSION_NAME}"), MonitorLine("${tasks.size} open tasks", true)))
    DeskWorkspace(DeskState.from(snap, facts), tasks, modifier, paused = paused, covered = !avatarSceneVisible(state),
        fpsTarget = state.avatarFpsTarget, interactive = interactive,
        onMenu = vm::openTiles, onSettings = vm::toggleSettings,
        onFlow = { vm.setAppTheme(AppTheme.FLOW) }, onProjects = vm::toggleProjectsScreen,
        onWeather = vm::toggleWeatherScreen, onConnections = vm::toggleConnectorsScreen, onNotes = vm::toggleVaultScreen)
}

/** Real sheets remain inside Desk; leaving the scene is always an explicit action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeskWorkspace(desk: DeskState, tasks: List<Intention>, modifier: Modifier = Modifier,
    paused: Boolean = false, covered: Boolean = false, fpsTarget: Int = 30, interactive: Boolean = true,
    thermalStatus: Int = com.example.meowmba.core.thermal.ThermalGovernor.currentStatus(),
    onMenu: () -> Unit = {}, onSettings: () -> Unit = {}, onFlow: () -> Unit = {},
    onProjects: () -> Unit = {}, onWeather: () -> Unit = {}, onConnections: () -> Unit = {}, onNotes: () -> Unit = {}) {
    var selected by rememberSaveable { mutableStateOf<DeskObject?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        DeskHabitat(desk, Modifier.fillMaxSize(), thermalStatus = thermalStatus,
            fpsTarget = fpsTarget, reduceMotion = paused || selected != null,
            interactive = interactive, wanderEveryMs = if (fpsTarget <= 15) 0 else 26_000,
            lineFor = { deskTitle(it) }, onArrive = { if (!covered) selected = it })
        val wide = maxWidth > maxHeight
        if (interactive) DeskNavigation(if (wide) Modifier.align(Alignment.CenterStart).safeDrawingPadding()
            else Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 140.dp), vertical = wide,
            onMenu = onMenu, onFlow = onFlow, onSettings = onSettings)
    }
    val obj = selected
    if (obj != null && interactive) ModalBottomSheet(onDismissRequest = { selected = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF193039), contentColor = Color(0xFFF4EEDB),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), dragHandle = null) {
        Column(Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("desk_detail")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(deskTitle(obj), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { selected = null }) { Text("Close", color = Color(0xFFF4EEDB)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            val relevant = when (obj) {
                DeskObject.MONEY -> tasks.filter { it.kind == FlowKind.MONEY }
                DeskObject.INBOX -> tasks.filter { it.kind == FlowKind.PEOPLE }
                DeskObject.IDEA -> tasks.filter { it.kind == FlowKind.OTHER }
                DeskObject.PROJECT -> tasks.filter { it.kind == FlowKind.WORK }
                DeskObject.TODAY -> tasks
                else -> emptyList()
            }
            val summary = when (obj) {
                DeskObject.WINDOW -> desk.weatherLine ?: "Weather is not connected yet. Open Weather to choose a location."
                DeskObject.PROJECT -> desk.projectTitle.takeIf { it.isNotBlank() } ?: "Your projects will appear here. Open Projects to choose or create one."
                DeskObject.BUILD -> "Meow ${BuildConfig.VERSION_NAME}\n${tasks.size} open tasks saved on this phone."
                DeskObject.TODAY -> if (tasks.isEmpty()) "Nothing waiting. Add a task in Flow to keep something in mind." else "Your saved tasks"
                DeskObject.INBOX -> if (relevant.isEmpty()) "No people tasks saved. Connect your services to set up message access." else "People tasks you saved"
                DeskObject.MONEY -> if (relevant.isEmpty()) "No money tasks saved." else "Money tasks you saved"
                DeskObject.IDEA -> if (relevant.isEmpty()) "No ideas waiting. Open Notes to capture one." else "Notes and things to remember"
            }
            Text(summary, style = MaterialTheme.typography.bodyLarge)
            relevant.take(12).forEach { task ->
                Text(task.text, Modifier.fillMaxWidth().padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
            }
            val action = when (obj) {
                DeskObject.WINDOW -> "Open Weather" to onWeather
                DeskObject.PROJECT -> "Open Projects" to onProjects
                DeskObject.BUILD -> "App settings" to onSettings
                DeskObject.INBOX -> "Connections" to onConnections
                DeskObject.IDEA -> "Open Notes" to onNotes
                else -> "Open Flow" to onFlow
            }
            OutlinedButton(onClick = { selected = null; action.second() }, border = BorderStroke(2.dp, Color(0xFF819F9F)),
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) { Text(action.first, color = Color(0xFFF4EEDB)) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

internal fun deskTitle(obj: DeskObject) = when (obj) {
    DeskObject.WINDOW -> "Weather"
    DeskObject.PROJECT -> "Projects"
    DeskObject.BUILD -> "About Meow"
    DeskObject.TODAY -> "Next up"
    DeskObject.MONEY -> "Money"
    DeskObject.INBOX -> "Messages & people"
    DeskObject.IDEA -> "Ideas & notes"
}
