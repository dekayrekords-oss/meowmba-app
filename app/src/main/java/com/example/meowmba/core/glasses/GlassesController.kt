package com.example.meowmba.core.glasses

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.example.meowmba.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Meta Ray-Ban glasses controller (Meta Wearables DAT).
 *
 * CaptureService only *samples* [latestJpeg]. Hardware streaming is started by
 * [ensureCaptureStream] → MWDAT [GlassesBridge.connect] (or mock frames).
 */
class MetaGlassesController(private val context: Context) {

    interface GlassesBridge {
        fun connect(context: Context, manager: MetaGlassesController)
        fun disconnect()
        suspend fun ensureRegistered(activity: Activity, timeoutMs: Long = 90_000): Boolean
        suspend fun ensureCameraPermission(activity: Activity): Boolean
    }

    companion object {
        const val SOURCE = "meta_glasses"
        private const val STALE_FRAME_MS = 12_000L

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _isRealDevice = MutableStateFlow(false)
        val isRealDevice: StateFlow<Boolean> = _isRealDevice.asStateFlow()

        private val _isStreaming = MutableStateFlow(false)
        val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

        private val _status = MutableStateFlow("idle")
        val status: StateFlow<String> = _status.asStateFlow()

        private val _latestJpegShared = MutableStateFlow<ByteArray?>(null)
        val latestJpeg: StateFlow<ByteArray?> = _latestJpegShared.asStateFlow()

        private val _frameEvents = MutableSharedFlow<ByteArray>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val frameEvents: SharedFlow<ByteArray> = _frameEvents.asSharedFlow()

        private val _frameSeq = MutableStateFlow(0L)
        val frameSeq: StateFlow<Long> = _frameSeq.asStateFlow()

        private val _lastFrameAtMs = MutableStateFlow(0L)
        val lastFrameAtMs: StateFlow<Long> = _lastFrameAtMs.asStateFlow()

        private val _captureMode = MutableStateFlow(CaptureMode.DEFAULT_BACKGROUND)
        val captureMode: StateFlow<CaptureMode> = _captureMode.asStateFlow()

        private val _backgroundMode = MutableStateFlow(CaptureMode.DEFAULT_BACKGROUND)
        val backgroundMode: StateFlow<CaptureMode> = _backgroundMode.asStateFlow()

        private val _viewingMode = MutableStateFlow(CaptureMode.VIEWING)
        val viewingMode: StateFlow<CaptureMode> = _viewingMode.asStateFlow()

        private val _tapEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val tapEvents: SharedFlow<Unit> = _tapEvents.asSharedFlow()

        private val _isRecordingVideo = MutableStateFlow(false)
        val isRecordingVideo: StateFlow<Boolean> = _isRecordingVideo.asStateFlow()

        private var viewfinderActive = false

        fun setCaptureMode(mode: CaptureMode) {
            if (mode.isVideo) {
                _viewingMode.value = mode
            } else {
                _backgroundMode.value = mode
            }
            _captureMode.value = mode
            Log.i("MeowGlasses", "Meta glasses capture mode → ${mode.label}")
        }

        fun setBackgroundMode(mode: CaptureMode) {
            _backgroundMode.value = mode
            if (!viewfinderActive && !_isRecordingVideo.value) {
                _captureMode.value = mode
            }
        }

        fun setViewfinderActive(active: Boolean) {
            viewfinderActive = active
            if (!_isRecordingVideo.value) {
                _captureMode.value = if (active) _viewingMode.value else _backgroundMode.value
            }
        }

        fun setRecordingVideo(recording: Boolean) {
            _isRecordingVideo.value = recording
            _captureMode.value = if (recording) {
                CaptureMode.VIDEO_30
            } else if (viewfinderActive) {
                _viewingMode.value
            } else {
                _backgroundMode.value
            }
        }

        fun setStatus(msg: String) {
            _status.value = msg
            Log.i("MeowGlasses", "Glasses: $msg")
        }

        internal fun publishFrame(jpeg: ByteArray) {
            if (jpeg.isEmpty()) return
            _latestJpegShared.value = jpeg
            _frameSeq.value = _frameSeq.value + 1
            _lastFrameAtMs.value = System.currentTimeMillis()
            _isStreaming.value = true
            _isConnected.value = true
            _frameEvents.tryEmit(jpeg)
        }
    }

    val isConnected: StateFlow<Boolean> get() = Companion.isConnected
    val isRealDevice: StateFlow<Boolean> get() = Companion.isRealDevice
    val isStreaming: StateFlow<Boolean> get() = Companion.isStreaming
    val status: StateFlow<String> get() = Companion.status
    val latestJpeg: StateFlow<ByteArray?> get() = Companion.latestJpeg
    val frameEvents: SharedFlow<ByteArray> get() = Companion.frameEvents
    val frameSeq: StateFlow<Long> get() = Companion.frameSeq
    val captureMode: StateFlow<CaptureMode> get() = Companion.captureMode
    val tapEvents: SharedFlow<Unit> get() = Companion.tapEvents
    val isRecording: StateFlow<Boolean> get() = Companion.isRecordingVideo

