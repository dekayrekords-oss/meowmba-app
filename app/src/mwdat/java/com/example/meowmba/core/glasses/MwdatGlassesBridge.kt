package com.example.meowmba.core.glasses

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import android.util.Log
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.removeStream
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real Meta Wearables Device Access Toolkit (mwdat) 0.8.0 bridge.
 *
 * VERIFIED against the actual downloaded .aar classes (javap), not docs —
 * mwdat-core/mwdat-camera 0.8.0 ship under `com.meta.wearable.dat.*` with a
 * different layout than the first draft of this file assumed:
 *   - AutoDeviceSelector lives in `core.selectors`, not `core`.
 *   - StreamConfiguration / VideoQuality live in `camera.types`, not `camera`.
 *   - `addStream` is a top-level extension fun on DeviceSession
 *     (`camera.SessionStreamExtensionsKt`) — must be imported explicitly.
 *   - `Wearables.createSession(...)` and `session.addStream(...)` both return
 *     `DatResult<T, E>` (an inline value class wrapping T|error, same call
 *     shape as kotlin.Result) — `.getOrElse` / `.fold` on it resolve to real
 *     generated extension functions, confirmed via javap.
 *
 * `Stream.videoStream` emits `VideoFrame(buffer: ByteBuffer, width, height,
 * presentationTimeUs, isCompressed, isCodecConfig)` with no format field and
 * no SDK-provided Bitmap helper (confirmed: zero `android.graphics.Bitmap`
 * references anywhere in mwdat-core/mwdat-camera). [MwdatVideoFrameDecoder]
 * does the actual decode using only standard Android platform APIs
 * (MediaCodec + ImageReader for H.264, stride-aware YUV/RGBA conversion for
 * raw) — see that file's doc for exactly what's assumed vs. guaranteed.
 *
 * Gestures/IMU: no public capability for either exists in 0.8.0 (only
 * unreachable `internal.events.GestureType` etc.) — those stay mock-only
 * even against real hardware until Meta ships a public surface for them.
 *
 * Registration gotcha (found 2026-07-03): `createSession(AutoDeviceSelector())`
 * reports NO_ELIGIBLE_DEVICE even with the glasses connected and dev mode on
 * in the Meta AI app, UNLESS this specific app has separately been through
 * `Wearables.startRegistration(activity)` — a one-time per-app consent
 * handshake tracked via `Wearables.registrationState` (persisted in
 * SharedPreferences by the SDK once granted). Bluetooth pairing and the
 * Meta AI app connection are necessary but not sufficient. See
 * [ensureRegistered] — must be called with an Activity (from the UI, not the
 * background service) before the first connect().
 * ───────────────────────────────────────────────────────────────────────────
 */
class MwdatGlassesBridge : MetaGlassesController.GlassesBridge {

    companion object {
        // The SDK allows exactly ONE session per device, tracked process-wide
        // in its internals — but this app creates multiple bridge instances
        // (MainActivity and MembaForegroundService each own a
        // MetaGlassesController). Observed 2026-07-03: a session created by one
        // instance that never reached STARTED was left dangling; the other
        // instance's disconnect() couldn't stop it (different bridge), and
        // every reconnect after that failed with "A session already exists
        // for this device". Track the live session here, across instances,
        // and always stop it before creating a new one.
        @Volatile private var activeSession: DeviceSession? = null

        // CONCURRENCY FIX (2026-07-08): activeSession being shared+@Volatile
        // only protects the READ; the check-stop-createSession-assign
        // sequence in attemptStream() was NOT atomic, so two bridge
        // instances (WastelandApp's UI connect + MembaForegroundService's
        // background capture loop, which independently calls connect() on
        // its OWN MetaGlassesController whenever isConnected is false) could
        // both pass the "activeSession is null/stale" check and both call
        // createSession() for the same physical device — the SDK only
        // allows one. Symptom: connect races, each side's session never
        // reaches STARTED, the 3-attempt retry in attemptStream burns
        // through and gives up ("cycles 3 times then stops"), sometimes
        // surfacing as a cancelled coroutine if one racer's owning scope
        // (e.g. a recomposing Composable) tears down mid-attempt. This
        // mutex makes session creation process-wide exclusive.
        private val connectMutex = Mutex()
    }

