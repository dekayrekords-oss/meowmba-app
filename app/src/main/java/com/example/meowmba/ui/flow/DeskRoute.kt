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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.meowmba.BuildConfig
import com.example.meowmba.core.flow.*
import com.example.meowmba.core.flow.sources.AppIntentions
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.desk.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Meow's Desk mode (AppTheme.DESK): the office scene bound to the same data as Meow Flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeskRoute(vm: CompanionViewModel, state: CompanionState, paused: Boolean, modifier: Modifier,
    interactive: Boolean = true) {
    val flow = vm.flowRuntime ?: return
    val snap by flow.snapshot.collectAsState()
    val intentions by flow.store.intentions.collectAsState()
    val now = snap.at.takeIf { it > 0 } ?: System.currentTimeMillis()
    val tasks = intentions.filter { it.isOpenAt(now) }
    val weather = state.ambientWeather ?: state.weather
    val stars = vm.northStars
    val starViews = stars?.views?.collectAsState()?.value.orEmpty()
    val mainStar = starViews.firstOrNull()
    val catActivity = when {
        state.interactionState == InteractionState.THINKING || state.computerTaskRunning -> DeskCatActivity.THINKING
        state.interactionState == InteractionState.LISTENING -> DeskCatActivity.LISTENING
        state.interactionState == InteractionState.SPEAKING -> DeskCatActivity.SPEAKING
        else -> DeskCatActivity.IDLE
    }
    val replies = remember(vm) { DeskCatReplies(state.activeConversationId, state.deskReplySequence, state.liveAssistantReply) }
    var catReply by remember(vm) { mutableStateOf<DeskCatReply?>(null) }
    LaunchedEffect(state.activeConversationId, state.deskReplySequence, state.liveAssistantReply, catActivity) {
        catReply = replies.observe(state.activeConversationId, state.deskReplySequence, state.deskReplyConversationId, state.deskReplyText,
            state.liveReplyConversationId, state.liveAssistantReply, catActivity)
    }
    val facts = deskFacts(now, tasks, state.projects.firstOrNull(), weather?.let { "${it.temperatureF}° ${it.condition.label.lowercase()}" },
        weather?.let { !it.isDay }).let { f -> mainStar?.let {
            f.copy(projectTitle = it.star.title.take(28), projectStatus = it.nextStep.take(55))
        } ?: f }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attach by remember { mutableStateOf(false) }
    var northStarOpen by remember { mutableStateOf(false) }
    DeskWorkspace(DeskState.from(snap, facts, tasks), tasks, modifier, paused = paused,
        fpsTarget = state.avatarFpsTarget, interactive = interactive,
        catActivity = catActivity, catReply = catReply, onAsk = vm::onUserTextSubmitted,
        onMenu = vm::openTiles, onSettings = vm::toggleSettings,
        onFlow = { vm.setAppTheme(AppTheme.FLOW) }, onProjects = vm::toggleProjectsScreen,
        onWeather = vm::toggleWeatherScreen, onConnections = vm::toggleConnectorsScreen, onNotes = vm::toggleVaultScreen,
        onTaskDone = { id -> scope.launch { handleFlowPrimary(vm, context, flow, id) { _ -> vm.setAppTheme(AppTheme.FLOW) } } },
        onTaskLater = { id -> scope.launch { flow.onLater(id) } },
        micOn = state.isMicEnabled, onMicToggle = { vm.setMicEnabled(!state.isMicEnabled) },
        onAttach = { attach = true },
        onClock = { openClock(context) },
        onNorthStar = if (mainStar != null) ({ northStarOpen = true }) else null,
        drawerContent = { top, close -> DeskDrawerPanel(vm, top, close) },
        dock = { HomeDock(vm, DockStyle.DESK) })
    if (attach) AttachSheet(vm, onDismiss = { attach = false })
    if (northStarOpen && stars != null) ModalBottomSheet(onDismissRequest = { northStarOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NorthStarSheet(stars, onClose = { northStarOpen = false }, onOpenSource = { id ->
            if (tasks.any { it.id == id }) {
                northStarOpen = false
                scope.launch { handleFlowPrimary(vm, context, flow, id) { _ -> vm.setAppTheme(AppTheme.FLOW) } }
            } else android.widget.Toast.makeText(context, "That item is no longer active.", android.widget.Toast.LENGTH_SHORT).show()
        })
    }
}

private val HM = DateTimeFormatter.ofPattern("h:mm", Locale.US)

/** Android's Clock app on the alarms page; falls back to any clock the phone has. */
internal fun openClock(context: android.content.Context) {
    val intents = listOf(
        android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS),
        android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS),
        context.packageManager.getLaunchIntentForPackage("com.sec.android.app.clockpackage"),
        context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock"),
    ).filterNotNull()
    for (i in intents) if (runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
}

