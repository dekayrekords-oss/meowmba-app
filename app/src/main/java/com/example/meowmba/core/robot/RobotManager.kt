package com.example.meowmba.core.robot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the active [MotorController] and honestly reports which body is live.
 *
 * Default is the [SimulatedMotorController] (no hardware). When the user enables
 * the robot body and a base is reachable, [connectLooi] swaps in a
 * [LooiMotorController]; if the base is unreachable we fall back to simulated
 * and say so in [status] rather than pretending.
 */
class RobotManager {
    private val _status = MutableStateFlow("simulated (no body)")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile private var controller: MotorController = SimulatedMotorController()
    private var transport: RobotTransport? = null

    /** The controller the companion should drive right now. Never null. */
    val active: MotorController get() = controller

    /** Drop any connection and return to the honest simulated body. */
    fun useSimulated() {
        transport?.close()
        transport = null
        controller = SimulatedMotorController()
        _status.value = "simulated (no body)"
    }

    /**
     * Try to bring up a LOOI base at [host]:[port]. Returns true when the body
     * is live; on failure falls back to simulated and reports it in [status].
     */
    fun connectLooi(host: String, port: Int): Boolean =
        bringUp(TcpRobotTransport(host, port), "LOOI body @ $host:$port",
            "LOOI unreachable ($host:$port) - simulated")

    /** Bring up a LOOI base over classic Bluetooth (RFCOMM/SPP) to a paired device. */
    fun connectBluetooth(address: String): Boolean =
        bringUp(BluetoothRobotTransport(address), "LOOI body (BT) @ $address",
            "LOOI BT unreachable ($address) - simulated")

    private fun bringUp(t: RobotTransport, okLabel: String, failLabel: String): Boolean =
        if (t.connect()) {
            transport?.close()
            transport = t
            controller = LooiMotorController(t)
            _status.value = okLabel
            true
        } else {
            t.close()
            transport?.close()
            transport = null
            controller = SimulatedMotorController()
            _status.value = failLabel
            false
        }

    fun close() {
        transport?.close()
        transport = null
    }
}
