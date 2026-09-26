package com.example.meowmba.ui.flow

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.meowmba.core.flow.*
import com.example.meowmba.core.flow.sources.AppIntentions
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Collect only in Flow/Desk modes. Minute refresh is foreground-only; no model polling. */
@Composable
internal fun FlowLifecycle(vm: CompanionViewModel, driving: Boolean) {
    val flow = vm.flowRuntime ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(flow) { flow.start(); onDispose { flow.stop() } }
    // Movement: the car assistant's GPS flag, or Android's activity transitions when Car mode is off.
    val live by com.example.meowmba.core.context.live.LiveContext.now.collectAsState()
    val activity = if (driving) Activity.DRIVING else live.activity.takeIf { it != Activity.UNKNOWN } ?: Activity.STILL
    LaunchedEffect(flow, activity) {
        val label = when (activity) { Activity.DRIVING -> "Driving"; Activity.WALKING -> "Walking"; else -> "Stopped" }
        flow.publish(Evidence(EvidenceType.MOVEMENT, System.currentTimeMillis(), "move", label, mapOf("activity" to activity.name)))
    }
    LaunchedEffect(flow, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                runCatching { vm.syncFlowAppData() }   // reminders, project, calendar, connect items
                flow.controller.schedule(immediate = true)
                delay(60_000)
            }
        }
    }
}

/**
 * What an item's primary button does. Items made from app data open the right place;
 * "connect" items open the exact system screen; everything else is marked done.
 */
internal suspend fun handleFlowPrimary(vm: CompanionViewModel, context: Context, flow: FlowRuntime, id: String,
                                      requestPermission: (String) -> Unit) {
    val item = flow.store.intentions.value.firstOrNull { it.id == id }
    when {
        id == AppIntentions.SETUP_NOTIFICATIONS -> context.startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        id == AppIntentions.SETUP_USAGE -> context.startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        id == AppIntentions.SETUP_CALENDAR -> requestPermission(android.Manifest.permission.READ_CALENDAR)
        id == AppIntentions.SETUP_ACTIVITY -> requestPermission(android.Manifest.permission.ACTIVITY_RECOGNITION)
        // Android asks for location while in use first; "all the time" is a second, separate step.
        id == AppIntentions.SETUP_LOCATION -> requestPermission(
            if (com.example.meowmba.core.context.live.ContextSensing.canLocate(context)) android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            else android.Manifest.permission.ACCESS_FINE_LOCATION)
        id.startsWith(AppIntentions.PROJECT) ->
            item?.facts?.get("project_id")?.toLongOrNull()?.let(vm::openProject) ?: vm.toggleProjectsScreen()
        id.startsWith(AppIntentions.MESSAGE) -> {
            val target = item?.facts?.get("notification_key")?.let(com.example.meowmba.core.flow.sources.FlowNotificationHub::target)
            val opened = target?.let {
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= 36) {
                        val options = android.app.ActivityOptions.makeBasic().apply {
                            setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                        }
                        it.send(context, 0, null, null, null, null, options.toBundle())
                    } else if (android.os.Build.VERSION.SDK_INT >= 34) {
                        val options = android.app.ActivityOptions.makeBasic().apply {
                            @Suppress("DEPRECATION")
                            setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        }
                        it.send(context, 0, null, null, null, null, options.toBundle())
                    } else it.send()
                    true
                }.getOrDefault(false)
            } == true
            // Opening a conversation is not proof that the user replied. The original
            // notification's removal closes its Flow row; sending is owned by that app.
            if (!opened) android.widget.Toast.makeText(context, "That conversation is no longer available. Open the messaging app to find it.", android.widget.Toast.LENGTH_LONG).show()
        }
        item?.facts?.get("url") != null -> {
            context.startSafely(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item!!.facts.getValue("url"))))
        }
        id.startsWith(AppIntentions.CALENDAR) -> context.startSafely(Intent(Intent.ACTION_VIEW,
            android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                .appendPath((item?.dueAt ?: System.currentTimeMillis()).toString()).build()))
        else -> flow.onPrimary(id)
    }
    flow.controller.schedule(immediate = true)
}