/** Desk objects from real items (master plan §9.1): nothing is invented; empty stays empty. */
internal fun deskFacts(now: Long, open: List<Intention>, project: ProjectUi?, weatherLine: String?, night: Boolean?,
                       zone: ZoneId = ZoneId.systemDefault()): DeskFacts {
    val time = Instant.ofEpochMilli(now).atZone(zone)
    // "Allow" setup rows belong to Flow; the desk shows real things only.
    val tasks = open.filterNot { it.id.startsWith(AppIntentions.SETUP) }
    val next = tasks.filter { it.dueAt != null && it.dueAt >= now - 10 * 60_000L }.minByOrNull { it.dueAt!! }
    val sticky = when {
        next != null -> listOf(HM.format(Instant.ofEpochMilli(next.dueAt!!).atZone(zone))) + wrap(next.text, 2)
        tasks.isNotEmpty() -> listOf("Next up") + wrap(tasks.first().text, 2)
        else -> listOf("All clear")
    }
    val work = tasks.firstOrNull { it.kind == FlowKind.WORK }
    val people = tasks.count { it.kind == FlowKind.PEOPLE }
    return DeskFacts(
        clockHour = time.hour, clockMinute = time.minute,
        night = night ?: (time.hour >= 19 || time.hour < 6),
        projectTitle = project?.name?.take(18).orEmpty(),
        projectStatus = project?.let { "${it.kindLabel} · ${it.whenLabel}".take(24) }.orEmpty(),
        pinnedNote = listOf("Meow", BuildConfig.VERSION_NAME),
        weatherLine = weatherLine,
        nextEventTime = sticky.first(), nextEventWords = sticky.drop(1),
        monitorLines = listOfNotNull(
            // The sticky note covers the monitor's right side (as in the design): keep lines short.
            work?.let { MonitorLine("> " + it.text.removePrefix("Continue ").take(12), true) },
            MonitorLine("${tasks.size} open"),
            MonitorLine("$people waiting"),
        ).take(3),
        ideaQueued = tasks.any { it.kind == FlowKind.OTHER && !it.id.startsWith(AppIntentions.SETUP) },
    )
}

private fun wrap(text: String, lines: Int, width: Int = 9): List<String> {
    val out = ArrayList<String>()
    var cur = ""
    for (w in text.split(' ')) {
        if (cur.isEmpty()) cur = w else if (cur.length + 1 + w.length <= width) cur += " $w" else { out += cur; cur = w }
        if (out.size == lines) break
    }
    if (out.size < lines && cur.isNotEmpty()) out += cur
    return out.take(lines).map { it.take(width + 2) }
}

