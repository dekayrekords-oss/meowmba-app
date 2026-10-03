package com.example.meowmba.core.ring

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import java.util.UUID

/** One process-wide GATT owner. All Bluetooth callbacks are serialized on the main thread. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class AndroidSmartRing private constructor(private val context: Context) : SmartRingDriver {
    companion object {
        private var singleton: AndroidSmartRing? = null
        fun get(context: Context) = singleton ?: AndroidSmartRing(context.applicationContext).also { singleton = it }
        private val SERVICE = UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")
        private val WRITE = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val NOTIFY = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    private val prefs = context.getSharedPreferences("smart_ring", Context.MODE_PRIVATE)
    override val state = MutableStateFlow(RingState(
        sleepEnabled = prefs.getBoolean("sleep_enabled", true),
        sleepAfterSeconds = prefs.getInt("sleep_after_seconds", 30).coerceIn(5, 300),
        paused = prefs.getBoolean("paused", true), reverse = prefs.getBoolean("reverse", false),
        distance = prefs.getFloat("distance", .35f).coerceIn(.15f, .65f),
        allApps = prefs.getBoolean("all_apps", false), showTarget = prefs.getBoolean("show_target", true),
        allowlist = prefs.getString("allowlist", context.packageName) ?: context.packageName,
        verifiedGestures = prefs.getStringSet("verified_gestures", emptySet())?.toSet() ?: emptySet(),
        inputDevice = prefs.getString("input_name", "") ?: "",
        mappings = defaultRingMappings().mapValues { (key, default) ->
            RingAction.entries.find { it.name == prefs.getString("map_$key", "") } ?: default
        },
    ))
    init {
        if (prefs.getInt("cursor_axes_revision", 0) < 1) {
            val migrated = ringMigrateCursorAxes(state.value.mappings)
            val edit = prefs.edit().putInt("cursor_axes_revision", 1)
            if (migrated != state.value.mappings) {
                state.value = state.value.copy(mappings = migrated)
                migrated.forEach { (gesture, action) -> edit.putString("map_$gesture", action.name) }
            }
            edit.apply()
        }
    }
    private val main = Handler(Looper.getMainLooper())
    private val idleSleep = RingIdleSleep(
        now = SystemClock::elapsedRealtime,
        schedule = { delay, action ->
            val task = Runnable { action() }; main.postDelayed(task, delay)
            val cancel: () -> Unit = { main.removeCallbacks(task) }; cancel
        },
        publish = { asleep ->
            state.value = state.value.copy(sleeping = asleep, pressedKeys = emptySet())
            RingAccessibilityService.instance?.cancelPending()
            log(if (asleep) "Ring controls asleep; double tap to wake" else "Ring controls awake")
        },
    )
    private val sleepMonitor = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
        state.map { ringSleepEligible(it) to it.sleepAfterSeconds }.distinctUntilChanged()
            .collect { (enabled, seconds) -> idleSleep.configure(enabled, seconds) }
    }
    fun checkSleepDeadline() = idleSleep.checkIdle()
    fun controlsActivity() = idleSleep.activity()
    fun consumeSleepingGesture(gesture: String): Boolean {
        val consumed = idleSleep.consumeSleepingGesture(gesture)
        if (consumed) log(if (state.value.sleeping) "$gesture: ignored while asleep" else "Double tap: wake only")
        return consumed
    }
    override fun setSleepEnabled(value: Boolean) {
        prefs.edit().putBoolean("sleep_enabled", value).apply(); state.value = state.value.copy(sleepEnabled = value)
    }
    override fun setSleepAfterSeconds(value: Int) {
        val seconds = value.coerceIn(5, 300)
        prefs.edit().putInt("sleep_after_seconds", seconds).apply(); state.value = state.value.copy(sleepAfterSeconds = seconds)
    }
    override fun sleepNow() = idleSleep.sleepNow()
    override fun wakeControls() = idleSleep.wake()
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var write: BluetoothGattCharacteristic? = null
    private var commandIndex = 0
    private val sensorWriteTimeout = Runnable { fail("Sensor Bluetooth write timed out. Reconnect the ring.") }
    private val sensorSession = RingSensorSession(
        send = { bytes ->
            val client = gatt
            val characteristic = write
            val accepted = client != null && characteristic != null && hasPermissions() && runCatching {
                val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                if (Build.VERSION.SDK_INT >= 33) client.writeCharacteristic(characteristic, bytes, type) == BluetoothStatusCodes.SUCCESS
                else { characteristic.value = bytes; characteristic.writeType = type; client.writeCharacteristic(characteristic) }
            }.getOrDefault(false)
            if (accepted) main.postDelayed(sensorWriteTimeout, 8_000)
            accepted
        },
        schedule = { delay, action ->
            val task = Runnable { action() }; main.postDelayed(task, delay)
            val cancel: () -> Unit = { main.removeCallbacks(task) }; cancel
        },
        publish = { active, readings -> state.value = state.value.copy(activeSensor = active, sensors = readings) },
    )
    private var selected: BluetoothDevice? = null
    private var bondReceiver: BroadcastReceiver? = null
    private var pendingScan = false
    private var wantedConnection = false
    private var retries = 0
    private val retry = Runnable { selected?.let(::openGatt) }
    private val timeout = Runnable { fail("Connection timed out. Wake the ring and try again.") }
    private val stopScan = Runnable {
        scanner?.let { runCatching { it.stopScan(scanCallback) } }; scanner = null
        state.value = state.value.copy(scanning = false,
            status = if (state.value.devices.isEmpty()) "No R08 found. Wake the ring and disconnect QRing." else "Select your R08 ring")
    }
    var verifiedDescriptor: String = prefs.getString("input_descriptor", "") ?: ""
        private set
    fun verifyInput(descriptor: String, name: String): Boolean {
        if (verifiedDescriptor.isNotEmpty() && verifiedDescriptor != descriptor) return false
        verifiedDescriptor = descriptor
        prefs.edit().putString("input_descriptor", descriptor).putString("input_name", name).apply()
        state.value = state.value.copy(inputDevice = name)
        return true
    }
    fun recordPress(key: Int, down: Boolean, label: String) {
        controlsActivity()
        val current = state.value
        state.value = current.copy(pressedKeys = if (down) current.pressedKeys + key else current.pressedKeys - key,
            lastInput = "${if (down) "Pressed" else "Released"}: $label", inputSequence = current.inputSequence + 1)
        log("${if (down) "Press" else "Release"}: $label")
    }
    fun recordGesture(gesture: String) {
        val current = state.value
        state.value = current.copy(lastInput = gesture, inputSequence = current.inputSequence + 1,
            gestureCounts = current.gestureCounts + (gesture to ((current.gestureCounts[gesture] ?: 0) + 1)))
    }
    fun verifyGesture(gesture: String) {
        val verified = state.value.verifiedGestures + gesture
        prefs.edit().putStringSet("verified_gestures", verified).apply()
        state.value = state.value.copy(verifiedGestures = verified)
        log("Confirmed: $gesture (test only)")
    }
    var permissionRequest: (() -> Unit)? = null
    private fun hasPermissions() = permissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun permissions() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun permissionResult() {
        if (pendingScan) { pendingScan = false; if (hasPermissions()) scan() else message("Bluetooth permission was denied") }
    }
    fun message(text: String) { state.value = state.value.copy(status = text); log(text) }
    fun log(text: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        state.value = state.value.copy(events = (listOf("$stamp $text") + state.value.events).take(60))
    }
    override fun scan() {
        if (!hasPermissions()) { pendingScan = true; permissionRequest?.invoke() ?: message("Open Meowmba to grant Bluetooth permission"); return }
        if (adapter?.isEnabled != true) { message("Turn on Bluetooth, then scan again"); return }
        disconnect()
        state.value = state.value.copy(devices = emptyList())
        adapter?.bondedDevices?.filter { it.name?.contains("R08", true) == true }?.forEach { addDevice(it, it.name ?: "R08") }
        scanner = adapter?.bluetoothLeScanner
        if (scanner == null) { message("Bluetooth LE scanning is unavailable"); return }
        state.value = state.value.copy(scanning = true, status = "Scanning for R08 (15 seconds)")
        runCatching { scanner?.startScan(scanCallback) }.onFailure { scanner = null; state.value = state.value.copy(scanning = false); message("Scan failed: ${it.message}"); return }
        main.postDelayed(stopScan, 15_000)
    }
    private fun addDevice(device: BluetoothDevice, name: String) {
        if (state.value.devices.none { it.address == device.address })
            state.value = state.value.copy(devices = state.value.devices + RingDevice(device.address, name))
    }
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) { main.post {
            if (!state.value.scanning || !hasPermissions()) return@post
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return@post
            if (name.contains("R08", true)) addDevice(result.device, name)
        } }
        override fun onScanFailed(error: Int) { main.post { main.removeCallbacks(stopScan); scanner = null
            state.value = state.value.copy(scanning = false); message("Scan failed ($error). Try Bluetooth settings.") } }
    }
    override fun connect(address: String) {
        if (!hasPermissions()) { message("Grant Bluetooth permission using Scan first"); return }
        if (state.value.devices.none { it.address == address }) return
        disconnect()
        val device = adapter?.getRemoteDevice(address) ?: return
        selected = device; wantedConnection = true; retries = 0
        if (prefs.getString("address", "") != address) {
            verifiedDescriptor = ""
            prefs.edit().remove("input_descriptor").remove("input_name").remove("verified_gestures").putBoolean("paused", true).apply()
            state.value = state.value.copy(paused = true, verifiedGestures = emptySet(), inputDevice = "")
        }
        prefs.edit().putString("address", address).apply()
        state.value = state.value.copy(selectedName = device.name ?: "R08", status = "Pairing / connecting")
        if (device.bondState == BluetoothDevice.BOND_BONDED) openGatt(device)
        else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent == null) return
                    val changed = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (changed?.address != selected?.address) return
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                        BluetoothDevice.BOND_BONDED -> { clearBondReceiver(); main.removeCallbacks(timeout); openGatt(device) }
                        BluetoothDevice.BOND_NONE -> fail("Pairing cancelled or failed. Try again.")
                    }
                }
            }
            bondReceiver = receiver
            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            if (device.bondState != BluetoothDevice.BOND_BONDING && !device.createBond()) fail("Android could not start pairing")
            else main.postDelayed(timeout, 45_000)
        }
    }
    private fun clearBondReceiver() { bondReceiver?.let { runCatching { context.unregisterReceiver(it) } }; bondReceiver = null }
    private fun openGatt(device: BluetoothDevice) {
        if (!wantedConnection || !hasPermissions()) return
        main.removeCallbacks(timeout)
        gatt?.let { runCatching { it.close() } }; write = null
        message("Connecting to ${state.value.selectedName}")
        runCatching { gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }
            .onFailure { fail("Bluetooth connection failed: ${it.message}"); return }
        main.postDelayed(timeout, 20_000)
    }
    private val callback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) { main.post {
            if (client !== gatt) return@post
            if (!hasPermissions()) { fail("Bluetooth permission was removed"); return@post }
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                message("Discovering R08 controls")
                if (!client.discoverServices()) fail("Service discovery could not start")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                main.removeCallbacks(timeout); client.close(); gatt = null; write = null
                main.removeCallbacks(sensorWriteTimeout); sensorSession.disconnected()
                state.value = state.value.copy(connected = false, pressedKeys = emptySet())
                if (wantedConnection && hasPermissions() && retries++ < 3) {
                    message("Ring disconnected ($status); reconnecting"); main.postDelayed(retry, 3000)
                } else { wantedConnection = false; message("Disconnected ($status). Wake the ring and reconnect.") }
            }
        } }
        override fun onServicesDiscovered(client: BluetoothGatt, status: Int) { main.post {
            if (client !== gatt) return@post
            if (!hasPermissions()) { fail("Bluetooth permission was removed"); return@post }
            if (status != BluetoothGatt.GATT_SUCCESS) { fail("R08 service discovery failed ($status)"); return@post }
            val service = client.getService(SERVICE)
            write = service?.getCharacteristic(WRITE)
            val notify = service?.getCharacteristic(NOTIFY)
            val descriptor = notify?.getDescriptor(CCCD)
            if (write == null || notify == null || descriptor == null) { fail("This device does not expose the R08 control protocol"); return@post }
            if (!client.setCharacteristicNotification(notify, true)) { fail("Could not enable R08 notifications"); return@post }
            val accepted = if (Build.VERSION.SDK_INT >= 33) client.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                else { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; client.writeDescriptor(descriptor) }
            if (!accepted) fail("Could not subscribe to R08 controls")
        } }
        override fun onDescriptorWrite(client: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) { main.post {
            if (client !== gatt) return@post
            if (!hasPermissions()) { fail("Bluetooth permission was removed"); return@post }
            if (status != BluetoothGatt.GATT_SUCCESS) { fail("Notification setup failed ($status)"); return@post }
            commandIndex = 0; sendCommand()
        } }
        override fun onCharacteristicWrite(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { main.post {
            if (client !== gatt || characteristic.uuid != WRITE) return@post
            if (state.value.connected) {
                main.removeCallbacks(sensorWriteTimeout)
                sensorSession.onWrite(status == BluetoothGatt.GATT_SUCCESS)
                if (status != BluetoothGatt.GATT_SUCCESS) fail("Sensor Bluetooth write failed ($status). Reconnect the ring.")
                return@post
            }
            if (status != BluetoothGatt.GATT_SUCCESS) { fail("Ring configuration failed ($status)"); return@post }
            commandIndex++
            // Small gap prevents overwhelming older R08 firmware; only one GATT write is outstanding.
            main.postDelayed({ if (client === gatt) sendCommand() }, 180)
        } }
        override fun onCharacteristicChanged(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid != NOTIFY) return
            val bytes = value.copyOf()
            main.post {
                if (client !== gatt) return@post
                if (bytes.firstOrNull() == 0x3b.toByte()) log("Ring control acknowledgement")
                else sensorSession.receive(bytes)
            }
        }
        @Deprecated("Legacy callback")
        override fun onCharacteristicChanged(client: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) { val bytes = characteristic.value?.copyOf() ?: return; onCharacteristicChanged(client, characteristic, bytes) }
        }
    }
    private fun sendCommand() {
        if (!hasPermissions()) { fail("Bluetooth permission was removed"); return }
        val client = gatt ?: return
        val characteristic = write ?: return
        if (commandIndex >= 3) {
            main.removeCallbacks(timeout); retries = 0
            state.value = state.value.copy(connected = true)
            message("R08 media-key mode configured. Test swipes and taps.")
            return
        }
        val bytes = ringPacket(commandIndex)
        val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val accepted = if (Build.VERSION.SDK_INT >= 33) client.writeCharacteristic(characteristic, bytes, type) == BluetoothStatusCodes.SUCCESS
            else { characteristic.value = bytes; characteristic.writeType = type; client.writeCharacteristic(characteristic) }
        if (!accepted) fail("Could not write R08 configuration")
        else log("Configure media-key mode: step ${commandIndex + 1}/3")
    }
    private fun fail(reason: String) { disconnect(); message(reason) }
    override fun testSensor(sensor: RingSensor) {
        if (state.value.connected && hasPermissions()) sensorSession.start(sensor)
        else message("Connect the ring before testing sensors")
    }
    override fun stopSensorTest() = sensorSession.stop()
    override fun clearTestResults() {
        sensorSession.clear()
        state.value = state.value.copy(gestureCounts = emptyMap(), lastInput = "Waiting for ring input", events = emptyList())
    }
    override fun disconnect() {
        main.removeCallbacks(sensorWriteTimeout); sensorSession.disconnected()
        wantedConnection = false; main.removeCallbacks(retry); main.removeCallbacks(timeout); main.removeCallbacks(stopScan)
        scanner?.let { runCatching { it.stopScan(scanCallback) } }; scanner = null
        clearBondReceiver(); val old = gatt; gatt = null; write = null
        old?.let { runCatching { it.disconnect(); it.close() } }
        state.value = state.value.copy(scanning = false, connected = false, testing = false, pressedKeys = emptySet(), status = "Disconnected")
        RingAccessibilityService.instance?.cancelPending()
    }
    fun restoreConnection() {
        retries = 0
        if (!hasPermissions() || wantedConnection) return
        val address = prefs.getString("address", null) ?: return
        val device = adapter?.bondedDevices?.find { it.address == address } ?: return
        selected = device; wantedConnection = true
        state.value = state.value.copy(selectedName = device.name ?: "R08")
        openGatt(device)
    }
    fun accessibilityConnected(value: Boolean) { state.value = state.value.copy(accessibility = value) }
    private fun launch(intent: Intent) { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { message("Could not open settings: ${it.message}") } }
    override fun openAccessibility() {
        if (com.example.meowmba.BuildConfig.PHONE_AUTOMATION_ENABLED) launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        else message("Cross-app ring controls are unavailable in this edition.")
    }
    override fun openBluetooth() = launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    override fun resetVerification() {
        verifiedDescriptor = ""
        prefs.edit().remove("input_descriptor").remove("input_name").remove("verified_gestures").putBoolean("paused", true).apply()
        state.value = state.value.copy(paused = true, testing = false, inputDevice = "", verifiedGestures = emptySet())
        RingAccessibilityService.instance?.cancelPending()
    }
    override fun setPaused(value: Boolean) { prefs.edit().putBoolean("paused", value).apply(); state.value = state.value.copy(paused = value); RingAccessibilityService.instance?.cancelPending() }
    override fun setTesting(value: Boolean) { state.value = state.value.copy(testing = value, pressedKeys = emptySet()); RingAccessibilityService.instance?.cancelPending() }
    override fun setReverse(value: Boolean) { prefs.edit().putBoolean("reverse", value).apply(); state.value = state.value.copy(reverse = value) }
    override fun setDistance(value: Float) { val distance = value.coerceIn(.15f, .65f); prefs.edit().putFloat("distance", distance).apply(); state.value = state.value.copy(distance = distance) }
    override fun setAllApps(value: Boolean) {
        prefs.edit().putBoolean("all_apps", value).apply(); state.value = state.value.copy(allApps = value)
        RingAccessibilityService.instance?.cancelPending()
    }
    override fun setShowTarget(value: Boolean) {
        prefs.edit().putBoolean("show_target", value).apply(); state.value = state.value.copy(showTarget = value)
    }
    override fun centerTarget() { RingAccessibilityService.instance?.centerTarget() }
    override fun setAllowlist(value: String) { prefs.edit().putString("allowlist", value).apply(); state.value = state.value.copy(allowlist = value); RingAccessibilityService.instance?.cancelPending() }
    override fun setMapping(gesture: String, action: RingAction) { prefs.edit().putString("map_$gesture", action.name).apply(); state.value = state.value.copy(mappings = state.value.mappings + (gesture to action)); RingAccessibilityService.instance?.cancelPending() }
    fun allowedAppChoices(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            val apps = context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            (apps + (context.packageName to context.applicationInfo.loadLabel(context.packageManager).toString()))
                .distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }.getOrElse { listOf(context.packageName to "Meowmba") }
    }
    override fun exportDiagnostics() {
        val text = "Meowmba R08 diagnostics\n${state.value.status}\nAccessibility: ${state.value.accessibility}\n" + state.value.events.reversed().joinToString("\n")
        launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share ring diagnostics"))
    }
}
