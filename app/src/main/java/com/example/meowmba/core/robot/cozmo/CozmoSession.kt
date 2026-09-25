package com.example.meowmba.core.robot.cozmo

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

data class CozmoState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val message: String = "Not connected",
    val firmware: String = "",
    val serial: String = "",
    val telemetry: CozmoTelemetry? = null,
    val wheelsEnabled: Boolean = false,
    val cameraEnabled: Boolean = false,
    val expression: String = "neutral",
    val companionEyes: Boolean = true,
    val cubes: List<CozmoCube> = emptyList(),
    val lastEvent: String = "",
)
data class CozmoCube(val factoryId: Int, val type: Int, val rssi: Int, val objectId: Int? = null)

/** Reliable ordered packets; telemetry/image events are out of band and never advance the ACK. */
internal class CozmoReliability {
    data class Pending(var packet: CozmoProtocol.Packet, var sentAt: Long)
    val pending = linkedMapOf<Int, Pending>()
    private val received = mutableMapOf<Int, CozmoProtocol.Packet>()
    private var nextSend = 0
    private var nextReceive = 0
    var ack = CozmoProtocol.OOB; private set
    fun add(packet: CozmoProtocol.Packet, now: Long): Pair<Int, Pending> {
        check(pending.size < 16) { "Cozmo command queue is full" }
        val seq = nextSend
        nextSend = CozmoProtocol.next(nextSend)
        return seq to Pending(packet, now).also { pending[seq] = it }
    }
    fun receive(frame: CozmoProtocol.Frame): List<CozmoProtocol.Packet> {
        val first = pending.keys.firstOrNull()
        if (first != null && frame.ack != CozmoProtocol.OOB &&
            CozmoProtocol.distance(first, frame.ack) < pending.size) {
            val count = CozmoProtocol.distance(first, frame.ack) + 1
            pending.keys.take(count).forEach { pending.remove(it) }
        }
        val delivered = mutableListOf<CozmoProtocol.Packet>()
        var seq = frame.first
        frame.packets.forEach { p ->
            if (p.type >= 5) delivered += p
            else {
                if (seq in 0 until CozmoProtocol.MOD && CozmoProtocol.distance(nextReceive, seq) < 62) received.putIfAbsent(seq, p)
                seq = CozmoProtocol.next(seq)
            }
        }
        while (true) {
            val p = received.remove(nextReceive) ?: break
            ack = nextReceive
            nextReceive = CozmoProtocol.next(nextReceive)
            delivered += p
        }
        return delivered
    }
    fun revokeMotion() {
        // Preserve sequence continuity while ensuring an old unacknowledged move cannot be replayed after stop.
        pending.values.filter { it.packet.id in MOTORS }.forEach { it.packet = CozmoProtocol.command(0x3b) }
    }
    companion object { val MOTORS = setOf(0x32, 0x34, 0x35, 0x36, 0x37) }
}

