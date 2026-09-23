package com.example.meowmba.ui.flow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meowmba.core.flow.FlowInterrupt
import com.example.meowmba.core.flow.FlowItem
import com.example.meowmba.core.flow.FlowKind
import com.example.meowmba.core.flow.FlowSettings
import com.example.meowmba.core.flow.FlowSnapshot
import com.example.meowmba.core.flow.FlowThemes
import com.example.meowmba.core.flow.Signal
import com.example.meowmba.core.flow.Urgency
import com.example.meowmba.core.flow.WorkState
import java.util.Locale

/** What the band shows after the user answered it (or the chance passed). */
data class BandResolution(val message: String)

/**
 * Meow Flow home mode (AppTheme.FLOW). Stateless: the caller supplies the snapshot from
 * FlowController and handles every action. Matches docs/design/meow-flow.html and
 * docs/design/README.md; screenshots in docs/design/screens/.
 *
 * Heat: everything is static between snapshots. The only animations are one-shot
 * (theme cross-fade, item enter, leader-line draw + travelling dot, band slide) and the
 * voice bars while [speaking] is true.
 */
@Composable
fun MeowFlowHome(
    snapshot: FlowSnapshot,
    settings: FlowSettings,
    dateLabel: String,
    modifier: Modifier = Modifier,
    distanceRemainingMeters: Int? = null,
    speaking: Boolean = false,
    bandResolution: BandResolution? = null,
    reduceMotion: Boolean = false,
    onPrimary: (FlowItem) -> Unit = {},
    onLater: (FlowItem) -> Unit = {},
    onInterruptAccept: (FlowInterrupt) -> Unit = {},
    onInterruptDecline: (FlowInterrupt) -> Unit = {},
    onAdjust: () -> Unit = {},
    onAsk: () -> Unit = {},
    onMic: () -> Unit = {},
) {
    val night = FlowThemes.isNight(settings.eveningLook, if (snapshot.evening) WorkState.OFF else WorkState.WORKING)
    val target = FlowColors.from(FlowThemes.palette(settings.theme, night, settings.accent))
    val colors = animatedColors(target, if (reduceMotion) 0 else FlowDims.THEME_ANIM_MS)

    CompositionLocalProvider(LocalFlowColors provides colors) {
        val anchors = remember { mutableStateMapOf<String, LayoutCoordinates>() }
        var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var bandHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        val interrupt = snapshot.interrupt
        val pushed = interrupt != null
        val streamTop by animateDpAsState(
            if (pushed) bandHeight + 14.dp else 12.dp,
            tween(if (reduceMotion) 0 else FlowDims.BAND_ANIM_MS, easing = BAND_EASING), label = "streamTop",
        )

        Box(
            modifier
                .fillMaxSize()
                .background(colors.paper)
                .onGloballyPositioned { root = it }
                .testTag("flow_home"),
        ) {
            LeaderLines(snapshot, anchors, root, reduceMotion)

            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = FlowDims.sidePadStart, end = FlowDims.sidePadEnd, top = streamTop,
                        bottom = FlowDims.streamBottom),
            ) {
                Dateline(dateLabel, snapshot.signals, anchors, onAdjust)
                snapshot.main?.let { main ->
                    key(main.intentionId) { MainThought(main, pushed, anchors, reduceMotion, onPrimary, onLater) }
                }
                if (snapshot.main == null && snapshot.more.isEmpty()) {
                    BasicText("Nothing waiting.\nUse Add task to keep something in mind.",
                        Modifier.padding(top = 48.dp), style = FlowType.itemDetail.copy(color = colors.ink2))
                }
                Spacer(Modifier.weight(1f))
                if (snapshot.more.isNotEmpty()) {
                    FlowList(snapshot.more, snapshot.evening, pushed, anchors, reduceMotion, onPrimary)
                }
            }

            AskLine(Modifier.align(Alignment.BottomCenter), onAsk, onMic)

            AnimatedVisibility(
                visible = interrupt != null,
                enter = slideInVertically(tween(if (reduceMotion) 0 else FlowDims.BAND_ANIM_MS, easing = BAND_EASING)) { -it },
                exit = slideOutVertically(tween(if (reduceMotion) 0 else FlowDims.BAND_ANIM_MS, easing = BAND_EASING)) { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                val last = remember { mutableStateOf(interrupt) }
                if (interrupt != null) last.value = interrupt
                last.value?.let { shown ->
                    InterruptBand(
                        shown, distanceRemainingMeters, speaking, bandResolution,
                        Modifier.onSizeChanged { with(density) { bandHeight = it.height.toDp() } },
                        onInterruptAccept, onInterruptDecline,
                    )
                }
            }
        }
    }
}