    // Recreatable scope — disconnect() must NOT permanently kill reconnects.
    private var supervisor = SupervisorJob()
    private val scope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Default + supervisor)
    private var session: DeviceSession? = null
    private var decoder: MwdatVideoFrameDecoder? = null
    private var currentStream: Stream? = null
    private var appliedMode: CaptureMode? = null
    private val streamReconfigMutex = Mutex()
    // Video/error collectors for the CURRENT stream — must be cancelled on
    // reconfigure or dead-stream collectors accumulate across mode switches.
    private val streamJobs = mutableListOf<kotlinx.coroutines.Job>()

    private fun ensureScope() {
        if (supervisor.isCancelled) {
            supervisor = SupervisorJob()
        }
    }

    /**
     * One-time per-app registration handshake. Must succeed before the
     * first createSession() call can ever find an eligible device. Returns
     * true once RegistrationState.REGISTERED is reached (either already
     * registered, or the user approved the Meta AI consent screen this call
     * triggers). Requires an Activity — cannot be called from a background
     * service. Purely global-SDK-state; safe to call on a throwaway
     * instance (doesn't touch this instance's session/decoder).
     */
    override suspend fun ensureRegistered(activity: Activity, timeoutMs: Long): Boolean {
        Wearables.initialize(activity.applicationContext)
        if (Wearables.registrationState.value == RegistrationState.REGISTERED) return true

        Log.i("MeowGlasses", "MWDAT: not registered yet — launching Meta AI consent screen…")
        Wearables.startRegistration(activity)

        val finalState = withTimeoutOrNull(timeoutMs) {
            Wearables.registrationState.first {
                it == RegistrationState.REGISTERED || it == RegistrationState.UNAVAILABLE
            }
        }
        return when (finalState) {
            RegistrationState.REGISTERED -> {
                Log.i("MeowGlasses", "MWDAT: registration approved")
                true
            }
            RegistrationState.UNAVAILABLE -> {
                Log.e("MeowGlasses", "MWDAT: registration denied or unavailable")
                false
            }
            null -> {
                Log.e("MeowGlasses", "MWDAT: registration timed out after ${timeoutMs}ms — did you approve the Meta AI consent screen?")
                false
            }
            else -> false
        }
    }

    /**
     * DAT device permissions (camera) are granted per-app through the Meta AI
     * app, and NOTHING requests them automatically — the SDK just fails the
     * stream with PERMISSIONS_DENIED (observed 2026-07-03: registration OK,
     * session STARTED, stream up, then denied; no prompt anywhere). The
     * public request path, found via javap, is Wearables.RequestPermissionContract —
     * an ActivityResultContract the APP must launch; it opens the Meta AI
     * grant screen and returns Granted/Denied. There is no standing settings
     * toggle in the Meta AI app until this has been requested at least once.
     */
    override suspend fun ensureCameraPermission(activity: Activity): Boolean {
        Wearables.initialize(activity.applicationContext)

        val alreadyGranted = Wearables.checkPermissionStatus(Permission.CAMERA).fold(
            onSuccess = { it is PermissionStatus.Granted },
            onFailure = { _, _ -> false },
        )
        if (alreadyGranted) {
            Log.i("MeowGlasses", "MWDAT: CAMERA permission already granted")
            return true
        }

        val host = activity as? ComponentActivity
        if (host == null) {
            Log.e("MeowGlasses", "MWDAT: activity is not a ComponentActivity — cannot launch permission request")
            return false
        }

        Log.i("MeowGlasses", "MWDAT: requesting CAMERA permission via Meta AI app…")
        val outcome = CompletableDeferred<Boolean>()
        var launcher: ActivityResultLauncher<Permission>? = null
        launcher = host.activityResultRegistry.register(
            "mwdat_camera_permission_${System.currentTimeMillis()}",
            Wearables.RequestPermissionContract(),
        ) { result ->
            result.fold(
                onSuccess = { status ->
                    val granted = status is PermissionStatus.Granted
                    Log.i("MeowGlasses", 
                        if (granted) "MWDAT: CAMERA permission GRANTED by user"
                        else "MWDAT: CAMERA permission denied by user",
                    )
                    outcome.complete(granted)
                },
                onFailure = { error, _ ->
                    Log.e("MeowGlasses", "MWDAT: permission request failed: $error")
                    outcome.complete(false)
                },
            )
            launcher?.unregister()
        }
        launcher.launch(Permission.CAMERA)
        return outcome.await()
    }

    override fun connect(context: Context, manager: MetaGlassesController) {
        ensureScope()
        Wearables.initialize(context.applicationContext)

        val reg = Wearables.registrationState.value
        Log.i("MeowGlasses", "MWDAT connect: registrationState=$reg")
        if (reg != RegistrationState.REGISTERED) {
            Log.e("MeowGlasses", 
                "MWDAT: app not registered yet — tap Setup glasses / approve Meta AI consent.",
            )
            MetaGlassesController.setStatus("not registered — Setup glasses first")
            throw IllegalStateException("MWDAT not registered (state=$reg)")
        }

        // Two async state machines must settle before streaming works —
        // confirmed against decompiled mwdat-core 0.8.0 (javap), 2026-07-03:
        //
        // 1. DISCOVERY + LINK: Wearables.devices fills in asynchronously
        //    after initialize(), and a discovered device still has
        //    Device.linkState CONNECTING at first. AutoDeviceSelector only
        //    treats a CONNECTED device as eligible, so createSession()
        //    immediately after discovery still throws NO_ELIGIBLE_DEVICE
        //    (observed: device present but ineligible <100ms after boot,
        //    eligible ~35s later). Wait for metadata to show CONNECTED via
        //    Wearables.devicesMetadata.
        //
        // 2. SESSION START: DeviceSession.start() is fire-and-forget
        //    (IDLE→STARTING→STARTED, exposed via session.state StateFlow).
        //    Calling addStream() while still STARTING is what produced
        //    SESSION_ALREADY_STOPPED on a brand-new session. Wait for
        //    STARTED (handled in attemptStream).
        //
        // All async and fully caught — a throw inside scope.launch crashed
        // the whole app once already.
        scope.launch {
            try {
                val appContext = context.applicationContext
                Log.i("MeowGlasses", 
                    "MWDAT: isDevMode=${Wearables.isDevMode} reg=${Wearables.registrationState.value}",
                )
                MetaGlassesController.setStatus(
                    "waiting for glasses… (devMode=${Wearables.isDevMode})",
                )
                val deviceIds = withTimeoutOrNull(25_000) {
                    Wearables.devices.first { it.isNotEmpty() }
                }
                if (deviceIds == null) {
                    Log.e("MeowGlasses", 
                        "MWDAT: no device appeared within 25s — pair glasses in Meta AI.",
                    )
                    MetaGlassesController.setStatus("no glasses found — open Meta AI & pair")
                    return@launch
                }
                Log.i("MeowGlasses", "MWDAT: discovered ${deviceIds.size} device(s)")
                dumpDevices("initial", deviceIds)

                // IMPORTANT: Meta AI "connected" ≠ DAT LinkState.CONNECTED.
                // AutoDeviceSelector only accepts CONNECTED + COMPATIBLE.
                // Strategy:
                //  1) Wait briefly for a real CONNECTED+COMPATIBLE device.
                //  2) In parallel / after, try SpecificDeviceSelector sessions for
                //     every known id — createSession can wake the DAT link even
                //     when metadata still says DISCONNECTED.
                //  3) Fall back to AutoDeviceSelector.

                val idFlows = deviceIds.mapNotNull { id ->
                    Wearables.devicesMetadata[id]?.let { flow ->
                        flow.map { meta -> id to meta }
                    }
                }

                // Multi-pair: prefer CONNECTING (worn/waking) over already-CONNECTED ghosts
                // that often end with SESSION_ENDED_BY_DEVICE (other pair in the case).
                fun rank(meta: com.meta.wearable.dat.core.types.Device): Int = when (meta.linkState) {
                    LinkState.CONNECTING -> 3
                    LinkState.CONNECTED -> 2
                    else -> 0
                }

                // Wait up to 20s for a usable device; prefer CONNECTING→CONNECTED
                if (idFlows.isNotEmpty()) {
                    val ordered = withTimeoutOrNull(20_000) {
                        // Emit whenever any device becomes CONNECTED; pick best rank at that moment
                        merge(*idFlows.toTypedArray())
                            .first { (_, meta) ->
                                meta.linkState == LinkState.CONNECTED &&
                                    meta.compatibility ==
                                    com.meta.wearable.dat.core.types.DeviceCompatibility.COMPATIBLE
                            }
                    }
                    // Also re-rank all currently CONNECTED/CONNECTING devices
                    val candidates = deviceIds.mapNotNull { id ->
                        Wearables.devicesMetadata[id]?.value?.let { id to it }
                    }.filter { (_, m) ->
                        m.linkState == LinkState.CONNECTED || m.linkState == LinkState.CONNECTING
                    }.sortedByDescending { (_, m) -> rank(m) }

                    val tryOrder = buildList {
                        // Highest rank first
                        candidates.forEach { add(it.first) }
                        // Then any remaining ids (ghosts last)
                        deviceIds.forEach { id -> if (none { it == id }) add(id) }
                    }

                    if (ordered != null || candidates.isNotEmpty()) {
                        Log.i("MeowGlasses", 
                            "MWDAT: try order = " + tryOrder.joinToString {
                                val m = Wearables.devicesMetadata[it]?.value
                                "${m?.name}:${m?.linkState}"
                            },
                        )
                        for (id in tryOrder) {
                            val meta = Wearables.devicesMetadata[id]?.value
                            // If still CONNECTING, wait briefly for CONNECTED
                            if (meta?.linkState == LinkState.CONNECTING) {
                                MetaGlassesController.setStatus(
                                    "waiting for '${meta.name}' to finish CONNECTING…",
                                )
                                withTimeoutOrNull(15_000) {
                                    Wearables.devicesMetadata[id]!!.first {
                                        it.linkState == LinkState.CONNECTED
                                    }
                                }
                            }
                            val m2 = Wearables.devicesMetadata[id]?.value
                            Log.i("MeowGlasses", 
                                "MWDAT: try '${m2?.name}' link=${m2?.linkState}…",
                            )
                            MetaGlassesController.setStatus(
                                "starting camera on ${m2?.name ?: id.identifier.take(8)}…",
                            )
                            if (attemptStreamOnce(appContext, manager, id)) {
                                Log.i("MeowGlasses", 
                                    "MWDAT: stream up on '${m2?.name}'",
                                )
                                return@launch
                            }
                            Log.w("MeowGlasses", 
                                "MWDAT: '${m2?.name}' session failed — trying next pair",
                            )
                        }
                    }
                }

                // Fallback: every id + auto
                dumpDevices("pre-session-try", deviceIds)
                MetaGlassesController.setStatus("opening session on each pair…")
                for (id in deviceIds) {
                    if (attemptStreamOnce(appContext, manager, id)) {
                        Log.i("MeowGlasses", "MWDAT: stream up via SpecificDeviceSelector")
                        return@launch
                    }
                }

                // Keep waiting for CONNECTED while also retrying sessions
                if (idFlows.isNotEmpty()) {
                    val pollJob = launch {
                        var t = 12
                        while (t < 60) {
                            delay(5_000)
                            t += 5
                            dumpDevices("poll+${t}s", deviceIds)
                            // retry sessions every 15s while waiting
                            if (t % 15 == 0) {
                                for (id in deviceIds) {
                                    if (attemptStreamOnce(appContext, manager, id)) {
                                        Log.i("MeowGlasses", "MWDAT: stream up on retry +${t}s")
                                        return@launch
                                    }
                                }
                                // Auto selector (CONNECTED+COMPATIBLE only)
                                if (attemptStreamOnce(appContext, manager, null)) {
                                    Log.i("MeowGlasses", "MWDAT: stream up via Auto +${t}s")
                                    return@launch
                                }
                            }
                        }
                    }

                    val hit = withTimeoutOrNull(55_000) {
                        merge(*idFlows.toTypedArray()).first { (_, meta) ->
                            meta.linkState == LinkState.CONNECTED
                        }
                    }
                    if (hit != null) {
                        pollJob.cancel()
                        val meta = hit.second
                        Log.i("MeowGlasses", 
                            "MWDAT: '${meta.name}' CONNECTED " +
                                "(compat=${meta.compatibility}) — pinning…",
                        )
                        MetaGlassesController.setStatus(
                            "'${meta.name}' CONNECTED — starting camera…",
                        )
                        // Try the newly CONNECTED pair, then the other ids
                        if (attemptStreamOnce(appContext, manager, hit.first)) return@launch
                        for (id in deviceIds) {
                            if (id != hit.first && attemptStreamOnce(appContext, manager, id)) {
                                return@launch
                            }
                        }
                    }
                    // Let pollJob finish retries, or cancel and final try
                    pollJob.join()
                }

                Log.e("MeowGlasses", 
                    "MWDAT: could not open camera. Devices stay DISCONNECTED at DAT layer " +
                        "even if Meta AI shows linked. Fix: wear glasses · Meta AI open · " +
                        "Wearables developer mode · unpair unused second pair · " +
                        "Setup glasses again.",
                )
                MetaGlassesController.setStatus(
                    "DAT still DISCONNECTED — open Meta AI, wear glasses, unpair spare pair",
                )
                // Final hail-mary
                attemptStream(appContext, manager, null)
            } catch (e: Exception) {
                Log.e("MeowGlasses", "MWDAT: async connect failed: ${e.localizedMessage}")
                MetaGlassesController.setStatus("connect failed: ${e.localizedMessage}")
            }
        }
    }

    private fun dumpDevices(tag: String, deviceIds: Collection<DeviceIdentifier>) {
        val lines = deviceIds.map { id ->
            val m = Wearables.devicesMetadata[id]?.value
            val short = id.identifier.take(10)
            if (m == null) {
                "[$short no-meta]"
            } else {
                "[$short name=${m.name} link=${m.linkState} type=${m.deviceType} " +
                    "compat=${m.compatibility} fw=${m.firmwareInfo}]"
            }
        }
        Log.i("MeowGlasses", "MWDAT devices ($tag): ${lines.joinToString(" | ")}")
        MetaGlassesController.setStatus(
            lines.joinToString(" · ").ifBlank { "no devices" }.take(140),
        )
    }

    /**
     * One-shot session attempt. Returns true if stream started.
     * Unlike [attemptStream], does not burn the full 3× retry + Wearables.reset loop
     * so we can try multiple device ids quickly.
     */
    private suspend fun attemptStreamOnce(
        appContext: Context,
        manager: MetaGlassesController,
        targetDevice: DeviceIdentifier?,
    ): Boolean = connectMutex.withLock {
        activeSession?.let { existing ->
            if (existing.state.value == DeviceSessionState.STARTED) {
                Log.i("MeowGlasses", "MWDAT: adopting already-STARTED session")
                session = existing
                applyStreamForMode(existing, manager, MetaGlassesController.captureMode.value)
                return@withLock true
            }
            runCatching { existing.stop() }
            activeSession = null
            delay(300)
        }

        return@withLock try {
            val selector = targetDevice?.let { SpecificDeviceSelector(it) } ?: AutoDeviceSelector()
            val s = Wearables.createSession(selector).getOrElse { error ->
                Log.w("MeowGlasses", 
                    "MWDAT: createSession(${targetDevice?.identifier?.take(10) ?: "auto"}) " +
                        "→ $error",
                )
                return@withLock false
            }
            session = s
            activeSession = s
            s.start()
            val state = withTimeoutOrNull(20_000) {
                s.state.first {
                    it == DeviceSessionState.STARTED ||
                        it == DeviceSessionState.STOPPED ||
                        it == DeviceSessionState.STOPPING
                }
            }
            if (state != DeviceSessionState.STARTED) {
                Log.w("MeowGlasses", 
                    "MWDAT: session not STARTED (got ${state ?: "timeout"}) for " +
                        (targetDevice?.identifier?.take(10) ?: "auto"),
                )
                runCatching { s.stop() }
                session = null
                activeSession = null
                return@withLock false
            }
            Log.i("MeowGlasses", "MWDAT: session STARTED — adding stream…")
            MetaGlassesController.setStatus("session STARTED — adding camera stream…")
            applyStreamForMode(s, manager, MetaGlassesController.captureMode.value)
            // Follow mode changes
            scope.launch {
                MetaGlassesController.captureMode.collect { mode ->
                    if (mode != appliedMode) applyStreamForMode(s, manager, mode)
                }
            }
            true
        } catch (e: Exception) {
            Log.w("MeowGlasses", "MWDAT: attemptStreamOnce threw: ${e.localizedMessage}")
            runCatching { session?.stop() }
            session = null
            activeSession = null
            false
        }
    }

    /**
     * SESSION_ALREADY_STOPPED gotcha (found 2026-07-03): reconnecting shortly
     * after a disconnect can hand back a session from `createSession()` that
     * the SDK still considers stopped internally — addStream() fails even
     * though createSession() itself succeeded.
     *
     * A same-process auto-retry (calling createSession() again immediately)
     * made this WORSE, not better: the retry then failed with "A session
     * already exists for this device" — a different, unhandled exception
     * that crashed the whole app (thrown inside a bare scope.launch with no
     * try/catch). Root-caused via a real device crash log 2026-07-03.
     * Everything below is wrapped so no createSession/addStream failure can
     * ever propagate out and crash the app again — on failure we just log
     * and stop, leaving a clean disconnect()/connect() cycle (user toggling
     * the camera selector) as the actual retry path, since the SDK's own
     * session bookkeeping needs to settle between attempts.
     */
    private suspend fun attemptStream(
        appContext: Context,
        manager: MetaGlassesController,
        targetDevice: DeviceIdentifier? = null,
    ) = connectMutex.withLock {
        // Whole connect sequence is now mutually exclusive process-wide (see
        // connectMutex doc in the companion object) — this is what actually
        // prevents two bridge instances (UI + foreground service) from
        // calling createSession() for the same device at the same time.

        // If another bridge instance already has a HEALTHY session running
        // (e.g. it just won the race for connectMutex and finished while we
        // were waiting), adopt it instead of tearing it down — otherwise the
        // second-arriving instance would kill a perfectly good stream the
        // moment it acquires the lock.
        activeSession?.let { existing ->
            if (existing.state.value == DeviceSessionState.STARTED) {
                Log.i("MeowGlasses", "MWDAT: adopting already-STARTED session from another bridge instance")
                session = existing
                applyStreamForMode(existing, manager, MetaGlassesController.captureMode.value)
                return@withLock
            }
            // Otherwise it's stale (never reached STARTED, or stopped) — clear it.
            Log.i("MeowGlasses", "MWDAT: stopping stale session from a previous connect…")
            runCatching { existing.stop() }
            activeSession = null
            delay(500)
        }

        // Some links report a transient bad MCI state: createSession succeeds,
        // but start() never reaches STARTED on the first try. Auto-retry a few
        // times before giving up.
        for (attempt in 1..3) {
            try {
                // Pin to the specific device that reached CONNECTED when we
                // know it (the multi-pair case); fall back to AutoDeviceSelector
                // only when we couldn't determine one (single-device / no
                // metadata), preserving the original behavior there.
                val selector = targetDevice?.let { SpecificDeviceSelector(it) } ?: AutoDeviceSelector()
                val s = Wearables.createSession(selector).getOrElse { error ->
                    throw IllegalStateException(error.toString())
                }
                session = s
                activeSession = s

                // Collect the session's own error stream — the SDK reports WHY
                // a session stops here (previously unlogged, which is why a
                // failed start looked like a silent "did not reach STARTED").
                scope.launch {
                    runCatching {
                        s.errors.collect { err ->
                            Log.e("MeowGlasses", "MWDAT: session error: $err")
                        }
                    }
                }

                s.start()

                // start() is async (IDLE→STARTING→STARTED). addStream() before
                // STARTED fails with SESSION_ALREADY_STOPPED.
                val state = withTimeoutOrNull(25_000) {
                    s.state.first {
                        it == DeviceSessionState.STARTED ||
                        it == DeviceSessionState.STOPPED ||
                        it == DeviceSessionState.STOPPING
                    }
                }

                if (state != DeviceSessionState.STARTED) {
                    Log.e("MeowGlasses", 
                        "MWDAT: session did not reach STARTED (got ${state ?: "timeout"}) " +
                        "attempt $attempt/3"
                    )
                    runCatching { s.stop() }
                    session = null
                    activeSession = null
                    // Hard reset SDK session bookkeeping before retry. This
                    // recovers stale/bad-MCI startup states seen on real devices.
                    if (attempt < 3) {
                        runCatching { Wearables.reset() }
                        delay(600)
                        Wearables.initialize(appContext)
                    }
                    delay(1500)
                    continue
                }

                Log.i("MeowGlasses", "MWDAT: session STARTED — adding video stream…")
                MetaGlassesController.setStatus("session STARTED — adding camera stream…")

                Wearables.checkPermissionStatus(Permission.CAMERA).fold(
                    onSuccess = { status ->
                        if (status is PermissionStatus.Granted) {
                            Log.i("MeowGlasses", "MWDAT: CAMERA permission granted")
                        } else {
                            Log.e("MeowGlasses", 
                                "MWDAT: CAMERA permission is DENIED for this app — enable " +
                                "camera access for MetaMemory in the Meta AI app (glasses " +
                                "settings → device access / connected apps), then reconnect.",
                            )
                            MetaGlassesController.setStatus("CAMERA denied in Meta AI app")
                        }
                    },
                    onFailure = { error, _ ->
                        Log.w("MeowGlasses", "MWDAT: could not check CAMERA permission: $error")
                    },
                )

                // Prefer a known-good video tier if current photo interval is very slow —
                // stream still works for photo modes; this just helps first light-up.
                val mode = MetaGlassesController.captureMode.value
                applyStreamForMode(s, manager, mode)
                scope.launch {
                    MetaGlassesController.captureMode.collect { m ->
                        if (m != appliedMode) applyStreamForMode(s, manager, m)
                    }
                }
                return@withLock
            } catch (e: Exception) {
                Log.e("MeowGlasses", "MWDAT: stream setup threw (attempt $attempt/3): ${e.localizedMessage}")
                MetaGlassesController.setStatus("stream setup fail $attempt/3: ${e.localizedMessage}")
                runCatching { session?.stop() }
                session = null
                activeSession = null
                if (attempt < 3) {
                    runCatching { Wearables.reset() }
                    delay(600)
                    Wearables.initialize(appContext)
                }
                delay(1500)
            }
        }

        Log.e("MeowGlasses", "MWDAT: giving up after 3 attempts — toggle camera selection or reconnect glasses in Meta AI app")
        MetaGlassesController.setStatus("gave up starting camera — retry Start capture")
    }

    /**
     * (Re)build the video stream for a capture tier. Reconfiguring quality/fps
     * on the MWDAT SDK means removeStream() then addStream() with a new
     * StreamConfiguration — there's no in-place setter. Guarded and
     * serialized so overlapping mode changes can't race.
     */
    private suspend fun applyStreamForMode(
        s: DeviceSession,
        manager: MetaGlassesController,
        mode: CaptureMode,
    ) = streamReconfigMutex.withLock {
        if (mode == appliedMode && currentStream != null) return@withLock

        // Tear down the old stream FULLY before adding the new one. The
        // first version did remove→add back-to-back with no settle time and
        // no recovery: if the new config was rejected (HIGH@15fps on real
        // hardware), the old stream was already gone → dead feed, frozen
        // frame re-ingested forever, and "switch to Action" nuked the video.
        streamJobs.forEach { it.cancel() }
        streamJobs.clear()
        runCatching { currentStream?.stop() }
        currentStream = null
        runCatching { s.removeStream() }
        decoder?.release()
        decoder = null
        delay(400) // let the device tear down before a new configure lands

        // Fallback ladder: requested tier → one retry → LIVE (proven-good).
        // Never leave the feed dead just because a tier was rejected.
        if (tryAddStream(s, manager, mode)) return@withLock
        delay(800)
        if (tryAddStream(s, manager, mode)) return@withLock
        Log.w("MeowGlasses", "MWDAT: ${mode.label} rejected twice — falling back to ${CaptureMode.VIEWING.label}")
        delay(400)
        if (!tryAddStream(s, manager, CaptureMode.VIEWING)) {
            Log.e("MeowGlasses", "MWDAT: could not restore any stream — toggle the camera selection to reconnect.")
        }
    }

    /** Add + start a stream for a tier; wires collectors into [streamJobs].
     *  Returns false (after logging) if the SDK rejects the configuration. */
    private fun tryAddStream(
        s: DeviceSession,
        manager: MetaGlassesController,
        mode: CaptureMode,
    ): Boolean {
        val frameDecoder = MwdatVideoFrameDecoder(onBitmap = manager::pushFrame).also { decoder = it }
        val quality = when (mode.quality) {
            "HIGH" -> VideoQuality.HIGH
            "MEDIUM" -> VideoQuality.MEDIUM
            else -> VideoQuality.LOW
        }
        var ok = false
        s.addStream(StreamConfiguration(videoQuality = quality, frameRate = mode.fps)).fold(
            onSuccess = { stream ->
                currentStream = stream
                appliedMode = mode
                
                var lastFrameHandledAt = 0L
                val throttleMs = mode.uploadIntervalMs

                streamJobs += scope.launch {
                    stream.videoStream.collect { frame ->
                        val now = System.currentTimeMillis()
                        if (throttleMs > 0 && now - lastFrameHandledAt < throttleMs) {
                            return@collect
                        }
                        lastFrameHandledAt = now
                        runCatching { frameDecoder.handle(frame) }
                            .onFailure { Log.w("MeowGlasses", "MWDAT: frame decode error: ${it.localizedMessage}") }
                    }
                }
                streamJobs += scope.launch {
                    stream.errorStream.collect { err -> Log.w("MeowGlasses", "MWDAT stream error: $err") }
                }
                stream.start()
                val kind = if (mode.isPhotoOnly) "photo every ${mode.photoIntervalMs}ms" else "video"
                Log.i("MeowGlasses", 
                    "MWDAT: stream started — ${mode.label} ($kind $quality @${mode.fps}fps)",
                )
                MetaGlassesController.setStatus(
                    "camera ON · ${mode.label} · $quality @${mode.fps}fps",
                )
                ok = true
            },
            onFailure = { error, _ ->
                Log.e("MeowGlasses", "MWDAT: addStream(${mode.label}: $quality@${mode.fps}fps) failed: $error")
                MetaGlassesController.setStatus("addStream failed: $error")
            },
        )
        return ok
    }

    override fun disconnect() {
        streamJobs.forEach { it.cancel() }
        streamJobs.clear()
        runCatching { currentStream?.stop() }
        currentStream = null
        appliedMode = null
        runCatching { session?.stop() }
        // Also stop the process-wide session if another instance made it.
        activeSession?.let { if (it !== session) runCatching { it.stop() } }
        activeSession = null
        session = null
        decoder?.release()
        decoder = null
        // Cancel outstanding connect/wait jobs, then allow new launches
        supervisor.cancel()
        supervisor = SupervisorJob()
        // Avoid Wearables.reset() on every disconnect — it races with a new connect
        // and cancels discovery. Only reset when session bookkeeping is stuck
        // (handled inside attemptStream retries).
        Log.i("MeowGlasses", "MWDAT: session stopped (bridge reusable)")
    }
}
