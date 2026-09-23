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
    LaunchedEffect(flow, driving) {
        flow.publish(Evidence(EvidenceType.MOVEMENT, System.currentTimeMillis(), "move",
            if (driving) "Driving" else "Stopped",
            mapOf("activity" to if (driving) Activity.DRIVING.name else Activity.STILL.name)))
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
                                      requestCalendar: () -> Unit) {
    val item = flow.store.intentions.value.firstOrNull { it.id == id }
    when {
        id == AppIntentions.SETUP_NOTIFICATIONS -> context.startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        id == AppIntentions.SETUP_USAGE -> context.startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        id == AppIntentions.SETUP_CALENDAR -> requestCalendar()
        id.startsWith(AppIntentions.PROJECT) ->
            item?.facts?.get("project_id")?.toLongOrNull()?.let(vm::openProject) ?: vm.toggleProjectsScreen()
        id.startsWith(AppIntentions.MESSAGE) -> {
            item?.facts?.get("package")?.let { pkg -> context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startSafely(it) } }
            flow.onPrimary(id)
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
    val snapshot by flow.snapshot.collectAsState()
    val settings by flow.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var adjust by remember { mutableStateOf(false) }
    var compose by remember { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { runCatching { vm.syncFlowAppData() }; flow.controller.schedule(immediate = true) }
    }
    val requestCalendar = { calendarPermission.launch(android.Manifest.permission.READ_CALENDAR) }

    MeowFlowHome(snapshot, settings,
        DateTimeFormatter.ofPattern("EEE d MMM · h:mm a").format(
            Instant.ofEpochMilli(snapshot.at.takeIf { it > 0 } ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault())),
        modifier = Modifier.fillMaxSize(), reduceMotion = paused || state.avatarFpsTarget <= 15,
        speaking = !paused && state.interactionState == InteractionState.SPEAKING,
        onPrimary = { scope.launch { handleFlowPrimary(vm, context, flow, it.intentionId, requestCalendar) } },
        onLater = { scope.launch { flow.onLater(it.intentionId) } },
        onInterruptAccept = { scope.launch { handleFlowPrimary(vm, context, flow, it.item.intentionId, requestCalendar) } },
        onInterruptDecline = { scope.launch { flow.onLater(it.item.intentionId) } },
        onAdjust = { adjust = true }, onAsk = { compose = true }, onMic = vm::onMicClick,
        onMenu = vm::openTiles, onDesk = { vm.setAppTheme(AppTheme.DESK) })

    val paper = FlowColors.from(FlowThemes.palette(settings.theme, snapshot.evening, settings.accent)).paper
    if (adjust) ModalBottomSheet(onDismissRequest = { adjust = false }, dragHandle = null,
        containerColor = paper, shape = RoundedCornerShape(0.dp)) {
        FlowAdjustSheet(settings, "Not enough activity yet. Meow learns your hours as you use the phone.", snapshot.evening,
            onChange = flow::updateSettings, onDone = { adjust = false },
            onAppSettings = { adjust = false; vm.toggleSettings() })
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
            onCancel = { compose = false })
    }
}

/** Paper-style "tell Meow" sheet: keep something in mind, or ask Meow. Replaces the Material dialog. */
@Composable
internal fun FlowComposer(settings: FlowSettings, night: Boolean, onKeep: (String) -> Unit, onAsk: (String) -> Unit, onCancel: () -> Unit) {
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
                .testTag("flow_composer_input"),
                textStyle = FlowType.itemTitle.copy(color = c.ink), cursorBrush = SolidColor(c.ink), minLines = 2)
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