private val BAND_EASING = CubicBezierEasing(.2f, .9f, .2f, 1f)

@Composable
private fun animatedColors(t: FlowColors, ms: Int): FlowColors {
    val spec = tween<Color>(ms)
    return FlowColors(
        animateColorAsState(t.paper, spec, label = "paper").value,
        animateColorAsState(t.ink, spec, label = "ink").value,
        animateColorAsState(t.ink2, spec, label = "ink2").value,
        animateColorAsState(t.ink3, spec, label = "ink3").value,
        animateColorAsState(t.rule, spec, label = "rule").value,
        animateColorAsState(t.accent, spec, label = "accent").value,
        animateColorAsState(t.onAccent, spec, label = "onAccent").value,
    )
}

// ---- dateline ------------------------------------------------------------------------------

@Composable
private fun Dateline(dateLabel: String, signals: List<Signal>, anchors: MutableMap<String, LayoutCoordinates>, onAdjust: () -> Unit) {
    val c = LocalFlowColors.current
    Column(Modifier.fillMaxWidth().drawBehind {
        val y = size.height - 0.75.dp.toPx()
        drawLine(c.ink, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
    }.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(dateLabel.uppercase(Locale.ROOT), Modifier.weight(1f), style = FlowType.dateline.copy(color = c.ink))
            BasicText(
                "ADJUST",
                Modifier
                    .clickable(role = Role.Button, onClickLabel = "Adjust Meow Flow", onClick = onAdjust)
                    .underline(c.ink, 1.dp, 0.dp)
                    .testTag("flow_adjust"),
                style = FlowType.dateline.copy(color = c.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            )
        }
        if (signals.isNotEmpty()) SignalLine(signals, anchors)
    }
}

/** One line only: shrink 12 → 10 sp in 0.5 sp steps, then ellipsize (README "Dateline line 2"). */
@Composable
private fun SignalLine(signals: List<Signal>, anchors: MutableMap<String, LayoutCoordinates>) {
    val c = LocalFlowColors.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        val density = LocalDensity.current
        val sepPx = with(density) { 10.dp.roundToPx() }
        var size = 12f
        fun width(sz: Float): Int {
            val st = FlowType.dateline.copy(fontSize = sz.sp)
            return signals.sumOf { measurer.measure(it.label.uppercase(Locale.ROOT), st).size.width } +
                (signals.size - 1) * (measurer.measure("/", st).size.width + sepPx)
        }
        while (size > FlowDims.DATELINE_MIN_SP && width(size) > maxPx) size -= 0.5f
        val style = FlowType.dateline.copy(fontSize = size.sp)
        Row(Modifier.fillMaxWidth().semantics { contentDescription = "Right now: " + signals.joinToString(", ") { it.label } }) {
            signals.forEachIndexed { i, s ->
                if (i > 0) BasicText("/", Modifier.padding(horizontal = 5.dp), style = style.copy(color = c.ink3))
                val last = i == signals.lastIndex
                BasicText(
                    s.label.uppercase(Locale.ROOT),
                    (if (last) Modifier.weight(1f, fill = false) else Modifier)
                        .onGloballyPositioned { anchors["sig:" + s.key] = it }
                        .then(if (s.fresh) Modifier.underline(c.accent, 2.dp, 0.dp) else Modifier),
                    style = style.copy(color = if (s.fresh) c.accent else c.ink2),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- main thought ----------------------------------------------------------------------------

internal fun kindLabel(item: FlowItem, evening: Boolean = false): String = when (item.kind) {
    FlowKind.WORK -> "Work"
    FlowKind.MONEY -> "Money"
    FlowKind.PEOPLE -> "People"
    FlowKind.HOME -> "Home"
    FlowKind.ERRAND -> "On the way"
    FlowKind.LEISURE -> if (evening) "Tonight" else "After work"
    FlowKind.HEALTH -> "Health"
    FlowKind.OTHER -> "For you"
}

@Composable
private fun MainThought(
    item: FlowItem, compact: Boolean, anchors: MutableMap<String, LayoutCoordinates>, reduceMotion: Boolean,
    onPrimary: (FlowItem) -> Unit, onLater: (FlowItem) -> Unit,
) {
    val c = LocalFlowColors.current
    Column(Modifier.padding(top = if (compact) 18.dp else 30.dp).enterAnimation(0, reduceMotion).testTag("flow_main")) {
        BasicText(
            kindLabel(item).uppercase(Locale.ROOT),
            Modifier.onGloballyPositioned { anchors["main:" + item.intentionId] = it },
            style = FlowType.label.copy(color = if (item.urgency == Urgency.URGENT) c.accent else c.ink2),
        )
        BasicText(item.headline, Modifier.padding(top = 8.dp).semantics { heading() }.testTag("flow_main_headline"),
            style = (if (compact) FlowType.headlineCompact else FlowType.headline).copy(color = c.ink))
        if (!compact && item.because.isNotBlank()) {
            BasicText(item.because, Modifier.padding(top = 12.dp), style = FlowType.because.copy(color = c.ink2))
        }
        Row(Modifier.padding(top = if (compact) 14.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FlowButton(item.primary.label, c.ink, c.paper) { onPrimary(item) }
            item.secondary?.let { FlowLink(it.label, c.ink) { onLater(item) } }
        }
    }
}

@Composable
internal fun FlowButton(label: String, fill: Color, text: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(FlowDims.buttonHeight)
            .clip(RoundedCornerShape(FlowDims.radius))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = FlowType.button.copy(color = text)) }
}

@Composable
internal fun FlowLink(label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.height(48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        BasicText(label, Modifier.underline(color, 1.dp, 4.dp), style = FlowType.link.copy(color = color))
    }
}

// ---- more items ------------------------------------------------------------------------------

@Composable
private fun FlowList(
    items: List<FlowItem>, evening: Boolean, compact: Boolean, anchors: MutableMap<String, LayoutCoordinates>,
    reduceMotion: Boolean, onPrimary: (FlowItem) -> Unit,
) {
    val c = LocalFlowColors.current
    Column(Modifier.fillMaxWidth().testTag("flow_more")) {
        BasicText(
            (if (evening) "If you feel like it" else "Also for you").uppercase(Locale.ROOT),
            Modifier.fillMaxWidth().ruleBelow(c.rule, 1.dp).padding(bottom = 8.dp),
            style = FlowType.label.copy(color = c.ink2),
        )
        items.forEachIndexed { index, item ->
            key(item.intentionId) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .enterAnimation(200 + index * FlowDims.ITEM_STAGGER_MS, reduceMotion)
                        .ruleBelow(c.rule, 1.dp)
                        .padding(vertical = 13.dp)
                        .semantics(mergeDescendants = true) {}
                        .testTag("flow_item_" + item.intentionId),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        item.heldReason?.let {
                            BasicText(it.uppercase(Locale.ROOT), Modifier.padding(bottom = 3.dp), style = FlowType.held.copy(color = c.accent))
                        }
                        Box {
                            // Leader-line dot, 22 dp left of the text, centred on the title's first line.
                            Box(
                                Modifier
                                    .offset(x = -FlowDims.dotInset, y = 8.5.dp)
                                    .size(FlowDims.dotSize)
                                    .clip(CircleShape)
                                    .background(c.ink)
                                    .onGloballyPositioned { anchors["item:" + item.intentionId] = it },
                            )
                            BasicText(item.headline, style = FlowType.itemTitle.copy(color = c.ink))
                        }
                        if (!compact && item.because.isNotBlank()) {
                            BasicText(item.because, Modifier.padding(top = 2.dp), style = FlowType.itemDetail.copy(color = c.ink2))
                        }
                    }
                    Box(Modifier.height(32.dp).clickable(role = Role.Button, onClickLabel = item.primary.label) { onPrimary(item) },
                        contentAlignment = Alignment.CenterEnd) {
                        BasicText(item.primary.label + " →", style = FlowType.itemAction.copy(color = c.ink))
                    }
                }
            }
        }
    }
}

// ---- ask line ------------------------------------------------------------------------------

@Composable
private fun AskLine(modifier: Modifier, onAsk: () -> Unit, onMic: () -> Unit) {
    val c = LocalFlowColors.current
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = FlowDims.sidePadStart, end = FlowDims.sidePadEnd, bottom = FlowDims.askBottom)
            .fillMaxWidth()
            .height(FlowDims.askHeight)
            .drawBehind { drawLine(c.ink, Offset(0f, 0.75.dp.toPx()), Offset(size.width, 0.75.dp.toPx()), 1.5.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText("Ask, or tell me what you're doing…",
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Ask Meow", onClick = onAsk),
            style = FlowType.ask.copy(color = c.ink2))
        Box(
            Modifier.size(FlowDims.micSize).clip(CircleShape).border(1.5.dp, c.ink, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Talk to Meow", onClick = onMic).testTag("flow_mic"),
            contentAlignment = Alignment.Center,
        ) { MicGlyph(c.ink) }
    }
}

@Composable
private fun MicGlyph(color: Color) {
    Canvas(Modifier.size(width = 18.dp, height = 22.dp)) {
        val s = 1.8.dp.toPx()
        val u = size.width / 18f
        drawRoundRect(color, topLeft = Offset(5 * u, 1 * u), size = androidx.compose.ui.geometry.Size(8 * u, 13 * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4 * u), style = Stroke(s))
        drawArc(color, 0f, 180f, false, topLeft = Offset(1.5f * u, 3f * u), size = androidx.compose.ui.geometry.Size(15 * u, 15 * u),
            style = Stroke(s, cap = StrokeCap.Round))
        drawLine(color, Offset(9 * u, 18 * u), Offset(9 * u, 21 * u), s, StrokeCap.Round)
    }
}

// ---- interruption band -----------------------------------------------------------------------

@Composable
private fun InterruptBand(
    interrupt: FlowInterrupt, remainingMeters: Int?, speaking: Boolean, resolution: BandResolution?,
    modifier: Modifier, onAccept: (FlowInterrupt) -> Unit, onDecline: (FlowInterrupt) -> Unit,
) {
    val c = LocalFlowColors.current
    val item = interrupt.item
    Column(
        modifier
            .fillMaxWidth()
            .background(c.accent)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = FlowDims.bandSidePad, end = FlowDims.bandSidePad, top = 12.dp, bottom = FlowDims.bandBottomPad)
            .semantics(mergeDescendants = false) { contentDescription = "Meow: " + item.headline }
            .testTag("flow_band"),
    ) {
        val kind = if (interrupt.countdown != null) "Stop here?" else kindLabel(item)
        BasicText("$kind · ${item.urgency.label}".uppercase(Locale.ROOT), Modifier.alpha(0.8f), style = FlowType.label.copy(color = c.onAccent))
        BasicText(item.headline, Modifier.padding(top = 8.dp).semantics { heading() }, style = FlowType.bandHeadline.copy(color = c.onAccent))
        BasicText(resolution?.message ?: item.because, Modifier.padding(top = 10.dp).alpha(0.92f), style = FlowType.bandBody.copy(color = c.onAccent))
        if (resolution == null) {
            interrupt.spokenLine?.let { line -> VoiceCaption(line, speaking, c.onAccent) }
            val cd = interrupt.countdown
            if (cd != null && remainingMeters != null) Distance(remainingMeters, cd.startMeters, c.onAccent)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                FlowButton(item.primary.label, c.onAccent, c.accent) { onAccept(interrupt) }
                FlowLink("Later", c.onAccent) { onDecline(interrupt) }
            }
        }
    }
}