    private var bridge: GlassesBridge? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mockJob: Job? = null
    private val connecting = AtomicBoolean(false)
    private var lastConnectAt = 0L

    fun pushFrame(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, stream)
        publishFrame(stream.toByteArray())
    }

    fun pushFrameJpeg(jpeg: ByteArray) {
        publishFrame(jpeg)
    }

    private fun publishFrame(jpeg: ByteArray) = Companion.publishFrame(jpeg)

    fun pushTap() {
        Log.i("MeowGlasses", "Meta Glasses: temple tap")
        _tapEvents.tryEmit(Unit)
    }

    fun setCaptureMode(mode: CaptureMode) = Companion.setCaptureMode(mode)

    fun setRecording(on: Boolean) = Companion.setRecordingVideo(on)

    /** True if MWDAT classes were compiled into this APK. */
    fun hasSdk(): Boolean = try {
        BuildConfig.HAS_MWDAT_SDK && Class.forName(
            "com.example.meowmba.core.glasses.MwdatGlassesBridge",
            false,
            javaClass.classLoader,
        ).let { GlassesBridge::class.java.isAssignableFrom(it) }
    } catch (_: Exception) {
        false
    } catch (_: LinkageError) {
        false
    }

    private fun obtainBridge(): GlassesBridge? {
        bridge?.let { return it }
        if (!hasSdk()) return null
        return try {
            val b = Class.forName("com.example.meowmba.core.glasses.MwdatGlassesBridge")
                .getDeclaredConstructor()
                .newInstance() as GlassesBridge
            bridge = b
            b
        } catch (e: Exception) {
            Log.w("MeowGlasses", "MWDAT bridge load failed: ${e.localizedMessage}")
            null
        } catch (e: LinkageError) {
            Log.w("MeowGlasses", "MWDAT dependency load failed", e)
            null
        }
    }

    suspend fun ensureRegistered(activity: Activity): Boolean {
        val b = obtainBridge() ?: run {
            setStatus("Meta wearable support is unavailable in this installation")
            return false
        }
        return try {
            setStatus("registering with Meta AI…")
            val ok = b.ensureRegistered(activity)
            setStatus(if (ok) "registered" else "registration failed / denied")
            ok
        } catch (e: Exception) {
            Log.e("MeowGlasses", "MWDAT: ensureRegistered threw: ${e.localizedMessage}")
            setStatus("registration error: ${e.localizedMessage}")
            false
        }
    }

    suspend fun ensureCameraPermission(activity: Activity): Boolean {
        val b = obtainBridge() ?: return false
        return try {
            setStatus("requesting glasses camera permission…")
            val ok = b.ensureCameraPermission(activity)
            setStatus(if (ok) "camera permission OK" else "camera permission denied in Meta AI")
            ok
        } catch (e: Exception) {
            Log.e("MeowGlasses", "MWDAT: ensureCameraPermission threw: ${e.localizedMessage}")
            setStatus("camera permission error: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Request glasses video session. Safe to call repeatedly.
     * Does **not** mark connected until a frame arrives (or mock starts).
     */
    fun connect(force: Boolean = false) {
        val b = obtainBridge()
        if (b == null) {
            sdkUnavailable()
            return
        }
        _isRealDevice.value = true
        stopMockFrames()

        val now = System.currentTimeMillis()
        if (!force && connecting.get() && now - lastConnectAt < 3_000L) {
            Log.i("MeowGlasses", "MWDAT connect already in flight — skip")
            return
        }
        // If we already have fresh frames, leave the stream alone
        if (!force && _isStreaming.value && now - _lastFrameAtMs.value < STALE_FRAME_MS) {
            Log.i("MeowGlasses", "MWDAT stream healthy (frame ${System.currentTimeMillis() - _lastFrameAtMs.value}ms ago)")
            return
        }

        if (!connecting.compareAndSet(false, true)) {
            Log.i("MeowGlasses", "MWDAT connect race — skip")
            return
        }
        lastConnectAt = now
        try {
            setStatus("connecting MWDAT session…")
            b.connect(context, this)
            // True "linked" only when a real frame arrives, not merely when an
            // asynchronous connect request was accepted.
            Log.i("MeowGlasses", "Meta Glasses: MWDAT bridge connect requested")
        } catch (e: Exception) {
            _isConnected.value = false
            _isStreaming.value = false
            connecting.set(false)
            setStatus("connect blocked: ${e.localizedMessage}")
            Log.w("MeowGlasses", "MWDAT connect deferred (${e.localizedMessage})")
        } finally {
            // Keep "connecting" true long enough for discovery (can take 20–40s)
            scope.launch {
                delay(45_000)
                connecting.set(false)
            }
        }
    }

    /**
     * Tear down + open a new session.
     * Will **not** interrupt an in-flight connect (that cancelled CONNECTED waits
     * and left the camera off).
     */
    fun forceReconnect() {
        if (connecting.get()) {
            Log.i("MeowGlasses", "MWDAT forceReconnect skipped — connect already in flight")
            return
        }
        Log.i("MeowGlasses", "MWDAT forceReconnect")
        stopMockFrames()
        runCatching { bridge?.disconnect() }
        _isConnected.value = false
        _isStreaming.value = false
        connect(force = true)
    }

    /**
     * Ensure a live JPEG source for capture / live call.
     * Reconnects when there is no SDK session producing frames.
     */
    fun ensureCaptureStream() {
        if (!hasSdk()) {
            sdkUnavailable()
            return
        }
        _isRealDevice.value = true
        obtainBridge()
        val age = System.currentTimeMillis() - _lastFrameAtMs.value
        val healthy = _isStreaming.value && _lastFrameAtMs.value > 0 && age < STALE_FRAME_MS
        if (healthy) {
            setStatus("streaming · mode=${_captureMode.value.label} · frame age ${age}ms")
            return
        }
        // If a connect is already waiting for glasses CONNECTED / session STARTED, leave it alone.
        if (connecting.get() || nowConnectingSoft()) {
            Log.i("MeowGlasses", "Capture stream: connect in progress — not interrupting")
            return
        }
        if (_lastFrameAtMs.value > 0 && age >= STALE_FRAME_MS) {
            setStatus("frames stale (${age}ms) — reconnecting")
            forceReconnect()
        } else if (!_isConnected.value || _frameSeq.value == 0L) {
            setStatus("starting stream · mode=${_captureMode.value.label}")
            connect(force = false)
        }
        Log.i("MeowGlasses", 
            "Capture stream requested · mode=${_captureMode.value.label} · " +
                "real=true · frames=${_frameSeq.value} · age=${if (_lastFrameAtMs.value == 0L) "never" else "${age}ms"}",
        )
    }

    private fun nowConnectingSoft(): Boolean =
        System.currentTimeMillis() - lastConnectAt < 40_000L && _frameSeq.value == 0L

    fun disconnect() {
        stopMockFrames()
        connecting.set(false)
        runCatching { bridge?.disconnect() }
        _isConnected.value = false
        _isStreaming.value = false
        _isRealDevice.value = false
        clearFrames()
        setStatus("disconnected")
        Log.i("MeowGlasses", "Meta Glasses: disconnected")
    }

    private fun startMock() {
        _isConnected.value = true
        _isRealDevice.value = false
        setStatus("mock mode")
        Log.i("MeowGlasses", "Meta Glasses: mock mode (add github_token for real DAT)")
    }

    private fun clearFrames() {
        _latestJpegShared.value = null
        _lastFrameAtMs.value = 0L
        _frameSeq.value = 0L
    }

    /** Missing hardware support must never fabricate a successful photo. */
    private fun sdkUnavailable() {
        stopMockFrames()
        connecting.set(false)
        _isConnected.value = false
        _isStreaming.value = false
        _isRealDevice.value = false
        clearFrames()
        setStatus("Glasses camera support is not included in this installation. Update to a glasses-enabled build.")
    }

    fun startMockFrames() {
        if (mockJob?.isActive == true) return
        startMock()
        mockJob = scope.launch {
            Log.i("MeowGlasses", "Mock frames: producing JPEGs")
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            var n = 0
            while (isActive) {
                val mode = _captureMode.value
                val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(Color.rgb(20 + (n % 40), 28, 40))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(232, 162, 58)
                    textSize = 36f
                }
                c.drawText("MetaMemory mock", 40f, 80f, paint)
                paint.textSize = 28f
                paint.color = Color.LTGRAY
                c.drawText(fmt.format(Date()), 40f, 130f, paint)
                c.drawText(mode.label, 40f, 180f, paint)
                c.drawText("#$n", 40f, 230f, paint)
                pushFrame(bmp)
                bmp.recycle()
                n++
                val wait = mode.photoIntervalMs.takeIf { it > 0 }
                    ?: mode.ingestIntervalMs.coerceAtLeast(1_000L)
                delay(wait.coerceIn(500L, 60_000L))
            }
        }
    }

    fun stopMockFrames() {
        mockJob?.cancel()
        mockJob = null
    }
}
