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
    private var bleLink: LooiBleLink? = null

    /** The controller the companion should drive right now. Never null. */
    val active: MotorController get() = controller

    /** Drop any connection and return to the honest simulated body. */
    fun useSimulated() {
        transport?.close()
        transport = null
        bleLink?.close()
        bleLink = null
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

    /**
     * Auto-discovery: the first bonded (paired) device whose name looks like a LOOI
     * base, so the body can connect without the user typing a MAC. Returns null when
     * nothing is paired or the BLUETOOTH_CONNECT permission is missing.
     */
    fun discoverLooiAddress(): String? = try {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return null
        adapter.bondedDevices?.firstOrNull { d ->
            val n = d.name ?: ""
            n.contains("looi", ignoreCase = true) || n.contains("meow", ignoreCase = true)
        }?.address
    } catch (e: Exception) {
        null
    }

    /**
     * Full-spectrum discovery for the Settings picker. Returns "name -> address"
     * pairs: bonded devices first (a base already paired at the system level), then
     * anything a bounded BLE scan sees. Requires BLUETOOTH_SCAN (manifest,
     * neverForLocation) and BLUETOOTH_CONNECT for names; returns whatever it can
     * rather than nothing — a missing permission degrades, it does not blank the
     * list. The scan runs up to [scanMs] and is stopped before returning.
     */
    fun discoverAllBases(scanMs: Long = 7_000): List<Pair<String, String>> {
        // address -> name, or null when the device never told us one.
        val out = LinkedHashMap<String, String?>()
        var scanRan = false
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            adapter.bondedDevices?.forEach { d ->
                runCatching {
                    // Skip bonded devices that physically cannot be a robot
                    // base. A user's phone accumulates years of paired
                    // headphones, speakers, TVs and watches — 28 of them here —
                    // and listing them all is what buried the picker AND let a
                    // Bluetooth speaker ("2BOOM-BT295") get saved as the LOOI
                    // address, after which the app cheerfully reported a live
                    // body while talking to a speaker.
                    if (!isPlausibleBaseClass(d.bluetoothClass?.majorDeviceClass)) return@runCatching
                    out[d.address] = d.name
                }
            }
            val scanner = if (adapter.isEnabled) {
                runCatching { adapter.bluetoothLeScanner }.getOrNull()
            } else null
            if (scanner != null) {
                val latch = java.util.concurrent.CountDownLatch(1)
                val callback = object : android.bluetooth.le.ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                        runCatching {
                            val d = result.device
                            // device.name FIRST, advertisement name second.
                            //
                            // This is the whole bug: a LOOI base puts its name
                            // ("looi_a444da") in the BLE scan RESPONSE, not in
                            // the advertisement, so scanRecord.deviceName is
                            // null for it. Reading only that and falling back to
                            // the address meant the base was listed as a bare
                            // MAC — present in the picker, but impossible to
                            // recognise, which reads as "it doesn't find it".
                            // device.name is the stack's resolved name and is
                            // what LooiBleLink's own scan has always matched on.
                            val name = (runCatching { d.name }.getOrNull())
                                ?.takeIf { it.isNotBlank() }
                                ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                            // A later sighting can resolve a name an earlier one
                            // lacked, so upgrade rather than keeping the first.
                            if (out[d.address] == null || name != null) out[d.address] = name
                        }
                    }
                    override fun onScanFailed(errorCode: Int) { latch.countDown() }
                }
                try {
                    // LOW_LATENCY, not the default LOW_POWER: this scan is a
                    // button the user is actively waiting on, and low-power
                    // duty-cycles so aggressively that an intermittent
                    // advertiser is routinely missed inside 7 seconds.
                    val settings = android.bluetooth.le.ScanSettings.Builder()
                        .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    scanner.startScan(emptyList(), settings, callback)
                    scanRan = true
                    latch.await(scanMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    runCatching { scanner.stopScan(callback) }
                } catch (e: Exception) {
                    // Almost always SecurityException: BLUETOOTH_SCAN is a
                    // runtime permission on Android 12+ and is not implied by
                    // declaring it. Say so — silently returning the bonded list
                    // looked like "the base is not there" for as long as the
                    // permission was missing.
                    android.util.Log.w("RobotManager", "BLE scan did not run", e)
                    _status.value =
                        "Can't scan: allow \"Nearby devices\" for Meowmba in system settings."
                    runCatching { scanner.stopScan(callback) }
                }
            }
        } catch (e: Exception) {
            // SecurityException on any missing permission: keep what we have.
        }
        // Diagnostic: what the scan actually saw, before ranking. Without this
        // a disappointing picker is indistinguishable from a scan that never
        // ran, a scan that ran but resolved no names, and a base that is simply
        // not advertising.
        android.util.Log.i(
            "RobotManager",
            "scan ran=$scanRan devices=${out.size} named=${out.count { it.value?.isNotBlank() == true }} " +
                "| " + out.entries.joinToString("; ") { "${it.value ?: "?"}=${it.key}" }.take(1200),
        )
        val rows = rankBases(out)
        if (!scanRan && rows.isEmpty()) {
            _status.value =
                "Can't scan: allow \"Nearby devices\" for Meowmba in system settings."
        }
        return rows
    }

    /**
     * Could a bonded device of this major class be a LOOI base?
     *
     * A LOOI reports UNCATEGORIZED (or TOY on some firmware). Audio gear,
     * phones, computers, wearables, imaging and networking hardware are all
     * definitively not a robot body, and excluding them is what keeps a paired
     * speaker from being offered — and chosen — as the base. null (class
     * unknown) is allowed through: unknown is not the same as disqualified.
     */
    internal fun isPlausibleBaseClass(majorDeviceClass: Int?): Boolean {
        if (majorDeviceClass == null) return true
        val excluded = setOf(
            android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO,
            android.bluetooth.BluetoothClass.Device.Major.PHONE,
            android.bluetooth.BluetoothClass.Device.Major.COMPUTER,
            android.bluetooth.BluetoothClass.Device.Major.WEARABLE,
            android.bluetooth.BluetoothClass.Device.Major.IMAGING,
            android.bluetooth.BluetoothClass.Device.Major.NETWORKING,
            android.bluetooth.BluetoothClass.Device.Major.HEALTH,
        )
        return majorDeviceClass !in excluded
    }

    /**
     * Turn raw scan results into the picker's list: recognisable bases first,
     * anonymous radios last and capped.
     *
     * Pure, so the ranking is unit-testable without a Bluetooth stack.
     */
    internal fun rankBases(
        found: Map<String, String?>,
        maxUnnamed: Int = 8,
    ): List<Pair<String, String>> {
        fun looksLikeBase(name: String?, address: String): Boolean {
            if (address.equals(LooiBleLink.KNOWN_LOOI_MAC, ignoreCase = true)) return true
            val n = name?.lowercase() ?: return false
            return "looi" in n || "meow" in n || "robot" in n || "luna" in n
        }

        val named = found.entries.filter { it.value?.isNotBlank() == true }
        // Unnamed radios are the "long list of MAC addresses" the user was
        // scrolling through — headphones, watches, TVs, every beacon in range.
        // They are kept (a base whose name never resolves must still be
        // selectable) but pushed below everything nameable and capped, so they
        // cannot bury the one row that matters.
        val unnamed = found.entries.filter { it.value.isNullOrBlank() }
            .sortedBy { it.key }
            .take(maxUnnamed)

        val namedRows = named
            .map { (addr, name) -> (name ?: addr) to addr }
            .sortedWith(
                compareByDescending<Pair<String, String>> { looksLikeBase(it.first, it.second) }
                    .thenBy { it.first.lowercase() }
            )
        val unnamedRows = unnamed.map { (addr, _) ->
            // Say what it is rather than showing a bare MAC with no explanation.
            (if (addr.equals(LooiBleLink.KNOWN_LOOI_MAC, ignoreCase = true)) {
                "LOOI base (unnamed) $addr"
            } else {
                "unnamed device $addr"
            }) to addr
        }
        // A known LOOI MAC outranks everything, named or not.
        return (namedRows + unnamedRows)
            .sortedByDescending { it.second.equals(LooiBleLink.KNOWN_LOOI_MAC, ignoreCase = true) }
    }

    /** Bring up a LOOI base over classic Bluetooth (RFCOMM/SPP) to a paired device. */
    fun connectBluetooth(address: String): Boolean =
        bringUp(BluetoothRobotTransport(address), "LOOI body (BT) @ $address",
            "LOOI BT unreachable ($address) - simulated")

    /**
     * Connect a LOOI base over BLE using the exact Memba 2 droid setup:
     * already-connected GATT attach -> BLE scan -> known-MAC fallback ->
     * connectGatt -> service discovery -> serial CCCD/read init -> keep-alive.
     * Pass a saved MAC in [address] to skip straight to that device, or blank
     * for full auto discovery. Blocking; call from a background thread.
     */
    fun connectLooiBle(context: android.content.Context, address: String = ""): Boolean {
        // Idempotent: if a healthy live link already exists, keep it. Repeated
        // refreshRobot()/onResume calls must not reconnect and drop the GATT.
        if (bleLink?.state == LooiBleLink.LinkState.READY) {
            _status.value = "LOOI body (BLE)" + if (address.isBlank()) "" else " @ $address"
            return true
        }
        var link = LooiBleLink(context.applicationContext)
        link.onStateChange = { st, detail -> _status.value = "LOOI BLE: ${st.name.lowercase()} $detail".trim() }
        var ok = if (address.isBlank()) link.connectAuto() else link.connectBlocking(address)
        if (!ok && address.isNotBlank()) {
            // Saved address failed (stale/wrong MAC). Fall back to the full
            // auto policy: attach-if-connected -> scan -> known-MAC connect.
            link.close()
            link = LooiBleLink(context.applicationContext)
            link.onStateChange = { st, detail -> _status.value = "LOOI BLE: ${st.name.lowercase()} $detail".trim() }
            ok = link.connectAuto()
        }
        return if (ok) {
            transport?.close()
            transport = null
            bleLink?.close()
            bleLink = link
            controller = BleMotorController(link)
            // Report the address we are ACTUALLY attached to, not the one that
            // was requested. Those differ whenever the requested MAC fails and
            // the auto/known-MAC fallback succeeds — which is exactly what
            // happened when a stale saved value pointed at a Bluetooth speaker:
            // the link was correctly attached to the real base while Settings
            // displayed the speaker's address, so a working robot looked broken.
            val connectedTo = link.connectedAddress ?: address
            _status.value =
                "LOOI body (BLE)" + if (connectedTo.isBlank()) "" else " @ $connectedTo"
            true
        } else {
            link.close()
            transport?.close()
            transport = null
            bleLink = null
            controller = SimulatedMotorController()
            _status.value = "LOOI BLE unreachable - simulated"
            false
        }
    }

    /**
     * Maps Meow's servo/expression vocabulary onto the Memba-verified LOOI
     * BLE channels: head-tilt servo -> neck pitch (fed1), body servo ->
     * short rotate pulse (fe00 frames), expression -> headlight (fed2).
     */
    private class BleMotorController(private val link: LooiBleLink) : MotorController {
        private var lastNeck = Float.NaN
        private var lastPanAtMs = 0L
        private var lastPanSign = 0

        override fun setServo(id: Int, angleDeg: Float) {
            val clamped = angleDeg.coerceIn(0f, 180f)
            when (id) {
                FaceMotorMap.SERVO_HEAD_TILT -> {
                    val pitch = (clamped - 90f) / 90f
                    if (!lastNeck.isNaN() && kotlin.math.abs(pitch - lastNeck) < 0.03f) return
                    lastNeck = pitch
                    link.setNeckPitch(pitch)
                }
                FaceMotorMap.SERVO_HEAD_PAN -> {
                    // No pan servo on the BLE base — turn in place toward the face.
                    val delta = clamped - 90f
                    val now = System.currentTimeMillis()
                    if (kotlin.math.abs(delta) < 8f) {
                        if (lastPanSign != 0) {
                            link.stopAll()
                            lastPanSign = 0
                        }
                        return
                    }
                    if (now - lastPanAtMs < 220L) return
                    lastPanAtMs = now
                    val sign = if (delta > 0f) 1 else -1
                    lastPanSign = sign
                    val speed = (kotlin.math.abs(delta) * 1.4f).toInt().coerceIn(15, 50)
                    link.turnPulse(sign * speed, 4)
                }
                FaceMotorMap.SERVO_BODY -> {
                    val delta = (clamped - 90f).toInt()
                    if (delta != 0) link.turnPulse(delta.coerceIn(-40, 40), 3)
                }
                else -> Unit // ears/tail have no BLE actuator
            }
        }

        override fun setExpression(face: String) {
            // Sleepy dims the light; anything awake turns it on.
            link.setHeadlight(face.lowercase() != "sleepy")
        }
    }

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

    /** True when a real LOOI body is attached over BLE and ready for commands. */
    val hasLiveBody: Boolean get() = bleLink?.connected == true

    /**
     * Every motion the base can perform, as one closed vocabulary.
     *
     * Named rather than free-form so the same set can be driven by the voice
     * fast path, the language model, and the Settings UI without any of them
     * inventing a motion the hardware does not have. Each carries the phrases a
     * person actually says for it, which is what [fromPhrase] matches on.
     */
    enum class Motion(
        val phrases: List<String>,
        /**
         * True when the avatar owns this phrase in conversation.
         *
         * These motions are expressive, and the face already performs them —
         * and performing them moves the body anyway, because FaceMotorMap
         * drives the servos from the played clip. So "shake your head" should
         * reach the face (head shakes, servos follow), while "spin around"
         * should reach the motors directly, because no face can spin. The
         * distinction keeps one phrase from having two different owners.
         */
        val faceOwnsPhrase: Boolean = false,
    ) {
        FORWARD(listOf("forward", "go forward", "drive forward", "come here", "move forward", "ahead")),
        BACK(listOf("back", "backward", "backwards", "go back", "reverse", "back up")),
        LEFT(listOf("left", "turn left", "go left")),
        RIGHT(listOf("right", "turn right", "go right")),
        SPIN(listOf("spin", "spin around", "spin in a circle", "turn around", "twirl", "circle")),
        STOP(listOf("stop", "halt", "stay", "freeze", "stop moving", "whoa")),
        LOOK_UP(listOf("look up", "head up", "chin up")),
        LOOK_DOWN(listOf("look down", "head down", "look at the floor")),
        LOOK_CENTER(listOf("look ahead", "center your head", "head center")),
        NOD(listOf("nod", "nod your head", "say yes"), faceOwnsPhrase = true),
        SHAKE_HEAD(listOf("shake your head", "shake head", "say no"), faceOwnsPhrase = true),
        WIGGLE(listOf("wiggle", "dance", "shimmy", "wiggle around"), faceOwnsPhrase = true),
        HEADLIGHT_ON(listOf("headlight on", "lights on", "turn on the headlight", "turn on the lights", "light on")),
        HEADLIGHT_OFF(listOf("headlight off", "lights off", "turn off the headlight", "turn off the lights", "light off")),
        ;

        companion object {
            /**
             * Resolve a spoken phrase to a motion.
             *
             * Longest phrase first, so "turn off the lights" cannot be won by a
             * shorter sibling. [includeFaceOwned] is false for the router's
             * fast path, where the avatar has already had its turn; the tool
             * keeps them so the model can still command a nod explicitly.
             */
            fun fromPhrase(text: String, includeFaceOwned: Boolean = true): Motion? {
                val t = text.lowercase()
                return entries
                    .filter { includeFaceOwned || !it.faceOwnsPhrase }
                    .flatMap { m -> m.phrases.map { it to m } }
                    .sortedByDescending { it.first.length }
                    .firstOrNull { (phrase, _) -> t.contains(phrase) }
                    ?.second
            }
        }
    }

    /**
     * Perform [motion] on the physical base. Returns a sentence describing what
     * happened, or null when there is no live body — the caller reports that
     * honestly rather than pretending the robot moved.
     *
     * Blocking (the movements have real durations); call off the main thread.
     */
    fun perform(motion: Motion, intensity: Float = 1f): String? {
        val link = bleLink?.takeIf { it.connected } ?: return null
        val k = intensity.coerceIn(0.2f, 1f)
        return when (motion) {
            Motion.FORWARD -> { link.driveSpeed(0.55f * k); Thread.sleep(900); link.stopAll(); "Moved forward." }
            Motion.BACK -> {
                // The verified drive channel (fed0) is forward-only, so backing
                // up is a half turn, a short drive, and a half turn back —
                // reported honestly rather than silently doing nothing.
                link.turnPulse(60, 18); Thread.sleep(700)
                link.driveSpeed(0.5f * k); Thread.sleep(700); link.stopAll()
                link.turnPulse(-60, 18); Thread.sleep(700); link.stopAll()
                "Backed up (turned around, moved, turned back)."
            }
            Motion.LEFT -> { link.turnPulse((-60 * k).toInt(), 10); Thread.sleep(600); link.stopAll(); "Turned left." }
            Motion.RIGHT -> { link.turnPulse((60 * k).toInt(), 10); Thread.sleep(600); link.stopAll(); "Turned right." }
            Motion.SPIN -> { link.turnPulse((80 * k).toInt(), 42); Thread.sleep(2600); link.stopAll(); "Spun around." }
            Motion.STOP -> { link.stopAll(); "Stopped." }
            Motion.LOOK_UP -> { link.setNeckPitch(1f); "Looked up." }
            Motion.LOOK_DOWN -> { link.setNeckPitch(0f); "Looked down." }
            Motion.LOOK_CENTER -> { link.setNeckPitch(0.5f); "Head centered." }
            Motion.NOD -> {
                repeat(2) {
                    link.setNeckPitch(0.15f); Thread.sleep(320)
                    link.setNeckPitch(0.75f); Thread.sleep(320)
                }
                link.setNeckPitch(0.5f); "Nodded."
            }
            Motion.SHAKE_HEAD -> {
                repeat(2) {
                    link.turnPulse(-45, 4); Thread.sleep(300)
                    link.turnPulse(45, 4); Thread.sleep(300)
                }
                link.stopAll(); "Shook my head."
            }
            Motion.WIGGLE -> {
                repeat(3) {
                    link.turnPulse(55, 5); Thread.sleep(240)
                    link.turnPulse(-55, 5); Thread.sleep(240)
                }
                link.stopAll(); "Wiggled."
            }
            Motion.HEADLIGHT_ON -> { link.setHeadlight(true); "Headlight on." }
            Motion.HEADLIGHT_OFF -> { link.setHeadlight(false); "Headlight off." }
        }
    }

    fun close() {
        transport?.close()
        transport = null
        bleLink?.close()
        bleLink = null
    }
}
