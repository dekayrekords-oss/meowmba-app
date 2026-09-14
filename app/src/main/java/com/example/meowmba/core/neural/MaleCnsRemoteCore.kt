package com.example.meowmba.core.neural

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.math.max

/** Immutable, audited graph identities. A remote response cannot select an arbitrary asset. */
enum class MaleCnsGraphProfile(val wireName: String, val protocol: Long, val asset: String,
    val pairs: Long, val synapses: Long) {
    RETAINED("retained", 1, "e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3", 6_287_749, 90_296_905),
    FULL("full", 2, "8860e68b59e454d8d4e8bd601dd7a3cb9049d18516f1ea8df34eabe1ae400d08", 25_862_574, 125_024_863),
}

data class MaleCnsRequest(val session: String, val seq: Long, val food: Float, val touch: Float,
    val loomLeft: Float, val loomRight: Float, val motionLeft: Float, val motionRight: Float,
    val graph: MaleCnsGraphProfile = MaleCnsGraphProfile.RETAINED) {
    fun parameters(): Map<String, Any?> = mapOf("session" to session, "seq" to seq,
        "food" to JsonPrimitive(food), "touch" to JsonPrimitive(touch),
        "loom_left" to JsonPrimitive(loomLeft), "loom_right" to JsonPrimitive(loomRight),
        "motion_left" to JsonPrimitive(motionLeft), "motion_right" to JsonPrimitive(motionRight)) +
        if (graph == MaleCnsGraphProfile.FULL) mapOf("graph" to graph.wireName) else emptyMap()
}

data class MaleCnsSnapshot(val simMs: Float, val computeMs: Float, val active: Int, val silent: Int,
    val spikes: Int, val meanHz: Float, val maxHz: Float, val feedHz: Float, val escapeHz: Float,
    val jumpHz: Float, val groomHz: Float, val wingHz: Float, val leftHz: Float, val rightHz: Float,
    val graph: MaleCnsGraphProfile = MaleCnsGraphProfile.RETAINED,
    val groom2Hz: Float? = null, val forwardHz: Float? = null, val backwardHz: Float? = null,
    val haltHz: Float? = null, val landingHz: Float? = null, val takeoffHz: Float? = null) {
    companion object {
        const val ASSET = "e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3"
        fun parse(text: String, request: MaleCnsRequest): MaleCnsSnapshot {
            require(text.length <= 4096)
            val o = Json.parseToJsonElement(text).jsonObject
            fun integer(k: String) = o.getValue(k).jsonPrimitive.long
            fun number(k: String, limit: Float = 100_000f): Float =
                o.getValue(k).jsonPrimitive.float.also { require(it.isFinite() && it in 0f..limit) { "Invalid neural measurement" } }
            val profile = request.graph
            require(integer("protocol") == profile.protocol && integer("seq") == request.seq)
            val reportedGraph = o["graph"]?.jsonPrimitive?.content
            require(reportedGraph == profile.wireName || (reportedGraph == null && profile == MaleCnsGraphProfile.RETAINED))
            require(o.getValue("session").jsonPrimitive.content == request.session)
            require(o.getValue("asset_sha256").jsonPrimitive.content == profile.asset)
            require(integer("neurons") == 176422L && integer("pairs") == profile.pairs && integer("synapses") == profile.synapses)
            fun motor(k: String): Float? = when {
                k in o -> number(k, 2000f)
                profile == MaleCnsGraphProfile.FULL -> error("Missing measured motor population")
                else -> null
            }
            fun count(k: String, limit: Long) = integer(k).also { require(it in 0..limit) }.toInt()
            return MaleCnsSnapshot(number("sim_ms", 1e12f), number("compute_ms", 8000f),
                count("active", 176422), count("silent", 176422), count("spikes", 17_642_200),
                number("mean_hz", 2000f), number("max_hz", 2000f), number("feed_hz", 2000f),
                number("escape_hz", 2000f), number("jump_hz", 2000f), number("groom_hz", 2000f),
                number("wing_hz", 2000f), number("left_hz", 2000f), number("right_hz", 2000f), profile,
                motor("groom2_hz"), motor("forward_hz"), motor("backward_hz"),
                motor("halt_hz"), motor("landing_hz"), motor("takeoff_hz"))
        }
    }
}

