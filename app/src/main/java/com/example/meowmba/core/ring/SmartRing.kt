package com.example.meowmba.core.ring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Local input only. The driver is installed by the Android host. */
object SmartRing {
    var driver: SmartRingDriver? = null
    val openRequested = MutableStateFlow(false)
}
data class RingDevice(val address: String, val name: String)
enum class RingAction(val label: String) {
    NONE("No action"), SCROLL_DOWN("Scroll down"), SCROLL_UP("Scroll up"), BACK("Go back"), CONTROLS("Open ring controls"),
    CLICK("Click target"), POINTER_UP("Move target up"), POINTER_DOWN("Move target down"),
    POINTER_LEFT("Move target left"), POINTER_RIGHT("Move target right")
}
data class RingState(
    val controlsConsent: Boolean = false,
    val status: String = "Not connected",
    val devices: List<RingDevice> = emptyList(),
    val scanning: Boolean = false,
    val selectedName: String = "",
    val connected: Boolean = false,
    val accessibility: Boolean = false,
    val paused: Boolean = true,
    val testing: Boolean = false,
    val sleepEnabled: Boolean = true,
    val sleepAfterSeconds: Int = 30,
    val sleeping: Boolean = false,
    val verifiedGestures: Set<String> = emptySet(),
    val inputDevice: String = "",
    val reverse: Boolean = false,
    val distance: Float = 0.35f,
    val allApps: Boolean = false,
    val showTarget: Boolean = true,
    val allowlist: String = "com.example.meowmba",
    val mappings: Map<String, RingAction> = defaultRingMappings(),
    val events: List<String> = emptyList(),
    val pressedKeys: Set<Int> = emptySet(),
    val lastInput: String = "Waiting for ring input",
    val inputSequence: Long = 0,
    val gestureCounts: Map<String, Int> = emptyMap(),
    val sensors: Map<RingSensor, RingReading> = RingSensor.entries.associateWith { RingReading() },
    val activeSensor: RingSensor? = null,
)
fun defaultRingMappings() = mapOf(
    "Next" to RingAction.SCROLL_DOWN, "Previous" to RingAction.SCROLL_UP,
    "Single tap" to RingAction.NONE, "Double tap" to RingAction.BACK,
    "Triple tap" to RingAction.CONTROLS, "Quadruple tap" to RingAction.NONE,
    "1 tap + Next" to RingAction.NONE, "1 tap + Previous" to RingAction.NONE,
    "2 taps + Next" to RingAction.NONE, "2 taps + Previous" to RingAction.NONE,
)
interface SmartRingDriver {
    val state: StateFlow<RingState>
    fun scan()
    fun connect(address: String)
    fun disconnect()
    fun openAccessibility()
    fun setControlsConsent(value: Boolean)
    fun openBluetooth()
    fun setSleepEnabled(value: Boolean)
    fun setSleepAfterSeconds(value: Int)
    fun sleepNow()
    fun wakeControls()
    fun setPaused(value: Boolean)
    fun resetVerification()
    fun setTesting(value: Boolean)
    fun setReverse(value: Boolean)
    fun setDistance(value: Float)
    fun setAllApps(value: Boolean)
    fun setShowTarget(value: Boolean)
    fun centerTarget()
    fun setAllowlist(value: String)
    fun setMapping(gesture: String, action: RingAction)
    fun testSensor(sensor: RingSensor)
    fun stopSensorTest()
    fun clearTestResults()
    fun exportDiagnostics()
}
/** Only explicit package IDs are accepted; an empty list enables no apps. */
fun ringAllowedPackages(text: String): Set<String> = text.split(',', '\n', ' ', '\t')
    .map(String::trim).filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) }.toSet()
fun ringPacket(operation: Int): ByteArray = ByteArray(16).also {
    it[0] = 0x3b; it[1] = 2; it[2] = operation.toByte()
    it[3] = if (operation == 1) 0 else 1
    it[4] = when (operation) { 0 -> 5; 2 -> 1; else -> 0 }
    it[15] = it.take(15).sumOf { byte -> byte.toInt() and 0xff }.toByte()
}