@Composable
private fun VoiceCaption(line: String, speaking: Boolean, color: Color) {
    Row(Modifier.padding(top = 12.dp).drawBehind {
        drawLine(color, Offset(0f, 2.dp.toPx()), Offset(0f, size.height - 2.dp.toPx()), 2.dp.toPx())
    }.padding(start = 14.dp), verticalAlignment = Alignment.Top) {
        if (speaking) VoiceBars(color)
        BasicText(if (speaking) "Speaking: \"$line\"" else line, style = FlowType.bandVoice.copy(color = color))
    }
}

@Composable
private fun VoiceBars(color: Color) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { while (true) { t.animateTo(1f, tween(900, easing = LinearEasing)); t.snapTo(0f) } }
    Canvas(Modifier.padding(top = 5.dp, end = 8.dp).size(width = 15.dp, height = 12.dp)) {
        repeat(3) { i ->
            val phase = ((t.value + i * 0.17f) % 1f)
            val h = size.height * (0.55f + 0.45f * kotlin.math.sin(phase * Math.PI).toFloat())
            val x = i * 5.dp.toPx() + 1.5.dp.toPx()
            drawLine(color, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), 3.dp.toPx())
        }
    }
}

@Composable
private fun Distance(remainingMeters: Int, startMeters: Int, color: Color) {
    val feet = (remainingMeters * 3.28084).toInt().coerceAtLeast(0)
    val fraction = if (startMeters <= 0) 1f else (1f - remainingMeters.toFloat() / startMeters).coerceIn(0f, 1f)
    Row(Modifier.padding(top = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(if (feet > 0) "$feet ft" else "passed", style = FlowType.distance.copy(color = color))
        Canvas(Modifier.weight(1f).height(15.dp)) {
            val y = size.height / 2
            val dot = 3.dp.toPx()
            var x = 0f
            while (x < size.width - 4.dp.toPx()) { drawCircle(color, dot / 2, Offset(x + dot / 2, y)); x += dot * 2 }
            drawCircle(color, 4.5.dp.toPx(), Offset(size.width * fraction, y))
            drawLine(color, Offset(size.width - 1.dp.toPx(), 0f), Offset(size.width - 1.dp.toPx(), size.height), 2.dp.toPx())
        }
        BasicText("store", style = FlowType.distance.copy(color = color))
    }
}

// ---- leader lines ------------------------------------------------------------------------------

private data class Line(val path: Path, val hot: Boolean, val delayMs: Int)

/**
 * Thin ink lines from a dateline signal, down a lane on the left edge, to the item it caused
 * (README "Leader lines"). Drawn in once per snapshot with a travelling dot, then static.
 */
@Composable
private fun LeaderLines(
    snapshot: FlowSnapshot, anchors: Map<String, LayoutCoordinates>, root: LayoutCoordinates?, reduceMotion: Boolean,
) {
    val c = LocalFlowColors.current
    val progress = remember { Animatable(1f) }
    val total = (1 + (snapshot.more.size)) * FlowDims.LINE_STAGGER_ITEM_MS + 3 * FlowDims.LINE_STAGGER_SOURCE_MS + FlowDims.LINE_DRAW_MS + 300
    LaunchedEffect(snapshot.main?.intentionId, snapshot.more.map { it.intentionId }, snapshot.signals.map { it.key }, reduceMotion) {
        if (reduceMotion) { progress.snapTo(1f); return@LaunchedEffect }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(total, easing = LinearEasing))
    }
    Canvas(Modifier.fillMaxSize()) {
        val r = root ?: return@Canvas
        if (!r.isAttached) return@Canvas
        fun box(key: String): Rect? = anchors[key]?.takeIf { it.isAttached }?.let { r.localBoundingBoxOf(it) }
        val signalKeys = snapshot.signals.map { it.key }
        val hotKeys = snapshot.signals.filter { it.fresh }.map { it.key }.toSet()
        val lines = ArrayList<Line>()
        val targets = listOfNotNull(snapshot.main?.let { "main:" + it.intentionId to it }) +
            snapshot.more.map { "item:" + it.intentionId to it }
        targets.forEachIndexed { i, (anchorKey, item) ->
            val t = box(anchorKey) ?: return@forEachIndexed
            val isMain = anchorKey.startsWith("main:")
            val tx = if (isMain) t.left - 6.dp.toPx() else t.center.x
            val ty = t.center.y
            item.sources.forEachIndexed { j, key ->
                val sIdx = signalKeys.indexOf(key)
                val s = box("sig:$key") ?: return@forEachIndexed
                val sx = s.left + minOf(14.dp.toPx(), s.width / 2)
                val sy = s.bottom - 2.dp.toPx()
                val lane = (11 + 4 * sIdx.coerceAtLeast(0)).dp.toPx()
                val path = Path().apply {
                    moveTo(sx, sy); lineTo(sx, sy + 8.dp.toPx())
                    quadraticBezierTo(sx, sy + 16.dp.toPx(), sx - 8.dp.toPx(), sy + 16.dp.toPx())
                    lineTo(lane + 6.dp.toPx(), sy + 16.dp.toPx())
                    quadraticBezierTo(lane, sy + 16.dp.toPx(), lane, sy + 22.dp.toPx())
                    lineTo(lane, ty - 6.dp.toPx())
                    quadraticBezierTo(lane, ty, lane + 6.dp.toPx(), ty)
                    lineTo(tx, ty)
                }
                lines += Line(path, key in hotKeys, i * FlowDims.LINE_STAGGER_ITEM_MS + j * FlowDims.LINE_STAGGER_SOURCE_MS)
            }
        }
        val now = progress.value * total
        val measure = PathMeasure()
        lines.forEach { line ->
            val color = if (line.hot) c.accent else c.ink3
            val width = (if (line.hot) 1.4.dp else 1.dp).toPx()
            val f = ((now - line.delayMs) / FlowDims.LINE_DRAW_MS).coerceIn(0f, 1f)
            if (f <= 0f) return@forEach
            if (f >= 1f) {
                drawPath(line.path, color, style = Stroke(width))
            } else {
                measure.setPath(line.path, false)
                val seg = Path()
                measure.getSegment(0f, measure.length * f, seg, true)
                drawPath(seg, color, style = Stroke(width))
            }
            // One travelling dot per line, once (starts 300 ms after the line begins).
            val tf = ((now - line.delayMs - 300) / FlowDims.TRAVEL_MS)
            if (tf in 0f..1f) {
                measure.setPath(line.path, false)
                drawCircle(c.ink, 2.4.dp.toPx(), measure.getPosition(measure.length * tf))
            }
        }
    }
}

// ---- small modifiers ---------------------------------------------------------------------------

private fun Modifier.underline(color: Color, thickness: Dp, offset: Dp) = drawBehind {
    val y = size.height + offset.toPx() - thickness.toPx() / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), thickness.toPx())
}

private fun Modifier.ruleBelow(color: Color, thickness: Dp) = drawBehind {
    val y = size.height - thickness.toPx() / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), thickness.toPx())
}

/** Fade + 8 dp rise, 600 ms (README "Motion rules"). */
@Composable
private fun Modifier.enterAnimation(delayMs: Int, reduceMotion: Boolean): Modifier {
    if (reduceMotion) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(FlowDims.ITEM_IN_MS, delayMillis = delayMs)) }
    return this.alpha(a.value).offset { androidx.compose.ui.unit.IntOffset(0, (8 * (1 - a.value)).dp.roundToPx()) }
}
