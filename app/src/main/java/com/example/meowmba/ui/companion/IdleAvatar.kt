package com.example.meowmba.ui.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.flow.FlowItem
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.ui.flow.homeSwipes
import kotlin.math.roundToInt

/**
 * Idle defaults to Flow (owner, 2026-10-03). Lumen and Orb remain explicit options;
 * their overlays keep Flow/Desk underneath and show new Flow items as a ribbon.
 */
object IdleAvatar {
    const val PREF_MODE = "idle_avatar"
    const val PREF_MINUTES = "idle_avatar_minutes"
    const val DEFAULT_KEY = "flow"
    const val DEFAULT_MINUTES = 2
    val MINUTES = listOf(1, 2, 5, 10)
    val MODES = listOf("flow" to "Flow", "lumen" to "Lumen", "orb" to "Orb", "off" to "Off")

    fun keyOf(stored: String?) = MODES.firstOrNull { it.first == stored }?.first ?: DEFAULT_KEY
    fun styleOf(key: String): AvatarStyle = if (key == "orb") AvatarStyle.SIGNAL else AvatarStyle.LUMEN

    /** New things since [seen]: the interruption first, then the main item, then the rest. */
    fun arrivals(snap: FlowSnapshot, seen: Set<String>): List<FlowItem> =
        (listOfNotNull(snap.interrupt?.item, snap.main) + snap.more)
            .distinctBy { it.intentionId }
            .filter { it.intentionId !in seen && !it.intentionId.startsWith("setup-") }
}

/** Idle navigation never saves over the selected home, or interrupts an open tool or driving avatar. */
@Composable
internal fun IdleHomeTimer(
    theme: AppTheme,
    mode: String = IdleAvatar.DEFAULT_KEY,
    minutes: Int = IdleAvatar.DEFAULT_MINUTES,
    blocked: Boolean = false,
    paused: Boolean = false,
    onFlow: () -> Unit,
    content: @Composable (idleAvatar: Boolean, wake: () -> Unit) -> Unit,
) {
    val key = IdleAvatar.keyOf(mode)
    var touches by remember(theme, key, minutes) { mutableLongStateOf(0L) }
    var idle by remember(theme, key, minutes) { mutableStateOf(false) }
    val wake: () -> Unit = { idle = false; touches++ }
    LaunchedEffect(blocked, paused) { if (blocked || paused) idle = false }
    LaunchedEffect(theme, key, minutes, touches, blocked, paused, idle) {
        val home = theme == AppTheme.FLOW || theme == AppTheme.DESK
        if (!home || key == "off" || idle || blocked || paused || (key == "flow" && theme == AppTheme.FLOW))
            return@LaunchedEffect
        kotlinx.coroutines.delay(minutes.coerceAtLeast(1) * 60_000L)
        if (key == "flow") onFlow() else idle = true
    }
    Box(Modifier.fillMaxSize().pointerInput(theme, key, minutes) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            // Keep an opted-in avatar through pointer-up so its click/swipe action can complete.
            touches++
        }
    }) {
        content(idle && !blocked && !paused, wake)
    }
}

/**
 * The idle avatar over Flow/Desk. Any tap wakes back to the page; left-to-right opens Flow,
 * right-to-left opens chat, up opens apps, down the menu. New Flow items cross the screen as a ribbon.
 */
@Composable
internal fun IdleAvatarLayer(
    avatar: @Composable () -> Unit,
    snapshot: FlowSnapshot?,
    onWake: () -> Unit,
    onFlow: () -> Unit,
    onChat: () -> Unit,
    onApps: () -> Unit,
    onMenu: () -> Unit,
) {
    // Everything already in Flow when the avatar came up is not "new".
    val seen = remember { mutableStateSetOf<String>().also { s -> snapshot?.let { IdleAvatar.arrivals(it, emptySet()).forEach { i -> s += i.intentionId } } } }
    val queue = remember { mutableStateListOf<FlowItem>() }
    LaunchedEffect(snapshot) {
        snapshot ?: return@LaunchedEffect
        IdleAvatar.arrivals(snapshot, seen).forEach { seen += it.intentionId; queue += it }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B1418))
            .homeSwipes(onUp = onApps, onDown = onMenu, onLeft = onChat, onRight = onFlow)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Back to your page", onClick = onWake)
            .semantics { contentDescription = "Meow is resting. Tap to return, swipe right for Flow, left for chat." }
            .testTag("idle_avatar"),
    ) {
        avatar()
        queue.firstOrNull()?.let { item ->
            key(item.intentionId) {
                FlowRibbon(item, Modifier.align(Alignment.TopCenter).padding(top = 96.dp), onDone = { queue.remove(item) }, onOpen = onFlow)
            }
        }
    }
}

/** A paper strip that crosses the screen right to left with the new item; tap it to open Flow. */
@Composable
private fun FlowRibbon(item: FlowItem, modifier: Modifier, onDone: () -> Unit, onOpen: () -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val screenPx = with(LocalDensity.current) { maxWidth.toPx() }
        var widthPx by remember { mutableIntStateOf(0) }
        val x = remember { Animatable(screenPx) }
        LaunchedEffect(widthPx) {
            if (widthPx == 0) return@LaunchedEffect
            // Two passes, slow enough to read (~70 px/s … capped at 12 s a pass).
            repeat(2) {
                x.snapTo(screenPx)
                val distance = screenPx + widthPx
                x.animateTo(-widthPx.toFloat(), tween((distance / 0.12f).roundToInt().coerceIn(6_000, 12_000), easing = LinearEasing))
            }
            onDone()
        }
        Row(
            Modifier.offset { IntOffset(x.value.roundToInt(), 0) }
                .onSizeChanged { widthPx = it.width }
                .wrapContentWidth(unbounded = true)
                .background(Color(0xFFF1ECE3), RoundedCornerShape(6.dp))
                .border(1.5.dp, Color(0xFF1C1B19), RoundedCornerShape(6.dp))
                .clickable(onClickLabel = "Open in Flow", onClick = onOpen)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .semantics { contentDescription = "New in Flow: ${item.headline}" }
                .testTag("flow_ribbon"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(com.example.meowmba.ui.flow.kindLabel(item).uppercase(), style = TextStyle(color = Color(0xFFC23B16),
                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp))
            Spacer(Modifier.width(12.dp))
            BasicText(item.headline, maxLines = 1, softWrap = false,
                style = TextStyle(color = Color(0xFF1C1B19), fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            item.because.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(12.dp))
                BasicText("— $it", maxLines = 1, softWrap = false, style = TextStyle(color = Color(0xFF55524B), fontSize = 16.sp))
            }
        }
    }
}