interface MaleCnsTransport {
    suspend fun step(request: MaleCnsRequest): Result<MaleCnsSnapshot>
    suspend fun stop(session: String)
}

/** Only small snapshots live on Android. Network I/O never runs in step/readout. */
class MaleCnsRemoteCore(
    private val scope: CoroutineScope? = null,
    private val transport: MaleCnsTransport? = null,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val graph: MaleCnsGraphProfile = MaleCnsGraphProfile.RETAINED,
) : NeuralCore {
    override val substrate = NeuralSubstrate.MALECNS_REMOTE
    override val inputLayout = NeuralFeatures.INPUT
    override val outputLayout = NeuralFeatures.OUTPUT
    private var session = UUID.randomUUID().toString()
    private var sequence = 0L
    private var pending: Job? = null
    private var nextAttempt = 0L
    private var sample: MaleCnsSnapshot? = null
    private var sampleInputMs = Long.MIN_VALUE
    private var foodUntil = 0L
    private var status = "Waiting for the paired computer and Meow"

    @Synchronized fun noteFood() { foodUntil = nowMs() + 2000L }
    @Synchronized override fun ready(): Boolean = sample != null && nowMs() - sampleInputMs in 0L..1800L

    @Synchronized override fun step(input: FloatArray, dtMs: Float) {
        require(input.size == inputLayout.size)
        if (scope == null || transport == null || !dtMs.isFinite() || dtMs <= 0f) return
        val now = nowMs()
        if (pending?.isActive == true || now < nextAttempt) return
        fun value(key: String): Float = input[inputLayout.index(key)].takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        val loom = max(value(NeuralFeatures.LOOMING), max(value(NeuralFeatures.THREAT), value(NeuralFeatures.LOUD_SOUND))).coerceAtLeast(0f)
        val motion = max(value(NeuralFeatures.VISUAL_MOTION), value(NeuralFeatures.NOVELTY)).coerceAtLeast(0f)
        val x = value(NeuralFeatures.FACE_OFFSET_X)
        val request = MaleCnsRequest(session, ++sequence, if (now < foodUntil) 1f else 0f,
            value(NeuralFeatures.TOUCH).coerceAtLeast(0f), loom, loom, motion * (1f - x) / 2f, motion * (1f + x) / 2f, graph)
        // Full graph p95 compute budget is 300ms; request at most every 400ms.
        nextAttempt = now + if (graph == MaleCnsGraphProfile.FULL) 400L else 200L
        pending = scope.launch(dispatcher) {
            val result = try { transport.step(request) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Result.failure(IllegalStateException("Meow unavailable")) }
            synchronized(this@MaleCnsRemoteCore) {
                if (request.session != session) return@synchronized
                val measured = result.getOrNull()
                if (measured != null && measured.graph == graph && nowMs() - now in 0L..1800L) {
                    sample = measured; sampleInputMs = now
                    status = "Connected · Meow through your paired computer"
                } else {
                    sample = null
                    status = if (measured != null) "Reply expired · using Classic while reconnecting"
                        else "Meow unavailable · check the paired computer connection"
                    nextAttempt = nowMs() + 2500L
                }
            }
        }
    }

    @Synchronized override fun readout(): FloatArray {
        val y = FloatArray(outputLayout.size)
        val s = sample?.takeIf { ready() } ?: return y
        val feed = (s.feedHz / 100f).coerceIn(0f, 1f)
        val escape = (s.escapeHz / 300f).coerceIn(0f, 1f)
        val groom = (s.groomHz / 200f).coerceIn(0f, 1f)
        val turn = ((s.rightHz - s.leftHz) / 80f).coerceIn(-1f, 1f)
        fun put(k: String, v: Float) { y[outputLayout.index(k)] = v.coerceIn(-1f, 1f) }
        // Authored, bounded retargeting of measured motor activity to the existing avatar contract.
        put(NeuralFeatures.OUT_APPROACH, .5f + .2f * feed - .4f * escape)
        put(NeuralFeatures.OUT_SOCIAL, .5f + .2f * feed)
        put(NeuralFeatures.OUT_CURIOSITY, .2f + .6f * groom)
        put(NeuralFeatures.OUT_CAUTION, .1f + .8f * escape)
        put(NeuralFeatures.OUT_ENERGY, .8f)
        put(NeuralFeatures.OUT_VALENCE, .4f * feed - .3f * escape)
        put(NeuralFeatures.OUT_ORIENT_X, turn)
        put(NeuralFeatures.OUT_ATTEND_USER, feed)
        put(NeuralFeatures.OUT_ATTEND_MOTION, max(escape, kotlin.math.abs(turn)))
        put(NeuralFeatures.OUT_STARTLE, escape)
        put(NeuralFeatures.OUT_INSPECT, groom * (1f - escape))
        return y
    }

    /** Actual population rates, with authored gains and no autonomous robot output. */
    @Synchronized fun flyMotorNow(): FlyMotorIntent {
        val s = sample?.takeIf { ready() } ?: return FlyMotorIntent.NONE
        // Old workers have no body readouts: missing measurements never mean zero.
        if (s.forwardHz == null || s.backwardHz == null || s.haltHz == null ||
            s.landingHz == null || s.takeoffHz == null || s.groom2Hz == null) return FlyMotorIntent.NONE
        return FlyMotorIntent(true, (s.rightHz - s.leftHz) / 80f,
            s.forwardHz / 100f, s.backwardHz / 100f, s.haltHz / 100f,
            s.landingHz / 100f, s.takeoffHz / 100f, s.jumpHz / 100f,
            s.escapeHz / 300f, max(s.groomHz, s.groom2Hz) / 200f).bounded()
    }

    @Synchronized override fun pause() {
        val old = session
        val hadWork = sequence > 0
        session = UUID.randomUUID().toString(); sequence = 0
        pending?.cancel(); pending = null
        sample = null; foodUntil = 0L; nextAttempt = 0L
        status = "Paused · resumes when the avatar is visible"
        if (hadWork && transport != null) scope?.launch(dispatcher) { runCatching { transport.stop(old) } }
    }
    override fun reset() = pause()

    @Synchronized override fun diagnostics(): CoreDiagnostics {
        val s = sample?.takeIf { ready() }
        return CoreDiagnostics(substrate, 176422, ((s?.meanHz ?: 0f) / 100f).coerceIn(0f, 1f),
            ((s?.maxHz ?: 0f) / 100f).coerceIn(0f, 1f), 0, s?.silent ?: 176422, if (s == null) 0 else 100, 0,
            extra = mapOf("malecns.full_graph" to if (graph == MaleCnsGraphProfile.FULL) 1f else 0f,
                "malecns.connected" to if (s == null) 0f else 1f,
                "malecns.wing_power" to (max(s?.wingHz ?: 0f, (s?.escapeHz ?: 0f) / 3f) / 100f).coerceIn(0f, 1f),
                "malecns.feed" to ((s?.feedHz ?: 0f) / 100f).coerceIn(0f, 1f),
                "malecns.groom" to ((s?.groomHz ?: 0f) / 200f).coerceIn(0f, 1f),
                "malecns.feed_hz" to (s?.feedHz ?: 0f), "malecns.escape_hz" to (s?.escapeHz ?: 0f),
                "malecns.groom_hz" to (s?.groomHz ?: 0f), "malecns.wing_hz" to (s?.wingHz ?: 0f),
                "malecns.mean_hz" to (s?.meanHz ?: 0f), "malecns.sim_ms" to (s?.simMs ?: 0f),
                "malecns.compute_ms" to (s?.computeMs ?: 0f)) +
                listOf("groom2" to s?.groom2Hz, "forward" to s?.forwardHz, "backward" to s?.backwardHz,
                    "halt" to s?.haltHz, "landing" to s?.landingHz, "takeoff" to s?.takeoffHz)
                    .mapNotNull { (key, value) -> value?.let { "malecns.${key}_hz" to it } }.toMap(),
            status = if (s == null && sample != null) "Connection stale · using Classic" else status)
    }
}
