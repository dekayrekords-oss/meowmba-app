package com.example.meowmba.ui.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Explicit navigation survives every avatar style; decorative gestures are only shortcuts. */
@Composable
internal fun FlowHomeNavigation(onMenu: () -> Unit, onAdd: () -> Unit, onDesk: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalFlowColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("Menu" to onMenu, "Add task" to onAdd, "Desk" to onDesk, "Settings" to onSettings).forEach { (label, action) ->
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = action)
                .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                BasicText(label, style = FlowType.button.copy(color = colors.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
    }
}

@Composable
internal fun DeskNavigation(modifier: Modifier = Modifier, vertical: Boolean = false, onMenu: () -> Unit, onFlow: () -> Unit, onSettings: () -> Unit,
                            micOn: Boolean? = null, onMicToggle: () -> Unit = {}, onAttach: (() -> Unit)? = null) {
    val controls: @Composable () -> Unit = {
        listOfNotNull("Menu" to onMenu, "Flow" to onFlow,
            micOn?.let { (if (it) "Mic on" else "Mic off") to onMicToggle },
            onAttach?.let { "Add" to it },
            "Settings" to onSettings).forEach { (label, action) ->
            TextButton(onClick = action, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(label, color = Color(0xFFF4EEDB))
            }
        }
    }
    if (vertical) Column(modifier.width(128.dp).padding(horizontal = 8.dp)) { controls() }
    else Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) { controls() }
}

/**
 * The home swipes from the avatar screen, for Flow and the Desk: up = apps, down = menu,
 * right = chat, left = browser (the setup guide's promise). Observes only, never consumes,
 * so taps, buttons and desk objects underneath keep working. One finger, decisive strokes.
 */
internal fun Modifier.homeSwipes(
    enabled: Boolean = true,
    onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(onUp, onDown, onLeft, onRight) {
    val vertical = 56.dp.toPx()
    val horizontal = 110.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        var end = down.position
        var multi = false
        var handled = false
        while (true) {
            // Final pass: children have had their turn; a drag they consumed (the dock handle,
            // a scrolling list) is theirs, not a home swipe.
            val ev = awaitPointerEvent(PointerEventPass.Final)
            if (ev.changes.size > 1) multi = true
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (c.isConsumed && c.pressed) handled = true
            end = c.position
            if (!c.pressed) break
        }
        if (multi || handled) return@awaitEachGesture
        val dx = end.x - down.position.x
        val dy = end.y - down.position.y
        when {
            abs(dy) > abs(dx) * 1.5f && abs(dy) > vertical -> if (dy < 0) onUp() else onDown()
            abs(dx) > abs(dy) * 1.5f && abs(dx) > horizontal -> if (dx < 0) onLeft() else onRight()
        }
    }
}