private fun Context.startSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlowHomeRoute(vm: CompanionViewModel, state: CompanionState, paused: Boolean) {
    val flow = vm.flowRuntime ?: return
    val rawSnapshot by flow.snapshot.collectAsState()
    val stars = vm.northStars
    val starViews = stars?.views?.collectAsState()?.value.orEmpty()
    val intentions by flow.store.intentions.collectAsState()
    val snapshot = stars?.decorate(rawSnapshot, intentions) ?: rawSnapshot
    val settings by flow.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var adjust by remember { mutableStateOf(false) }
    var compose by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    var attachNote by remember { mutableStateOf<String?>(null) }
    var attention by remember { mutableStateOf(false) }
    var northStarOpen by remember { mutableStateOf(false) }
    var accessSetup by remember { mutableStateOf(false) }
    var commentItem by remember { mutableStateOf<Intention?>(null) }
    var commentBusy by remember { mutableStateOf(false) }
    var commentResult by remember { mutableStateOf<String?>(null) }
    val guidedSetupIds = setOf(AppIntentions.SETUP_CALENDAR, AppIntentions.SETUP_NOTIFICATIONS, AppIntentions.SETUP_USAGE)
    val openComment: (FlowItem) -> Unit = { card ->
        commentItem = intentions.firstOrNull { it.id == card.intentionId }
        commentResult = null
    }
    val requestedComment by vm.flowCommentRequest.collectAsState()
    LaunchedEffect(requestedComment, commentBusy) {
        val id = requestedComment ?: return@LaunchedEffect
        if (commentBusy) return@LaunchedEffect
        val item = flow.store.find(id)
        if (item != null && item.status in setOf(IntentionStatus.OPEN, IntentionStatus.SNOOZED)) {
            commentItem = item
            commentResult = null
        } else android.widget.Toast.makeText(context, "That Flow is no longer active. Nothing was changed.", android.widget.Toast.LENGTH_LONG).show()
        vm.consumeFlowCommentRequest(id)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { runCatching { vm.syncFlowAppData() }; flow.controller.schedule(immediate = true) }
    }
    val requestPermission: (String) -> Unit = { p -> permission.launch(p) }

    MeowFlowHome(snapshot, settings,
        DateTimeFormatter.ofPattern("EEE d MMM · h:mm a").format(
            Instant.ofEpochMilli(snapshot.at.takeIf { it > 0 } ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault())),
        modifier = Modifier.fillMaxSize(), reduceMotion = paused || state.avatarFpsTarget <= 15,
        speaking = !paused && state.interactionState == InteractionState.SPEAKING,
        onPrimary = { if (it.intentionId in guidedSetupIds) accessSetup = true
            else scope.launch { handleFlowPrimary(vm, context, flow, it.intentionId, requestPermission) } },
        onLater = { scope.launch { flow.onLater(it.intentionId) } },
        onComment = openComment,
        onInterruptAccept = { if (it.item.intentionId in guidedSetupIds) accessSetup = true
            else scope.launch { handleFlowPrimary(vm, context, flow, it.item.intentionId, requestPermission) } },
        onInterruptDecline = { scope.launch { flow.onLater(it.item.intentionId) } },
        onAdjust = { adjust = true }, onAsk = { compose = true }, onMic = vm::onFlowMicClick,
        onMenu = vm::openTiles, onDesk = { vm.setAppTheme(AppTheme.DESK) },
        voice = flowVoice(state),
        micOn = state.isMicEnabled, onMicToggle = { vm.setMicEnabled(!state.isMicEnabled) },
        onAttach = { attach = true },
        onTopBar = { attention = true },
        northStar = { NorthStarLine(starViews.firstOrNull()) { northStarOpen = true } })

    val flowColors = FlowColors.from(FlowThemes.palette(settings.theme, snapshot.evening, settings.accent))
    val paper = flowColors.paper
    // Six-app side dock, movable; drawn over the page.
    HomeDock(vm, DockStyle.flow(flowColors))
    if (adjust) ModalBottomSheet(onDismissRequest = { adjust = false }, dragHandle = null,
        containerColor = paper, shape = RoundedCornerShape(0.dp)) {
        FlowAdjustSheet(settings, "Not enough activity yet. Meow learns your hours as you use the phone.", snapshot.evening,
            onChange = flow::updateSettings, onDone = { adjust = false },
            onAppSettings = { adjust = false; vm.toggleSettings() },
            thinking = layaThinking(vm),
            speechMode = state.features.speechMode, onSpeechMode = vm::setSpeechMode,
            idleAvatar = state.features.idleAvatar, idleMinutes = state.features.idleAvatarMinutes,
            onIdleAvatar = vm::setIdleAvatar,
            onNorthStars = { adjust = false; northStarOpen = true },
            onAccessSetup = { adjust = false; accessSetup = true })
    }
    if (northStarOpen && stars != null) ModalBottomSheet(onDismissRequest = { northStarOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = paper, contentColor = flowColors.ink) {
        NorthStarSheet(stars, onClose = { northStarOpen = false }, onOpenSource = { id ->
            if (flow.store.intentions.value.any { it.id == id }) {
                northStarOpen = false
                if (id in guidedSetupIds) accessSetup = true
                else scope.launch { handleFlowPrimary(vm, context, flow, id, requestPermission) }
            } else android.widget.Toast.makeText(context, "That item is no longer active. Its saved evidence is shown here.", android.widget.Toast.LENGTH_LONG).show()
        })
    }
    if (attach) AttachSheet(vm, onDismiss = { attach = false; attachNote = null }, note = attachNote)
    if (attention) ModalBottomSheet(onDismissRequest = { attention = false }, dragHandle = null,
        containerColor = paper, shape = RoundedCornerShape(0.dp)) {
        AttentionSheet(needsAttention(snapshot), flowColors,
            onPrimary = { item -> attention = false
                if (item.intentionId in guidedSetupIds) accessSetup = true
                else scope.launch { handleFlowPrimary(vm, context, flow, item.intentionId, requestPermission) } },
            onLater = { item -> scope.launch { flow.onLater(item.intentionId) } },
            onClose = { attention = false },
            onComment = { attention = false; openComment(it) })
    }
    commentItem?.let { original ->
        ModalBottomSheet(onDismissRequest = { if (!commentBusy) commentItem = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
                confirmValueChange = { !commentBusy || it != SheetValue.Hidden })) {
            FlowCommentSheet(original, commentBusy, commentResult, onSubmit = { text ->
                if (!commentBusy) {
                    commentBusy = true
                    scope.launch {
                        try {
                            val current = flow.store.find(original.id)
                            val target = FlowComments.target(original)
                            if (current == null || current.status !in setOf(IntentionStatus.OPEN, IntentionStatus.SNOOZED)) {
                                commentResult = "This Flow is no longer active. Nothing was changed."
                            } else if (FlowComments.requestsCancellation(text)) {
                                if (target == null || FlowComments.target(current) != target) {
                                    commentResult = "This event changed. Refresh Flow and comment on the current event; nothing was changed."
                                } else {
                                    val result = com.example.meowmba.core.agent.tools.CalendarCancelTool(context) { title, at ->
                                        flow.cancelCalendarOccurrence(title, at, target.eventId)
                                    }.cancelOccurrence(target)
                                    commentResult = when (result) {
                                        is com.example.meowmba.core.agent.tools.ToolResult.Success -> result.output
                                        is com.example.meowmba.core.agent.tools.ToolResult.Failure -> result.error
                                    }
                                    if (result is com.example.meowmba.core.agent.tools.ToolResult.Success) vm.syncFlowAppData()
                                }
                            } else {
                                val saved = flow.saveComment(original.id, text)
                                commentResult = if (saved) "Comment saved on this Flow. No calendar change or message was sent."
                                    else "This Flow is no longer available. The comment was not saved."
                                if (saved) commentItem = flow.store.find(original.id)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { commentResult = "Could not finish. Check the event before relying on a change." }
                        finally { commentBusy = false }
                    }
                }
            }, onSetup = { accessSetup = true }, onClose = { commentItem = null })
        }
    }
    if (accessSetup) androidx.compose.ui.window.Dialog(onDismissRequest = { accessSetup = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            AccessSetupGuide(onClose = { accessSetup = false }, onChanged = {
                scope.launch { runCatching { vm.syncFlowAppData() }; flow.controller.schedule(immediate = true) }
            })
        }
    }
    if (compose) ModalBottomSheet(onDismissRequest = { compose = false }, dragHandle = null,
        containerColor = paper, shape = RoundedCornerShape(0.dp)) {
        FlowComposer(settings, snapshot.evening,
            onKeep = { text ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    // Explicit phrasing becomes a typed task; anything else is kept as written.
                    if (flow.captureFromChat(text, now) == null) {
                        val i = Intention("cap-" + UUID.randomUUID().toString().take(8), text, FlowKind.OTHER,
                            "asked me to keep this in mind", now, facts = mapOf(FlowPolicy.FACT_SIGNALS to "need"))
                        flow.store.upsert(i)
                        flow.publish(com.example.meowmba.core.flow.sources.EvidenceMappers.fromCapturedNeed(i.text, now))
                    }
                    compose = false
                }
            },
            onAsk = { text -> compose = false; vm.onUserTextSubmitted(text); vm.openChat() },
            onCancel = { compose = false },
            onAttach = { note -> compose = false; attachNote = note.ifBlank { null }; attach = true },
            onPasteFile = { uri, note -> compose = false
                vm.onAttachment(uri, com.example.meowmba.core.attach.Attachments.Kind.FILE, note.ifBlank { null }) })
    }
}

/** Laya's line in Adjust › Thinking, with the one action that fits right now. */
@Composable
internal fun layaThinking(vm: CompanionViewModel): ThinkingStatus {
    val dl = vm.layaDownloader ?: return ThinkingStatus.RULES_ONLY
    LaunchedEffect(dl) { dl.refresh() }
    val st by dl.status.collectAsState()
    val mb = com.example.meowmba.core.flow.laya.LayaFiles.TOTAL_BYTES / 1_000_000
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var check by remember { mutableStateOf<String?>(null) }
    return when (val x = st) {
        com.example.meowmba.core.flow.laya.LayaDownloader.Status.Ready -> ThinkingStatus(
            "Laya decides on this phone" + (vm.layaEngine?.lastPassMs?.takeIf { it > 0 }?.let { " (last question took ${it} ms)" } ?: "") +
                ". It pauses when the phone is warm and local rules take over. Nothing leaves the phone." +
                (check?.let { "\n$it" } ?: ""),
            ("Check Laya" to {
                check = "Checking against the reference answers…"
                scope.launch {
                    val json = runCatching { context.assets.open("laya/parity.json").bufferedReader().readText() }.getOrNull()
                    val r = json?.let { vm.layaEngine?.check(it) }
                    check = r?.let { "Reference check: ${it.agree} of ${it.total} answers agree, largest shift ${"%.3f".format(it.maxDelta)}, ${it.avgMs} ms per question (loaded in ${it.loadMs / 1000.0} s)." }
                        ?: "Laya could not run on this phone right now (too warm, or its files could not be loaded)."
                }
                Unit
            }).takeIf { check == null || check?.startsWith("Checking") == false })
        com.example.meowmba.core.flow.laya.LayaDownloader.Status.Missing -> ThinkingStatus(
            "Local rules decide. Laya can judge timing better, on this phone only ($mb MB, Wi-Fi).", "Download Laya" to dl::start)
        com.example.meowmba.core.flow.laya.LayaDownloader.Status.NeedsWifi -> ThinkingStatus(
            "Laya downloads on Wi-Fi only ($mb MB). Connect to Wi-Fi and try again.", "Try again" to dl::start)
        is com.example.meowmba.core.flow.laya.LayaDownloader.Status.Downloading -> ThinkingStatus(
            "Downloading Laya: ${x.bytes / 1_000_000} of ${x.total / 1_000_000} MB.", "Cancel" to dl::cancel)
        com.example.meowmba.core.flow.laya.LayaDownloader.Status.Verifying -> ThinkingStatus("Checking Laya's files…")
        is com.example.meowmba.core.flow.laya.LayaDownloader.Status.Failed -> ThinkingStatus(
            "Laya didn't finish downloading: ${x.reason}. Local rules decide meanwhile.", "Retry" to dl::start)
    }
}

/** The ask line mirrors the voice pipeline so a mic tap always shows what is happening. */
internal fun flowVoice(state: CompanionState): FlowVoice = when (state.interactionState) {
    InteractionState.LISTENING -> FlowVoice.Listening(state.captionText?.takeUnless { it.startsWith("Listening") })
    InteractionState.THINKING -> FlowVoice.Thinking
    InteractionState.SPEAKING -> FlowVoice.Speaking(state.captionText)
    InteractionState.MUTED -> FlowVoice.Note("Microphone is off. Tap the mic to turn it on.")
    InteractionState.OFFLINE -> FlowVoice.Note(state.captionText ?: "Meow can't reach its voice right now.")
    InteractionState.IDLE -> FlowVoice.Idle
}

/** What the top bar opens: urgent and worth-it items (and held interruptions), most urgent first. */
internal fun needsAttention(s: FlowSnapshot): List<FlowItem> =
    (listOfNotNull(s.interrupt?.item, s.main) + s.more)
        .distinctBy { it.intentionId }
        .filter { it.urgency.atLeast(Urgency.WORTH_IT) || it.heldReason != null }
        .sortedByDescending { it.urgency.ordinal }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttentionSheet(items: List<FlowItem>, c: FlowColors, onPrimary: (FlowItem) -> Unit,
                            onLater: (FlowItem) -> Unit, onClose: () -> Unit, onComment: (FlowItem) -> Unit = {}) {
    CompositionLocalProvider(LocalFlowColors provides c) {
        Column(Modifier.fillMaxWidth().background(c.paper)
            .drawBehind { drawLine(c.ink, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx()) }
            .padding(start = 28.dp, end = 26.dp, top = 22.dp, bottom = 28.dp).testTag("flow_attention")) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BasicText("Needs attention", Modifier.weight(1f), style = FlowType.headline.copy(fontSize = 26.sp, lineHeight = 32.sp, color = c.ink))
                FlowLink("Close", c.ink2, onClose)
            }
            if (items.isEmpty()) BasicText("Nothing urgent right now. Everything else is waiting quietly in your flow.",
                Modifier.padding(top = 10.dp), style = FlowType.because.copy(color = c.ink2))
            items.forEach { item ->
                Column(Modifier.fillMaxWidth().padding(top = 18.dp)
                    .drawBehind { drawLine(c.rule, Offset(0f, size.height + 9.dp.toPx()), Offset(size.width, size.height + 9.dp.toPx()), 1.dp.toPx()) }) {
                    BasicText((kindLabel(item) + " · " + item.urgency.label).uppercase(),
                        style = FlowType.label.copy(color = if (item.urgency == Urgency.URGENT) c.accent else c.ink2))
                    BasicText(item.headline, Modifier.padding(top = 4.dp), style = FlowType.itemTitle.copy(color = c.ink))
                    item.because.takeIf { it.isNotBlank() }?.let { BasicText(it, Modifier.padding(top = 3.dp), style = FlowType.itemDetail.copy(color = c.ink2)) }
                    item.heldReason?.let { BasicText(it, Modifier.padding(top = 3.dp), style = FlowType.itemDetail.copy(color = c.accent)) }
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowButton(item.primary.label, c.ink, c.paper) { onPrimary(item) }
                        FlowLink("Later", c.ink) { onLater(item) }
                        FlowLink("Comment", c.ink) { onComment(item) }
                    }
                }
            }
        }
    }
}

