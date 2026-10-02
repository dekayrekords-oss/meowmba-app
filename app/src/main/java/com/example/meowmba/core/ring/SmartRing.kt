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
    NONE("No action"), SCROLL_DOWN("Scroll down"), SCROLL_UP("Scroll up"), BACK("Go back"), CONTROLS("Open ring controls")
}
data class RingState(
    val status: String = "Not connected",
    val devices: List<RingDevice> = emptyList(),
    val scanning: Boolean = false,
    val selectedName: String = "",
    val connected: Boolean = false,
    val accessibility: Boolean = false,
    val paused: Boolean = true,
    val testing: Boolean = false,
    val verifiedGestures: Set<String> = emptySet(),
    val inputDevice: String = "",
    val reverse: Boolean = false,
    val distance: Float = 0.35f,
    val allowlist: String = "com.example.meowmba",
    val mappings: Map<String, RingAction> = defaultRingMappings(),
    val events: List<String> = emptyList(),
)
fun defaultRingMappings() = mapOf(
    "Next" to RingAction.SCROLL_DOWN, "Previous" to RingAction.SCROLL_UP,
    "Single tap" to RingAction.NONE, "Double tap" to RingAction.BACK,
    "Triple tap" to RingAction.CONTROLS,
)
interface SmartRingDriver {
    val state: StateFlow<RingState>
    fun scan()
    fun connect(address: String)
    fun disconnect()
    fun openAccessibility()
    fun openBluetooth()
    fun setPaused(value: Boolean)
    fun resetVerification()
    fun setTesting(value: Boolean)
    fun setReverse(value: Boolean)
    fun setDistance(value: Float)
    fun setAllowlist(value: String)
    fun setMapping(gesture: String, action: RingAction)
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
    if (!state.connected || state.paused || state.testing || gesture !in state.verifiedGestures ||
        packageName !in ringAllowedPackages(state.allowlist)) return RingAction.NONE
    val action = state.mappings[gesture] ?: RingAction.NONE
    return if (!state.reverse) action else when (action) {
        RingAction.SCROLL_DOWN -> RingAction.SCROLL_UP
        RingAction.SCROLL_UP -> RingAction.SCROLL_DOWN
        else -> action
    }
}
