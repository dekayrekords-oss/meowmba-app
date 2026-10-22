package com.example.meowmba.core.ring

import java.util.Locale

enum class RingSensor(val label: String, val unit: String, val type: Int = 0, val sub: Int = 0, val duration: Long = 8_000) {
    BATTERY("Battery", "%"), STEPS("Today's steps", "steps"),
    HEART_RATE("Heart rate", "bpm", 1, 0, 25_000),
    OXYGEN("Oxygen saturation", "%", 3, 0x25, 25_000),
    TEMPERATURE("Temperature estimate", "°C", 0x0b, 0x25, 25_000),
    HRV("HRV estimate", "ms", 0x0a, 0x25, 80_000),
    STRESS("Stress estimate", "score", 8, 0x25, 30_000);
    val measurement get() = type != 0
    val opcode get() = when (this) { BATTERY -> 3; STEPS -> 0x48; else -> 0x69 }
    fun request() = if (measurement) ringControlFrame(opcode, type, sub) else ringControlFrame(opcode)
}

/** Session readings stay in memory and are excluded from shared diagnostics. */
data class RingReading(val value: String = "—", val status: String = "Not tested", val receivedAt: Long = 0)

fun ringControlFrame(opcode: Int, vararg payload: Int): ByteArray {
    require(opcode in 0..255 && payload.size <= 14)
    return ByteArray(16).also { bytes ->
        bytes[0] = opcode.toByte()
        payload.forEachIndexed { index, value -> bytes[index + 1] = value.toByte() }
        bytes[15] = bytes.take(15).sumOf { it.toInt() and 255 }.toByte()
    }
}

data class RingSensorReply(val raw: Int, val value: String?, val error: String? = null)

/** Strict lengths/checksums and matching types prevent unrelated notifications becoming readings. */
fun ringSensorReply(sensor: RingSensor, bytes: ByteArray, stopped: Boolean = false): RingSensorReply? {
    if (bytes.size != 16 || bytes.take(15).sumOf { it.toInt() and 255 }.and(255) != bytes[15].toInt().and(255)) return null
    fun at(index: Int) = bytes[index].toInt() and 255
    val opcode = if (stopped) 0x6a else sensor.opcode
    if (at(0) and 127 != opcode || sensor.measurement && at(1) != sensor.type) return null
    if (at(0) and 128 != 0) return RingSensorReply(0, null, "Ring rejected request")
    if (sensor == RingSensor.BATTERY) {
        val raw = at(1)
        return if (raw <= 100) RingSensorReply(raw, "$raw%${if (at(2) == 1) " · charging" else ""}") else null
    }
    if (sensor == RingSensor.STEPS) {
        val raw = (at(1) shl 16) or (at(2) shl 8) or at(3)
        return RingSensorReply(raw, "$raw steps")
    }
    val error = at(if (stopped) 3 else 2)
    val raw = at(if (stopped) 2 else 3)
    if (error != 0) return RingSensorReply(raw, null, if (error == 1) "Not worn / poor contact" else "Ring error $error")
    val valid = raw > 0 && (sensor != RingSensor.OXYGEN || raw <= 100)
    val value = if (!valid) null else if (sensor == RingSensor.TEMPERATURE)
        String.format(Locale.US, "%.1f °C", 20.0 + raw / 10.0) else "$raw ${sensor.unit}"
    return RingSensorReply(raw, value)
}

/** One sensor request at a time, including the stop acknowledgement and write completion.
 * schedule returns a cancellation function; send is the single GATT write owned by the host. */
class RingSensorSession(
    private val send: (ByteArray) -> Boolean,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val publish: (RingSensor?, Map<RingSensor, RingReading>) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var active: RingSensor? = null; private set
    private var readings = RingSensor.entries.associateWith { RingReading() }
    private var writing = false
    private var stopping = false
    private var stopPending = false
    private var finishPending: String? = null
    private var endingStatus: String? = null
    private var raw = 0
    private var cancelTimer: (() -> Unit)? = null
    private fun emit() = publish(active, readings)
    private fun status(value: String) { active?.let { readings = readings + (it to readings.getValue(it).copy(status = value)) }; emit() }
    private fun timer(delay: Long, action: () -> Unit) { cancelTimer?.invoke(); cancelTimer = schedule(delay, action) }
    fun start(sensor: RingSensor) {
        if (active != null) return
        active = sensor; raw = 0; stopping = false; stopPending = false; endingStatus = null; finishPending = null
        readings = readings + (sensor to RingReading(status = if (sensor.measurement) "Measuring… keep still" else "Requesting…"))
        emit()
        timer(sensor.duration) { if (sensor.measurement) stop(null) else finish("No response · unverified") }
        write(sensor.request())
    }
    private fun write(bytes: ByteArray) {
        writing = true
        if (!send(bytes)) { writing = false; finish("Could not send request") }
    }
    fun onWrite(success: Boolean) {
        if (!writing || active == null) return
        writing = false
        if (!success) { finish("Bluetooth write failed"); return }
        finishPending?.let { finish(it); return }
        if (stopPending) { stopPending = false; sendStop() }
    }
    fun receive(bytes: ByteArray) {
        val sensor = active ?: return
        val reply = ringSensorReply(sensor, bytes, stopping) ?: return
        if (reply.value != null) {
            raw = reply.raw
            readings = readings + (sensor to RingReading(reply.value, "Received from ring", now()))
            emit()
        }
        if (sensor.measurement && !stopping) {
            if (reply.error != null || reply.value != null) stop(reply.error ?: "Received from ring")
        } else finish(endingStatus ?: reply.error ?: if (reply.value != null) "Received from ring" else "No reading · unverified")
    }
    fun stop(reason: String? = "Cancelled") {
        val sensor = active ?: return
        if (!sensor.measurement) { finish(reason ?: "Cancelled"); return }
        if (stopping || stopPending) return
        endingStatus = reason
        status(if (reason == null) "Collecting final reading…" else "$reason · stopping…")
        if (writing) stopPending = true else sendStop()
    }
    private fun sendStop() {
        val sensor = active ?: return
        stopping = true
        timer(8_000) { finish(endingStatus ?: "No response · unverified") }
        write(ringControlFrame(0x6a, sensor.type, if (sensor == RingSensor.TEMPERATURE) 0 else raw, 0))
    }
    private fun finish(result: String) {
        // Do not let the next test overtake an outstanding GATT write.
        if (writing) { finishPending = result; status("$result · waiting for Bluetooth"); return }
        status(result); cancelTimer?.invoke(); cancelTimer = null
        active = null; stopPending = false; finishPending = null; emit()
    }
    fun disconnected() {
        cancelTimer?.invoke(); cancelTimer = null; writing = false
        if (active != null) finish("Disconnected · test interrupted")
        readings = readings.mapValues { (_, value) -> if (value.receivedAt > 0) value.copy(status = "Previous connection · read again") else value }
        emit()
    }
    fun clear() { if (active == null) { readings = RingSensor.entries.associateWith { RingReading() }; emit() } }
}