/** Paper-style "tell Meow" sheet: keep something in mind, or ask Meow. Replaces the Material dialog. */
@Composable
internal fun FlowComposer(settings: FlowSettings, night: Boolean, onKeep: (String) -> Unit, onAsk: (String) -> Unit, onCancel: () -> Unit,
                          onAttach: ((note: String) -> Unit)? = null, onPasteFile: ((android.net.Uri, String) -> Unit)? = null) {
    val c = FlowColors.from(FlowThemes.palette(settings.theme, night, settings.accent))
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CompositionLocalProvider(LocalFlowColors provides c) {
        Column(Modifier.fillMaxWidth().background(c.paper)
            .drawBehind { drawLine(c.ink, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx()) }
            .padding(start = 28.dp, end = 26.dp, top = 22.dp, bottom = 28.dp).imePadding().testTag("flow_composer")) {
            BasicText("Tell Meow", style = FlowType.headline.copy(fontSize = 26.sp, lineHeight = 32.sp, color = c.ink))
            BasicText("“The rear light is out”, “remind me to call Dana”, or anything you want kept in mind.",
                Modifier.padding(top = 8.dp), style = FlowType.because.copy(color = c.ink2))
            BasicTextField(text, { text = it.take(300) }, Modifier.fillMaxWidth().padding(top = 18.dp).focusRequester(focus)
                .drawBehind { drawLine(c.ink, Offset(0f, size.height + 8.dp.toPx()), Offset(size.width, size.height + 8.dp.toPx()), 1.5.dp.toPx()) }
                .then(if (onPasteFile != null) Modifier.acceptPastedFiles { onPasteFile(it, text.trim()) } else Modifier)
                .testTag("flow_composer_input"),
                textStyle = FlowType.itemTitle.copy(color = c.ink), cursorBrush = SolidColor(c.ink), minLines = 2)
            onAttach?.let { attach -> Box(Modifier.padding(top = 18.dp)) { FlowLink("+ Photo, camera or file", c.ink) { attach(text.trim()) } } }
            Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FlowButton("Keep it in mind", c.ink, c.paper) { if (text.isNotBlank()) onKeep(text.trim()) }
                FlowLink("Ask Meow", c.ink) { if (text.isNotBlank()) onAsk(text.trim()) }
                Spacer(Modifier.weight(1f))
                FlowLink("Cancel", c.ink2, onCancel)
            }
        }
    }
}