/** Panels open the moment an object is tapped; leaving the scene is always an explicit action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeskWorkspace(desk: DeskState, tasks: List<Intention>, modifier: Modifier = Modifier,
    paused: Boolean = false, fpsTarget: Int = 30, interactive: Boolean = true,
    thermalStatus: Int = com.example.meowmba.core.thermal.ThermalGovernor.currentStatus(),
    onMenu: () -> Unit = {}, onSettings: () -> Unit = {}, onFlow: () -> Unit = {},
    onProjects: () -> Unit = {}, onWeather: () -> Unit = {}, onConnections: () -> Unit = {}, onNotes: () -> Unit = {},
    onTaskDone: (String) -> Unit = {}, onTaskLater: (String) -> Unit = {},
    micOn: Boolean? = null, onMicToggle: () -> Unit = {}, onAttach: (() -> Unit)? = null,
    onClock: () -> Unit = {},
    onNorthStar: (() -> Unit)? = null,
    catActivity: DeskCatActivity = DeskCatActivity.IDLE, catReply: DeskCatReply? = null,
    onAsk: ((String) -> Unit)? = null,
    /** Contents of an opened drawer (true = top); the route supplies the real folder. */
    drawerContent: (@Composable (top: Boolean, close: () -> Unit) -> Unit)? = null,
    /** Side dock, drawn above the scene. */
    dock: (@Composable () -> Unit)? = null) {
    var selected by rememberSaveable { mutableStateOf<DeskObject?>(null) }
    var asking by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        DeskHabitat(desk, Modifier.fillMaxSize(), thermalStatus = thermalStatus,
            topInset = insets.calculateTopPadding() + 8.dp,
            // Portrait: leave room for the Menu · Flow · Settings row under the desk.
            bottomInset = insets.calculateBottomPadding() + if (interactive && !wide) 56.dp else 8.dp,
            fpsTarget = fpsTarget, reduceMotion = paused,
            activity = catActivity, reply = catReply,
            interactive = interactive, wanderEveryMs = if (fpsTarget <= 15) 28_000 else 14_000,
            lineFor = { deskLine(it, desk) }, onTap = {
                // The clock hands straight over to Android's Clock; everything else opens its panel.
                if (interactive) {
                    if (it == DeskObject.CLOCK) onClock()
                    else if (it == DeskObject.PROJECT && onNorthStar != null) onNorthStar()
                    else selected = it
                }
            })
        if (interactive && onAsk != null) FilledTonalButton(onClick = { asking = true },
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp).testTag("desk_ask")) {
            Text("Ask Meow")
        }
        if (interactive) dock?.invoke()
        if (interactive) DeskNavigation(if (wide) Modifier.align(Alignment.CenterStart).safeDrawingPadding()
            else Modifier.align(Alignment.BottomCenter).safeDrawingPadding(), vertical = wide,
            onMenu = onMenu, onFlow = onFlow, onSettings = onSettings,
            micOn = micOn, onMicToggle = onMicToggle, onAttach = onAttach)
    }
    if (asking && onAsk != null) AlertDialog(onDismissRequest = { asking = false },
        title = { Text("Ask Meow") },
        text = { OutlinedTextField(question, { question = it.take(4_000) }, label = { Text("What’s on your mind?") },
            minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth().testTag("desk_question")) },
        confirmButton = { TextButton(enabled = question.isNotBlank() && catActivity != DeskCatActivity.THINKING,
            onClick = { val text = question.trim(); question = ""; asking = false; onAsk(text) }, modifier = Modifier.testTag("desk_question_send")) { Text("Ask") } },
        dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } })
    val obj = selected
    if (obj != null && interactive) ModalBottomSheet(onDismissRequest = { selected = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF193039), contentColor = Color(0xFFF4EEDB),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), dragHandle = null) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("desk_detail")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(deskTitle(obj), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { selected = null }, modifier = Modifier.testTag("desk_detail_close")) {
                    Text("Close", color = Color(0xFFF4EEDB))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFF33505A))
            if (obj == DeskObject.DRAWER_TOP || obj == DeskObject.DRAWER_BOTTOM) {
                drawerContent?.invoke(obj == DeskObject.DRAWER_TOP) { selected = null }
                Spacer(Modifier.height(20.dp))
                return@Column
            }
            val relevant = when (obj) {
                DeskObject.MONEY -> tasks.filter { it.kind == FlowKind.MONEY }
                DeskObject.INBOX -> tasks.filter { it.kind == FlowKind.PEOPLE }
                DeskObject.IDEA -> tasks.filter { it.kind == FlowKind.OTHER || it.kind == FlowKind.ERRAND }
                DeskObject.PROJECT, DeskObject.BUILD -> tasks.filter { it.kind == FlowKind.WORK }
                DeskObject.TODAY -> tasks.sortedBy { it.dueAt ?: Long.MAX_VALUE }
                DeskObject.WINDOW, DeskObject.CLOCK, DeskObject.DRAWER_TOP, DeskObject.DRAWER_BOTTOM -> emptyList()
            }
            val summary = when (obj) {
                DeskObject.WINDOW -> desk.weatherLine ?: "Weather isn't set up yet. Open Weather to choose a location."
                DeskObject.PROJECT -> if (desk.projectTitle.isBlank()) "No project yet. Open Projects to start one." else "${desk.projectTitle} · ${desk.projectStatus}"
                DeskObject.BUILD -> "${tasks.size} open, ${tasks.count { it.kind == FlowKind.PEOPLE }} people waiting."
                DeskObject.TODAY -> if (tasks.isEmpty()) "Nothing waiting. Tell Meow what's on your mind in Flow." else "Everything Meow is keeping in mind, soonest first."
                DeskObject.INBOX -> if (relevant.isEmpty()) "Nobody is waiting on you." else "People waiting on you."
                DeskObject.MONEY -> if (relevant.isEmpty()) "Nothing due." else "Money things coming up."
                DeskObject.IDEA -> if (relevant.isEmpty()) "No ideas or errands waiting." else "Things you asked Meow to keep in mind."
                DeskObject.CLOCK, DeskObject.DRAWER_TOP, DeskObject.DRAWER_BOTTOM -> ""
            }
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFBED0CD))
            relevant.take(12).forEach { task -> DeskTaskRow(task, onTaskDone, onTaskLater) }
            val action = when (obj) {
                DeskObject.WINDOW -> "Open Weather" to onWeather
                DeskObject.PROJECT -> "Open Projects" to onProjects
                DeskObject.BUILD -> "App settings" to onSettings
                DeskObject.INBOX -> "Connections" to onConnections
                DeskObject.IDEA -> "Open Notes" to onNotes
                else -> "Open Flow" to onFlow
            }
            OutlinedButton(onClick = { selected = null; action.second() }, border = BorderStroke(2.dp, Color(0xFF819F9F)),
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 48.dp)) { Text(action.first, color = Color(0xFFF4EEDB)) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DeskTaskRow(task: Intention, onDone: (String) -> Unit, onLater: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).testTag("desk_task_" + task.id)) {
        Text(task.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        task.facts[FlowPolicy.FACT_BECAUSE]?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBED0CD)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onDone(task.id) }) { Text(task.facts[FlowPolicy.FACT_ACTION] ?: "Done", color = Color(0xFFC6EB78)) }
            TextButton(onClick = { onLater(task.id) }) { Text("Later", color = Color(0xFFF4EEDB)) }
        }
        HorizontalDivider(color = Color(0xFF33505A))
    }
}