/** One socket-owning worker per explicit connection. No reconnect or motion replay after link loss. */
class CozmoSession(
    private val socket: DatagramSocket,
    private val onState: (CozmoState) -> Unit,
    private val onImage: (ByteArray?) -> Unit = {},
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : AutoCloseable {
    private sealed class Request {
        data class Command(val packets: List<CozmoProtocol.Packet>, val motor: Boolean = false, val wheels: Boolean = false,
            val created: Long, val duration: Long = 0) : Request()
        data class Wheels(val enabled: Boolean) : Request()
        data class Camera(val enabled: Boolean) : Request()
        data class Expression(val name: String) : Request()
        data class Companion(val enabled: Boolean) : Request()
    }
    @Volatile var state = CozmoState(); private set
    private val queue = ArrayBlockingQueue<Request>(32)
    private val stopping = AtomicBoolean(false)
    private val halt = AtomicBoolean(false)
    private val wire = CozmoReliability()
    private val camera = CozmoCamera()
    private val worker = Thread(::run, "Cozmo-WiFi").apply { isDaemon = true }
    private var wheelUntil = 0L
    private var lastStateAt = 0L
    private var lastPublishedAt = 0L
    private var lastRobotTimestamp: Long? = null
    private var firmwareSupported = false
    private var bodySeen = false
    private var lastFace = 0L
    private var faceTicks = 0
    private var blinkUntil = 0L
    private var lastCameraRequest = 0L
    private var pingCounter = 0

    fun start() = worker.start()
    private fun publish(next: CozmoState = state) { state = next; onState(next) }
    private fun enqueue(r: Request): String {
        if (!state.connected || stopping.get()) return "Cozmo is not connected"
        return if (queue.offer(r)) "Command queued for Cozmo" else "Cozmo is busy; try again"
    }
    fun setWheels(enabled: Boolean): String = enqueue(Request.Wheels(enabled))
    fun setCamera(enabled: Boolean): String = enqueue(Request.Camera(enabled))
    fun setCompanion(enabled: Boolean): String = enqueue(Request.Companion(enabled))
    fun stop() { halt.set(true) }
    fun expression(name: String): String = if (name in CozmoProtocol.EXPRESSIONS) enqueue(Request.Expression(name)) else "Unknown Cozmo expression"
    fun action(action: String): String {
        if (action == "stop") { stop(); return "Stop requested; check that Cozmo stops" }
        if (action == "status") return statusText()
        val p = CozmoProtocol
        if (action in p.EXPRESSIONS) return expression(action)
        if (action.startsWith("light_")) {
            val color = p.COLORS[action.removePrefix("light_")] ?: return "Unknown backpack color"
            return enqueue(Request.Command(listOf(p.light(color), p.light(color, side = true)), created = clock()))
        }
        val wheels = when (action) {
            "forward" -> 45f to 45f
            "back" -> -40f to -40f
            "left" -> -35f to 35f
            "right" -> 35f to -35f
            else -> null
        }
        if (wheels != null) {
            if (!state.wheelsEnabled) return "Enable short floor moves in Robot → Cozmo first"
            state.telemetry?.motionBlock(clock())?.let { return it }
            if (state.telemetry == null) return "Waiting for Cozmo's sensors"
            return enqueue(Request.Command(listOf(p.wheels(wheels.first, wheels.second)), true, true, clock(), 350))
        }
        val packet = when (action) {
            "look_up" -> p.head(35f)
            "look_down" -> p.head(-20f)
            "look_center" -> p.head(0f)
            "lift_up" -> p.lift(85f)
            "lift_down" -> p.lift(32f)
            "headlight_on" -> p.command(0x0b, byteArrayOf(1))
            "headlight_off" -> p.command(0x0b, byteArrayOf(0))
            else -> return "Unsupported Cozmo action: $action"
        }
        return enqueue(Request.Command(listOf(packet), packet.id in CozmoReliability.MOTORS, created = clock()))
    }
    fun connectCube(factoryId: Int): String {
        if (state.cubes.none { it.factoryId == factoryId }) return "That cube has not been discovered"
        return enqueue(Request.Command(listOf(CozmoProtocol.command(5,
            CozmoProtocol.buffer(5).putInt(factoryId).put(1).array())), created = clock()))
    }
    fun statusText(): String = buildString {
        append(state.message)
        state.telemetry?.let { t -> append("; battery %.2f V; head %.0f°; lift %.0f mm".format(t.batteryVolts, t.headDegrees, t.liftMm))
            if (t.pickedUp) append("; picked up")
            if (t.onCharger) append("; on charger")
            if (t.cliff) append("; edge detected")
        }
        append("; wheels ${if (state.wheelsEnabled) "enabled" else "disabled"}; ${state.cubes.size} cube(s)")
    }
    private fun sendRaw(frame: CozmoProtocol.Frame) {
        val bytes = CozmoProtocol.encode(frame)
        socket.send(DatagramPacket(bytes, bytes.size))
    }
    private fun send(p: CozmoProtocol.Packet) {
        val (seq, pending) = wire.add(p, clock())
        sendPending(seq, pending)
    }
    private fun sendPending(seq: Int, pending: CozmoReliability.Pending) {
        sendRaw(CozmoProtocol.Frame(7, seq, seq, wire.ack, listOf(pending.packet)))
        pending.sentAt = clock()
    }
    private fun sendFace(expression: String, blink: Boolean = false) {
        // The firmware animation engine advances display frames with audio ticks (silence here).
        send(CozmoProtocol.command(0x8f))
        send(CozmoProtocol.face(CozmoProtocol.eyes(expression, blink)))
    }
    private fun stopNow(disarm: Boolean = true) {
        queue.removeIf { it is Request.Command && it.motor || it is Request.Wheels }
        wire.revokeMotion()
        wheelUntil = 0
        if (firmwareSupported) {
            if (wire.pending.size < 16) send(CozmoProtocol.command(0x3b))
            // Even a full reliable window can retransmit the revoked motor sequence immediately.
            wire.pending.filterValues { it.packet.id == 0x3b }.forEach { (seq, p) -> sendPending(seq, p) }
        }
        publish(state.copy(wheelsEnabled = state.wheelsEnabled && !disarm, lastEvent = "Stop requested; verify Cozmo has stopped"))
    }
    private fun run() {
        val started = clock()
        var lastPing = 0L
        var lastReset = 0L
        var handshake = false
        try {
            socket.soTimeout = 25
            publish(state.copy(connecting = true, message = "Contacting Cozmo…"))
            while (!stopping.get()) {
                val now = clock()
                if (!handshake && now - lastReset >= 1000) {
                    sendRaw(CozmoProtocol.Frame(1, 0, 0, CozmoProtocol.OOB, emptyList()))
                    lastReset = now
                }
                if (!state.connected && now - started > 10_000) error("No compatible Cozmo response. Join Cozmo's Wi-Fi and close the official app.")
                if (handshake && now - lastPing >= 250) {
                    val ping = CozmoProtocol.buffer(17).putDouble(now / 1000.0).putInt(pingCounter++).putInt(0).put(0).array()
                    sendRaw(CozmoProtocol.Frame(11, CozmoProtocol.OOB, CozmoProtocol.OOB, wire.ack, listOf(CozmoProtocol.Packet(11, data = ping))))
                    lastPing = now
                }
                if (halt.getAndSet(false)) stopNow()
                if (wheelUntil > 0 && (now >= wheelUntil || state.telemetry?.motionBlock(now) != null))
                    stopNow(disarm = state.telemetry?.motionBlock(now) != null)
                if (state.connected && now - lastStateAt > 1500) error("Cozmo connection lost; reconnect manually")
                if (wire.pending.size < 8) queue.poll()?.let { process(it, now) }
                if (state.connected && wire.pending.size < 8 && blinkUntil > 0 && now >= blinkUntil) {
                    sendFace(state.expression)
                    blinkUntil = 0
                }
                if (state.connected && wire.pending.size < 8 && now - lastFace > 1500) {
                    val blink = state.companionEyes && ++faceTicks % 4 == 0
                    sendFace(state.expression, blink)
                    if (blink) blinkUntil = now + 140
                    lastFace = now
                }
                if (state.cameraEnabled && wire.pending.size < 8 && now - lastCameraRequest > 300) {
                    // Single shots bound decoding, network load and battery cost to about 3 FPS.
                    send(CozmoProtocol.command(0x4c, byteArrayOf(2, 4)))
                    lastCameraRequest = now
                }
                wire.pending.toList().filter { now - it.second.sentAt >= 120 }.forEach { (seq, p) -> sendPending(seq, p) }
                try {
                    val datagram = DatagramPacket(ByteArray(2048), 2048)
                    socket.receive(datagram)
                    val frame = CozmoProtocol.decode(datagram.data.copyOf(datagram.length)) ?: continue
                    if (frame.type == 3) error("Cozmo ended the connection")
                    wire.receive(frame).forEach { p ->
                        if (p.type == 2) handshake = true
                        if (p.type == 3) error("Cozmo disconnected")
                        receive(p, now)
                    }
                } catch (_: SocketTimeoutException) { /* Worker checks deadlines without waiting for robot traffic. */ }
            }
        } catch (e: Exception) {
            publish(state.copy(message = e.message ?: "Cozmo connection failed"))
        } finally {
            runCatching { stopNow() }
            // Do not send protocol Disconnect: old firmware can reboot if packets follow it.
            socket.close()
            queue.clear()
            camera.reset()
            onImage(null)
            publish(state.copy(connecting = false, connected = false, wheelsEnabled = false, cameraEnabled = false,
                telemetry = null, cubes = emptyList(), message = if (stopping.get()) "Disconnected" else state.message))
        }
    }
    private fun process(request: Request, now: Long) {
        when (request) {
            is Request.Wheels -> publish(state.copy(wheelsEnabled = request.enabled, lastEvent = "Short floor moves ${if (request.enabled) "enabled" else "disabled"}"))
            is Request.Companion -> publish(state.copy(companionEyes = request.enabled))
            is Request.Camera -> {
                if (!request.enabled) { send(CozmoProtocol.camera(false)); camera.reset(); onImage(null) }
                else send(CozmoProtocol.command(0x66, byteArrayOf(0)))
                publish(state.copy(cameraEnabled = request.enabled))
            }
            is Request.Expression -> {
                sendFace(request.name)
                publish(state.copy(expression = request.name))
            }
            is Request.Command -> {
                if (now - request.created > 500) return
                if (request.motor && (state.telemetry == null || now - lastStateAt > 500 || state.telemetry?.falling == true)) return
                if (request.wheels && (!state.wheelsEnabled || state.telemetry?.motionBlock(now) != null)) return
                request.packets.forEach(::send)
                if (request.wheels) wheelUntil = now + request.duration
            }
        }
    }
    private fun receive(p: CozmoProtocol.Packet, now: Long) {
        val b = CozmoProtocol.reader(p.data)
        when (p.id) {
            0xee -> {
                if (p.data.size < 4) return
                val length = b.getShort(2).toInt() and 65535
                if (length > p.data.size - 4) return
                val json = p.data.copyOfRange(4, 4 + length).toString(Charsets.UTF_8)
                val version = Regex("\"version\"\\s*:\\s*\"?(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
                check(version == 2381 && !json.contains("FACTORY")) { "Unsupported Cozmo firmware ($version). This adapter uses the documented 2381 protocol." }
                if (!firmwareSupported) {
                    firmwareSupported = true
                    publish(state.copy(firmware = version.toString()))
                    send(CozmoProtocol.command(0x25)); send(CozmoProtocol.command(0x25))
                }
            }
            0xed -> if (firmwareSupported && !bodySeen && p.data.size >= 12) {
                bodySeen = true
                publish(state.copy(serial = Integer.toUnsignedString(b.int, 16)))
                send(CozmoProtocol.command(0x45, CozmoProtocol.buffer(24).putInt(0).putInt(0).putInt(1).putFloat(0f).putFloat(0f).putInt(Int.MIN_VALUE).array()))
                send(CozmoProtocol.command(0x4b, ByteArray(8)))
                send(CozmoProtocol.command(0x9f))
                send(CozmoProtocol.command(0x60, byteArrayOf(1)))
                send(CozmoProtocol.command(0x0a, byteArrayOf(1)))
            }
            0xf0 -> if (bodySeen) {
                val telemetry = CozmoTelemetry.decode(p.data, now) ?: return
                val timestamp = b.getInt(0).toLong() and 0xffffffffL
                val previous = lastRobotTimestamp
                if (previous != null && ((timestamp - previous) and 0xffffffffL) !in 1..0x7fffffffL) return
                lastRobotTimestamp = timestamp
                lastStateAt = now
                state = state.copy(telemetry = telemetry, connecting = false, connected = true, message = "Cozmo connected")
                if (now - lastPublishedAt >= 200) { publish(); lastPublishedAt = now }
            }
            0xf2 -> if (state.cameraEnabled) camera.accept(p.data)?.let(onImage)
            0xf3 -> if (p.data.size >= 9) {
                val id = b.int; val type = b.int; val signal = b.get().toInt()
                if (type in 1..3) {
                    val existing = state.cubes.firstOrNull { it.factoryId == id }
                    val cube = CozmoCube(id, type, signal, existing?.objectId)
                    state = state.copy(cubes = (state.cubes.filterNot { it.factoryId == id } + cube).takeLast(3))
                }
            }
            0xd0 -> if (p.data.size >= 13) {
                val objectId = b.int; val factory = b.int; b.int; val connected = b.get().toInt() != 0
                publish(state.copy(cubes = state.cubes.map { if (it.factoryId == factory) it.copy(objectId = objectId.takeIf { connected }) else it },
                    lastEvent = "Cube ${if (connected) "connected" else "disconnected"}"))
            }
            0xb6 -> if (p.data.size >= 9) publish(state.copy(lastEvent = "Cube tapped ${p.data[8].toInt() and 255} time(s)"))
        }
    }
    override fun close() { stopping.set(true); halt.set(true) }
    fun awaitClosed() { if (Thread.currentThread() != worker) worker.join(1000) }
}