/** Rechecked at dispatch, including after a delayed tap sequence. */
fun ringActionFor(state: RingState, gesture: String, packageName: String?): RingAction {
    if (!state.controlsConsent || !state.connected || state.paused || state.testing || state.sleeping ||
        !ringAppAllowed(state, packageName)) return RingAction.NONE
    val action = state.mappings[gesture] ?: RingAction.NONE
    if (!ringGestureReady(state, gesture, action)) return RingAction.NONE
    if (!state.showTarget && action in ringPointerActions) return RingAction.NONE
    return if (!state.reverse) action else when (action) {
        RingAction.SCROLL_DOWN -> RingAction.SCROLL_UP
        RingAction.SCROLL_UP -> RingAction.SCROLL_DOWN
        else -> action
    }
}

/** Global scope is a separate opt-in; missing foreground identity still disables input. */
fun ringAppAllowed(state: RingState, packageName: String?): Boolean =
    !packageName.isNullOrBlank() && (state.allApps || packageName in ringAllowedPackages(state.allowlist))

fun ringScrollClickMappings() = defaultRingMappings() + mapOf(
    "Single tap" to RingAction.CLICK,
    "1 tap + Next" to RingAction.POINTER_RIGHT, "1 tap + Previous" to RingAction.POINTER_LEFT,
    "2 taps + Next" to RingAction.POINTER_DOWN, "2 taps + Previous" to RingAction.POINTER_UP,
)

data class RingPoint(val x: Float = .5f, val y: Float = .5f) {
    fun move(action: RingAction): RingPoint = when (action) {
        RingAction.POINTER_UP -> copy(y = (y - .08f).coerceAtLeast(.03f))
        RingAction.POINTER_DOWN -> copy(y = (y + .08f).coerceAtMost(.97f))
        RingAction.POINTER_LEFT -> copy(x = (x - .08f).coerceAtLeast(.03f))
        RingAction.POINTER_RIGHT -> copy(x = (x + .08f).coerceAtMost(.97f))
        else -> this
    }
}

val ringMovementActions = setOf(RingAction.POINTER_UP, RingAction.POINTER_DOWN, RingAction.POINTER_LEFT, RingAction.POINTER_RIGHT)
val ringPointerActions = ringMovementActions + RingAction.CLICK

/** Cursor movement can compose individually verified inputs; clicks still require the exact gesture. */
fun ringGestureReady(state: RingState, gesture: String, action: RingAction): Boolean {
    if (gesture in state.verifiedGestures) return true
    if (action !in ringMovementActions) return false
    val inputs = when (gesture) {
        "1 tap + Next" -> setOf("Single tap", "Next")
        "1 tap + Previous" -> setOf("Single tap", "Previous")
        "2 taps + Next" -> setOf("Single tap", "Double tap", "Next")
        "2 taps + Previous" -> setOf("Single tap", "Double tap", "Previous")
        else -> return false
    }
    return state.verifiedGestures.containsAll(inputs)
}

/** Update the original complete cursor preset once; preserve manually customized combinations. */
fun ringMigrateCursorAxes(mappings: Map<String, RingAction>): Map<String, RingAction> {
    val original = mapOf("1 tap + Next" to RingAction.POINTER_DOWN, "1 tap + Previous" to RingAction.POINTER_UP,
        "2 taps + Next" to RingAction.POINTER_RIGHT, "2 taps + Previous" to RingAction.POINTER_LEFT)
    return if (original.all { (gesture, action) -> mappings[gesture] == action })
        mappings + ringScrollClickMappings().filterKeys { it in original } else mappings
}

fun ringSleepEligible(state: RingState) = state.controlsConsent && state.sleepEnabled && state.connected && state.accessibility &&
    !state.paused && !state.testing