/** What the cat says when it reaches an object: real content, one line. */
internal fun deskLine(obj: DeskObject, s: DeskState): String = when (obj) {
    DeskObject.INBOX -> if (s.trayCount == 0) "Nobody's waiting on you." else "${s.trayCount} ${if (s.trayCount == 1) "person is" else "people are"} waiting on you."
    DeskObject.MONEY -> s.jarNote?.let { "$it is coming up." } ?: "Nothing due."
    DeskObject.TODAY -> if (s.sticky.firstOrNull() == "All clear") "Nothing next. Nice." else "Next: ${s.sticky.drop(1).joinToString(" ")}"
    DeskObject.PROJECT -> if (s.projectTitle.isBlank()) "No project pinned yet." else "Back to ${s.projectTitle}?"
    DeskObject.BUILD -> s.monitorLines.firstOrNull()?.text?.removePrefix("> ") ?: "All quiet."
    DeskObject.IDEA -> if (s.lampOn) "I'm keeping a few things in mind for you." else "No ideas waiting."
    DeskObject.WINDOW -> s.weatherLine ?: "Weather isn't set up yet."
    DeskObject.CLOCK -> "Opening your clock."
    DeskObject.DRAWER_TOP, DeskObject.DRAWER_BOTTOM -> "Here's what's in the drawer."
}

internal fun deskTitle(obj: DeskObject) = when (obj) {
    DeskObject.WINDOW -> "Weather"
    DeskObject.PROJECT -> "Projects"
    DeskObject.BUILD -> "Work"
    DeskObject.TODAY -> "Next up"
    DeskObject.MONEY -> "Money"
    DeskObject.INBOX -> "People waiting"
    DeskObject.IDEA -> "Ideas & errands"
    DeskObject.CLOCK -> "Clock"
    DeskObject.DRAWER_TOP -> "Top drawer"
    DeskObject.DRAWER_BOTTOM -> "Bottom drawer"
}
