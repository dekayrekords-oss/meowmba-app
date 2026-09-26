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
        state.interactionState == InteractionState.LISTENING -> DeskCatActivity.LISTENING
        state.interactionState == InteractionState.SPEAKING -> DeskCatActivity.SPEAKING
        state.interactionState == InteractionState.THINKING || state.computerTaskRunning || state.activeTasks.any { it.status == com.example.meowmba.core.agent.runtime.TaskStatus.RUNNING } -> DeskCatActivity.THINKING
        else -> DeskCatActivity.IDLE
    }
    val replies = remember(vm) { DeskCatReplies(state.activeConversationId, state.deskReplySequence, state.liveAssistantReply) }
    var catReply by remember(vm) { mutableStateOf<DeskCatReply?>(null) }
    LaunchedEffect(state.activeConversationId, state.deskReplySequence, state.liveAssistantReply, catActivity) {
        catReply = replies.observe(state.activeConversationId, state.deskReplySequence, state.deskReplyConversationId, state.deskReplyText,
            state.liveReplyConversationId, state.liveAssistantReply, catActivity)
    }
    val context = LocalContext.current
    // The rooms' stereo, TV and bookshelf follow media sessions only while the Desk is on screen.
    val watcher = remember { com.example.meowmba.core.media.RoomMediaWatcher(context.applicationContext) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, watcher) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) watcher.start()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) watcher.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) watcher.start()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); watcher.stop() }
    }
    val media by watcher.now.collectAsState()
    val studio by com.example.meowmba.core.computer.DevStudioProjects.get(context).projects.collectAsState()
    val facts = deskFacts(now, tasks, state.projects.firstOrNull(), weather?.let { "${it.temperatureF}° ${it.condition.label.lowercase()}" },
        weather?.let { !it.isDay }).let { f -> mainStar?.let {
            f.copy(projectTitle = it.star.title.take(28), projectStatus = it.nextStep.take(55))
        } ?: f }.let { studioFacts(it, studio) }
    val room = roomState(media, studio, now)
    val roomActions = remember(context, watcher, vm) { DeskRoomActions(
        openComputer = vm::openComputerTools,
        openStudio = { ComputerToolsLaunch.next = ComputerToolsPage.DEVELOPMENT; vm.openComputerTools() },
        openArcade = { RoomApps.launchFirst(context, RoomApps.MAME) },
        getArcade = { RoomApps.store(context, RoomApps.MAME.first()) },
        toggle = watcher::togglePlay, next = watcher::next,
        openApp = { kind -> RoomApps.openFor(context, kind, watcher.now.value) },
        allowMedia = { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        manual = { runCatching { context.assets.open("manual/meow_manual.md").bufferedReader().use { it.readText() } }.getOrDefault("") },
    ) }
    val scope = rememberCoroutineScope()
    var attach by remember { mutableStateOf(false) }
    var northStarOpen by remember { mutableStateOf(false) }
    DeskWorkspace(DeskState.from(snap, facts, tasks, room), tasks, modifier, paused = paused, roomActions = roomActions,
        fpsTarget = state.avatarFpsTarget, interactive = interactive,
        catActivity = catActivity, catReply = catReply, onAsk = vm::onUserTextSubmitted,
        onMenu = vm::openTiles, onSettings = vm::toggleSettings, onWork = { vm.openTaskHistory() },
        onFlow = { vm.setAppTheme(AppTheme.FLOW) }, onProjects = vm::toggleProjectsScreen,
        onWeather = vm::toggleWeatherScreen, onConnections = vm::toggleConnectorsScreen, onNotes = vm::toggleVaultScreen,
        onTaskDone = { id -> scope.launch { handleFlowPrimary(vm, context, flow, id) { _ -> vm.setAppTheme(AppTheme.FLOW) } } },
        onTaskLater = { id -> scope.launch { flow.onLater(id) } },
        micOn = state.isMicEnabled, onMicToggle = { vm.setMicEnabled(!state.isMicEnabled) },
        onAttach = { attach = true },
        onClock = { openClock(context) },
        onNorthStar = if (mainStar != null) ({ northStarOpen = true }) else null,
        drawerContent = { top, close ->
            TextButton(onClick = { close(); vm.openTaskHistory() }, modifier = Modifier.testTag("desk_work")) { Text("Meow’s work") }
            DeskDrawerPanel(vm, top, close)
        },
        dock = { HomeDock(vm, DockStyle.DESK) })
    if (attach) AttachSheet(vm, onDismiss = { attach = false })
    if (northStarOpen && stars != null) ModalBottomSheet(onDismissRequest = { northStarOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NorthStarSheet(stars, onClose = { northStarOpen = false }, onOpenSource = { id ->
            if (id.startsWith("task:")) { northStarOpen = false; vm.openTaskHistory(id.removePrefix("task:")) }
            else if (tasks.any { it.id == id }) {
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

/** The Desk computer shows Development Studio projects on its screen and a note on the corkboard. */
internal fun studioFacts(f: DeskFacts, studio: List<com.example.meowmba.core.computer.StudioProject>): DeskFacts =
    if (studio.isEmpty()) f else f.copy(
        monitorLines = studio.take(2).flatMap { listOf(MonitorLine("> " + it.name, true), MonitorLine("  " + it.lastEvent)) },
        pinnedNote = listOf("Studio", "${studio.size} project" + if (studio.size == 1) "" else "s"),
    )

internal fun roomState(media: com.example.meowmba.core.media.RoomNowPlaying, studio: List<com.example.meowmba.core.computer.StudioProject>, now: Long) = RoomState(
    music = media.music?.let { RoomMedia(it.title, it.subtitle, it.playing, it.packageName) },
    video = media.video?.let { RoomMedia(it.title, it.subtitle, it.playing, it.packageName) },
    audiobook = media.audiobook?.let { RoomMedia(it.title, it.subtitle, it.playing, it.packageName) },
    mediaAccess = media.access,
    studio = studio.take(8).map { StudioLine(it.name, "${it.lastEvent} · ${com.example.meowmba.core.computer.DevStudioLog.ago(it.at, now)}") },
)

/** What the living room and bedroom can do. Everything is a user tap; nothing runs by itself. */
internal class DeskRoomActions(
    val openComputer: () -> Unit = {},
    val openStudio: () -> Unit = {},
    /** Opens MAME4droid; false when it isn't installed. */
    val openArcade: () -> Boolean = { false },
    val getArcade: () -> Unit = {},
    val toggle: (com.example.meowmba.core.media.MediaKind) -> Unit = {},
    val next: (com.example.meowmba.core.media.MediaKind) -> Unit = {},
    val openApp: (com.example.meowmba.core.media.MediaKind) -> Unit = {},
    val allowMedia: () -> Unit = {},
    val manual: () -> String = { "" },
)

/** Opening other apps from the rooms. Launcher-visible apps only (manifest <queries>). */
internal object RoomApps {
    val MAME = listOf("com.seleuco.mame4d2024", "com.seleuco.mame4droid", "com.seleuco.mame4all")

    fun launch(context: android.content.Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
            runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        } ?: false

    fun launchFirst(context: android.content.Context, pkgs: List<String>) = pkgs.any { launch(context, it) }

    /** Google Play's page for an app: the user decides whether to install. */
    fun store(context: android.content.Context, pkg: String) {
        val market = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg"))
        val web = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        for (i in listOf(market, web)) if (runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }

    fun openFor(context: android.content.Context, kind: com.example.meowmba.core.media.MediaKind, now: com.example.meowmba.core.media.RoomNowPlaying) {
        val current = when (kind) {
            com.example.meowmba.core.media.MediaKind.MUSIC -> now.music
            com.example.meowmba.core.media.MediaKind.VIDEO -> now.video
            com.example.meowmba.core.media.MediaKind.AUDIOBOOK -> now.audiobook
        }?.packageName
        val fallbacks = when (kind) {
            com.example.meowmba.core.media.MediaKind.VIDEO -> listOf("com.google.android.youtube")
            com.example.meowmba.core.media.MediaKind.AUDIOBOOK -> com.example.meowmba.core.media.RoomMediaWatcher.AUDIOBOOK_APPS
            com.example.meowmba.core.media.MediaKind.MUSIC -> listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.sec.android.app.music")
        }
        if (!launchFirst(context, listOfNotNull(current) + fallbacks))
            android.widget.Toast.makeText(context, "No app for that is installed.", android.widget.Toast.LENGTH_SHORT).show()
    }
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
    dock: (@Composable () -> Unit)? = null,
    onWork: (() -> Unit)? = null,
    roomActions: DeskRoomActions = DeskRoomActions()) {
    var selected by rememberSaveable { mutableStateOf<DeskObject?>(null) }
    var asking by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Only a short (landscape phone) screen puts Menu · Flow · Settings down the side; the
        // unfolded panorama keeps it along the bottom instead of floating over the living room.
        val wide = maxHeight < 480.dp
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
                    // The console hands straight over to the arcade; the panel only explains how to get it.
                    else if (it == DeskObject.ARCADE) { if (!roomActions.openArcade()) selected = it }
                    else if (!it.catOnly) selected = it
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
            if (obj in ROOM_PANELS) {
                DeskRoomPanel(obj, desk, roomActions, close = { selected = null }, onWork = onWork)
                Spacer(Modifier.height(20.dp))
                return@Column
            }
            val o = if (obj == DeskObject.BIG_WINDOW) DeskObject.WINDOW else obj
            val relevant = when (o) {
                DeskObject.MONEY -> tasks.filter { it.kind == FlowKind.MONEY }
                DeskObject.INBOX -> tasks.filter { it.kind == FlowKind.PEOPLE }
                DeskObject.IDEA -> tasks.filter { it.kind == FlowKind.OTHER || it.kind == FlowKind.ERRAND }
                DeskObject.PROJECT, DeskObject.BUILD -> tasks.filter { it.kind == FlowKind.WORK }
                DeskObject.TODAY -> tasks.sortedBy { it.dueAt ?: Long.MAX_VALUE }
                else -> emptyList()
            }
            val summary = when (o) {
                DeskObject.WINDOW -> desk.weatherLine ?: "Weather isn't set up yet. Open Weather to choose a location."
                DeskObject.PROJECT -> if (desk.projectTitle.isBlank()) "No project yet. Open Projects to start one." else "${desk.projectTitle} · ${desk.projectStatus}"
                DeskObject.BUILD -> "${tasks.size} open, ${tasks.count { it.kind == FlowKind.PEOPLE }} people waiting."
                DeskObject.TODAY -> if (tasks.isEmpty()) "Nothing waiting. Tell Meow what's on your mind in Flow." else "Everything Meow is keeping in mind, soonest first."
                DeskObject.INBOX -> if (relevant.isEmpty()) "Nobody is waiting on you." else "People waiting on you."
                DeskObject.MONEY -> if (relevant.isEmpty()) "Nothing due." else "Money things coming up."
                DeskObject.IDEA -> if (relevant.isEmpty()) "No ideas or errands waiting." else "Things you asked Meow to keep in mind."
                else -> ""
            }
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFBED0CD))
            if (obj == DeskObject.BUILD && onWork != null) OutlinedButton(onClick = { selected = null; onWork() },
                modifier = Modifier.testTag("desk_monitor_work")) { Text("Meow’s work") }
            relevant.take(12).forEach { task -> DeskTaskRow(task, onTaskDone, onTaskLater) }
            val action = when (o) {
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
    DeskObject.BUILD -> s.room.studio.firstOrNull()?.let { "${it.name}: ${it.status}" } ?: s.monitorLines.firstOrNull()?.text?.removePrefix("> ") ?: "All quiet."
    DeskObject.IDEA -> if (s.lampOn) "I'm keeping a few things in mind for you." else "No ideas waiting."
    DeskObject.WINDOW -> s.weatherLine ?: "Weather isn't set up yet."
    DeskObject.CLOCK -> "Opening your clock."
    DeskObject.DRAWER_TOP, DeskObject.DRAWER_BOTTOM -> "Here's what's in the drawer."
    DeskObject.BIG_WINDOW -> s.weatherLine?.let { "$it. Nice view." } ?: "Nice view."
    DeskObject.ARCADE -> "Player one, ready."
    DeskObject.TOWER_BED -> "Best seat in the house."
    DeskObject.BED -> "Nap time."
    DeskObject.FOOD -> "Snack break!"
    DeskObject.LITTER -> "A little privacy, please."
    DeskObject.BOOKSHELF -> s.room.audiobook?.let { "Listening to ${it.title}." } ?: "Audiobooks, and the Meow manual."
    DeskObject.BIG_TV -> s.room.video?.let { "On YouTube: ${it.title}" } ?: "Nothing on YouTube right now."
    DeskObject.STEREO -> s.room.music?.let { "♪ ${it.title}${it.subtitle?.let { a -> " · $a" } ?: ""}" } ?: "The stereo's quiet."
    DeskObject.COUCH -> "Couch time."
    DeskObject.TOWER_LIVING -> "Up I go!"
}

internal fun deskTitle(obj: DeskObject) = when (obj) {
    DeskObject.WINDOW -> "Weather"
    DeskObject.PROJECT -> "Projects"
    DeskObject.TODAY -> "Next up"
    DeskObject.MONEY -> "Money"
    DeskObject.INBOX -> "People waiting"
    DeskObject.IDEA -> "Ideas & errands"
    DeskObject.CLOCK -> "Clock"
    DeskObject.DRAWER_TOP -> "Top drawer"
    DeskObject.DRAWER_BOTTOM -> "Bottom drawer"
    DeskObject.BUILD -> "Computer"
    DeskObject.BIG_WINDOW -> "Weather"
    DeskObject.ARCADE -> "Arcade"
    DeskObject.BOOKSHELF -> "Bookshelf"
    DeskObject.BIG_TV -> "TV"
    DeskObject.STEREO -> "Stereo"
    else -> obj.label
}

private val ROOM_PANELS = setOf(DeskObject.BUILD, DeskObject.STEREO, DeskObject.BIG_TV, DeskObject.BOOKSHELF, DeskObject.ARCADE)
private val SHEET_TEXT = Color(0xFFF4EEDB)
private val SHEET_MUTE = Color(0xFFBED0CD)

/** Panels for the computer, the stereo, the TV, the bookshelf and the arcade. */
@Composable
private fun DeskRoomPanel(obj: DeskObject, desk: DeskState, actions: DeskRoomActions, close: () -> Unit, onWork: (() -> Unit)?) {
    val r = desk.room
    when (obj) {
        DeskObject.BUILD -> {
            Text(if (r.studio.isEmpty()) "No Development Studio projects yet. Open Development Studio and name a project; it shows up here."
                else "Development Studio projects, most recent first.", style = MaterialTheme.typography.bodyLarge, color = SHEET_MUTE)
            r.studio.forEach { p ->
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("desk_studio_" + p.name)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(p.status, style = MaterialTheme.typography.bodyMedium, color = SHEET_MUTE)
                }
                HorizontalDivider(Modifier.padding(top = 8.dp), color = Color(0xFF33505A))
            }
            Button(onClick = { close(); actions.openStudio() }, modifier = Modifier.padding(top = 20.dp).fillMaxWidth().heightIn(min = 48.dp)
                .testTag("desk_open_studio")) { Text("Development Studio") }
            OutlinedButton(onClick = { close(); actions.openComputer() }, border = BorderStroke(2.dp, Color(0xFF819F9F)),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 48.dp).testTag("desk_open_computer")) { Text("Open Computer", color = SHEET_TEXT) }
            if (onWork != null) TextButton(onClick = { close(); onWork() }, modifier = Modifier.padding(top = 4.dp).testTag("desk_monitor_work")) { Text("Meow’s work", color = SHEET_TEXT) }
        }
        DeskObject.STEREO -> MediaPanel(com.example.meowmba.core.media.MediaKind.MUSIC, r.music, r.mediaAccess, "Nothing is playing. Start music in any app and the stereo shows it.", actions)
        DeskObject.BIG_TV -> MediaPanel(com.example.meowmba.core.media.MediaKind.VIDEO, r.video, r.mediaAccess, "Nothing on YouTube right now. Play a video and the TV shows it.", actions)
        DeskObject.BOOKSHELF -> {
            MediaPanel(com.example.meowmba.core.media.MediaKind.AUDIOBOOK, r.audiobook, r.mediaAccess, "No audiobook playing.", actions)
            var reading by rememberSaveable { mutableStateOf(false) }
            OutlinedButton(onClick = { reading = !reading }, border = BorderStroke(2.dp, Color(0xFF819F9F)),
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 48.dp).testTag("desk_manual")) {
                Text(if (reading) "Close the manual" else "Read the Meow manual", color = SHEET_TEXT)
            }
            if (reading) ManualText(remember { actions.manual() })
        }
        DeskObject.ARCADE -> {
            Text("The old console opens the MAME arcade (MAME4droid). It isn’t installed on this phone yet. MAME4droid is free on Google Play; you’ll need your own game files.",
                style = MaterialTheme.typography.bodyLarge, color = SHEET_MUTE)
            Button(onClick = { close(); actions.getArcade() }, modifier = Modifier.padding(top = 20.dp).fillMaxWidth().heightIn(min = 48.dp)
                .testTag("desk_get_arcade")) { Text("View MAME4droid on Google Play") }
        }
        else -> Unit
    }
}

@Composable
private fun MediaPanel(kind: com.example.meowmba.core.media.MediaKind, now: RoomMedia?, access: Boolean, empty: String, actions: DeskRoomActions) {
    if (!access) {
        Text("To show what's playing, Meow needs Android's notification access. Meow reads media titles only while this room is on screen, and never uses the microphone for this.",
            style = MaterialTheme.typography.bodyLarge, color = SHEET_MUTE)
        Button(onClick = actions.allowMedia, modifier = Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 48.dp)) { Text("Allow media info") }
        return
    }
    if (now == null) Text(empty, style = MaterialTheme.typography.bodyLarge, color = SHEET_MUTE)
    else Column(Modifier.testTag("desk_now_playing")) {
        Text(if (now.playing) "Playing" else "Paused", style = MaterialTheme.typography.labelLarge, color = Color(0xFFC6EB78))
        Text(now.title, style = MaterialTheme.typography.titleLarge)
        now.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = SHEET_MUTE) }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.toggle(kind) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (now.playing) "Pause" else "Play") }
            if (kind != com.example.meowmba.core.media.MediaKind.AUDIOBOOK)
                OutlinedButton(onClick = { actions.next(kind) }, border = BorderStroke(2.dp, Color(0xFF819F9F)), modifier = Modifier.heightIn(min = 48.dp)) { Text("Next", color = SHEET_TEXT) }
        }
    }
    val app = when (kind) { com.example.meowmba.core.media.MediaKind.MUSIC -> "Open music"; com.example.meowmba.core.media.MediaKind.VIDEO -> "Open YouTube"; else -> "Open audiobooks" }
    OutlinedButton(onClick = { actions.openApp(kind) }, border = BorderStroke(2.dp, Color(0xFF819F9F)),
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp)) { Text(app, color = SHEET_TEXT) }
}

/** The manual's small Markdown subset: # headings, bullets and **bold**. */
@Composable
private fun ManualText(md: String) {
    Column(Modifier.padding(top = 12.dp).testTag("desk_manual_text")) {
        md.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(bold(if (line.startsWith("- ")) "•  " + line.removePrefix("- ") else line), style = MaterialTheme.typography.bodyMedium, color = SHEET_MUTE)
            }
        }
    }
}

private fun bold(text: String) = androidx.compose.ui.text.buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) with(this) { pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = SHEET_TEXT)); append(part); pop() }
        else append(part)
    }
}
