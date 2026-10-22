package com.example.meowmba.core.flow.laya

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.meowmba.core.flow.decide.Answer
import com.example.meowmba.core.flow.decide.DecisionEngine
import com.example.meowmba.core.flow.decide.Question
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.LongBuffer

/**
 * Laya (ModernBERT-large, weight-only int8 ONNX) on this phone. No server (owner D8).
 *
 * Loads on the first question and unloads after [IDLE_UNLOAD_MS] without questions, so the
 * ~0.6 GB of weights are resident only while Flow is deciding. Abstains (returns null →
 * rules fallback) when the files are missing, the phone is even slightly warm (thermal LIGHT),
 * or a question does not fit.
 */
class LayaEngine(
    private val files: LayaFiles,
    private val scope: CoroutineScope,
    private val thermalStatus: () -> Int,
    /** Two threads: half the peak power of four. Measured on SM-F971U: ~1.05 s/question at 4 threads,
     *  and 26 back-to-back questions took the skin from 33 °C to 40 °C, so Laya is kept sparse. */
    private val threads: Int = 2,
) : DecisionEngine {
    override val id = "laya"

    private class Loaded(val env: OrtEnvironment, val encoder: OrtSession, val head: OrtSession,
                         val tok: LayaTokenizer, val cfg: Config) {
        fun close() { runCatching { encoder.close() }; runCatching { head.close() } }
    }

    class Config(val maxLen: Int, val headMaxLen: Int, val temperature: DoubleArray, val byOptions: Map<String, Double>) {
        fun temperatureFor(type: LayaSequence.QType, k: Int): Double =
            byOptions[LayaSequence.tempBucket(type, k)] ?: temperature[type.index]

        companion object {
            fun parse(json: String): Config {
                val o = Json.parseToJsonElement(json).jsonObject
                return Config(
                    o.getValue("max_len").jsonPrimitive.double.toInt(),
                    o.getValue("head_max_len").jsonPrimitive.double.toInt(),
                    o.getValue("temperature").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray(),
                    o.getValue("temperature_by_options").jsonObject.mapValues { it.value.jsonPrimitive.double },
                )
            }
        }
    }

    private val lock = Mutex()
    private var loaded: Loaded? = null
    private var unloadJob: Job? = null

    /** Last forward pass in ms, for the Adjust sheet and heat review. */
    @Volatile var lastPassMs: Long = 0L
        private set

    override suspend fun available(): Boolean =
        files.present() && thermalStatus() < PowerManager.THERMAL_STATUS_LIGHT

    override suspend fun ask(stateJson: String, questions: List<Question>): List<Answer>? {
        if (!available()) return null
        return withContext(Dispatchers.Default) {
            lock.withLock {
                val m = loaded ?: load() ?: return@withLock null
                scheduleUnload()
                // Laya was trained on Python's json.dumps spacing; re-serialise the app's compact JSON.
                val state = runCatching { LayaSequence.pyJsonDumps(Json.parseToJsonElement(stateJson)) }.getOrDefault(stateJson)
                val out = ArrayList<Answer>(questions.size)
                for (q in questions) {
                    if (thermalStatus() >= PowerManager.THERMAL_STATUS_LIGHT) return@withLock null
                    out += answer(m, state, q) ?: return@withLock null
                }
                out
            }
        }
    }

    private fun answer(m: Loaded, state: String, question: Question): Answer? {
        val q = when (question) {
            is Question.Noul -> LayaSequence.Q(LayaSequence.QType.NOUL, question.instructions)
            is Question.Choice -> LayaSequence.Q(LayaSequence.QType.CHOICE, question.instructions, question.criteria)
        }
        val k = LayaSequence.renderOptions(q).size
        val built = LayaSequence.build(m.tok, state, q, m.cfg.maxLen, m.cfg.headMaxLen)
        if (built.markers.size != k) return null // options do not fit: abstain rather than guess
        val probs = run(m, built, q.type, k)
        return when (question) {
            is Question.Noul -> Answer(question.id, if (probs[1] >= 0.5) "yes" else "no", probs[1])
            is Question.Choice -> {
                val keys = question.criteria.keys.toList()
                val best = probs.indices.maxBy { probs[it] }
                Answer(question.id, keys[best], probs[best], keys.indices.associate { keys[it] to probs[it] })
            }
        }
    }

    /** Encoder then head, exactly as the reference session; returns temperature-scaled probabilities. */
    private fun run(m: Loaded, built: LayaSequence.Built, type: LayaSequence.QType, k: Int): DoubleArray {
        val t0 = SystemClock.elapsedRealtime()
        val len = built.ids.size.toLong()
        val env = m.env
        OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(built.ids.size) { built.ids[it].toLong() }), longArrayOf(1, len)).use { ids ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(built.ids.size) { 1L }), longArrayOf(1, len)).use { att ->
                m.encoder.run(mapOf("input_ids" to ids, "attention_mask" to att)).use { enc ->
                    val hidden = enc.get("hidden").get() as OnnxTensor
                    OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(built.markers.size) { built.markers[it].toLong() }),
                        longArrayOf(1, built.markers.size.toLong())).use { pos ->
                        OnnxTensor.createTensor(env, arrayOf(BooleanArray(built.markers.size) { true })).use { mask ->
                            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(type.index.toLong())), longArrayOf(1)).use { qt ->
                                m.head.run(mapOf("hidden" to hidden, "attention_mask" to att, "marker_pos" to pos,
                                    "marker_mask" to mask, "qtype" to qt)).use { r ->
                                    val fb = (r.get("logits").get() as OnnxTensor).floatBuffer
                                    val logits = FloatArray(k) { fb.get(it) }
                                    lastPassMs = SystemClock.elapsedRealtime() - t0
                                    return LayaSequence.softmax(logits, m.cfg.temperatureFor(type, k))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Result of running the reference golden cases on this phone. */
    data class Check(val agree: Int, val total: Int, val maxDelta: Double, val avgMs: Long, val loadMs: Long)

    /**
     * Runs the reference port's golden cases (assets/laya/parity.json) through this phone's
     * runtime: argmax agreement, worst probability shift, mean pass time. User-started only.
     */
    suspend fun check(parityJson: String): Check? = withContext(Dispatchers.Default) {
        lock.withLock {
            val t0 = SystemClock.elapsedRealtime()
            val m = loaded ?: load() ?: return@withLock null
            val loadMs = SystemClock.elapsedRealtime() - t0
            scheduleUnload()
            val root = Json.parseToJsonElement(parityJson).jsonObject
            var agree = 0; var total = 0; var maxDelta = 0.0; var ms = 0L
            for (case in root.getValue("cases").jsonArray.map { it.jsonObject }) {
                val stEl = case.getValue("state")
                val state = if (stEl is kotlinx.serialization.json.JsonPrimitive && stEl.isString) stEl.content else LayaSequence.pyJsonDumps(stEl)
                val expect = case.getValue("expect").jsonObject
                for ((qid, qdef) in case.getValue("questions").jsonObject) {
                    val exp = expect[qid]?.jsonObject ?: continue
                    val want = exp["probs"]?.jsonArray?.map { it.jsonPrimitive.double } ?: continue
                    val q = LayaSequence.fromWire(qdef.jsonObject)
                    val built = LayaSequence.build(m.tok, state, q, m.cfg.maxLen, m.cfg.headMaxLen)
                    if (built.markers.size != want.size) continue
                    val got = run(m, built, q.type, want.size)
                    ms += lastPassMs; total++
                    if (got.indices.maxBy { got[it] } == want.indices.maxBy { want[it] }) agree++
                    maxDelta = maxOf(maxDelta, got.indices.maxOf { kotlin.math.abs(got[it] - want[it]) })
                }
            }
            Check(agree, total, maxDelta, if (total > 0) ms / total else 0, loadMs).also { Log.i(TAG, "Laya check: $it") }
        }
    }

    private fun load(): Loaded? = runCatching {
        val t0 = SystemClock.elapsedRealtime()
        val env = OrtEnvironment.getEnvironment()
        fun opts() = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        // Loading by path lets ORT find (and map) the external .onnx.data next to each graph.
        val enc = env.createSession(files.encoder.absolutePath, opts())
        val head = env.createSession(files.head.absolutePath, opts())
        Loaded(env, enc, head, LayaTokenizer.load(files.tokenizer), Config.parse(files.config.readText())).also {
            loaded = it
            Log.i(TAG, "Laya loaded in ${SystemClock.elapsedRealtime() - t0} ms")
        }
    }.onFailure { Log.w(TAG, "Laya failed to load; rules stay in charge", it) }.getOrNull()

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            lock.withLock { loaded?.close(); loaded = null }
            Log.i(TAG, "Laya unloaded after idle")
        }
    }

    companion object {
        private const val TAG = "LayaEngine"
        const val IDLE_UNLOAD_MS = 10 * 60_000L
    }
}

/** Where Laya's files live: app-specific external storage, reachable by the in-app download and adb. */
class LayaFiles(val dir: File) {
    val encoder get() = File(dir, "encoder_q8.onnx")
    val head get() = File(dir, "head_q8.onnx")
    val tokenizer get() = File(dir, "tokenizer.json")
    val config get() = File(dir, "rl_agent_config.json")

    /** Cheap presence check: every file there at its published size. Hashes are checked after download. */
    fun present(): Boolean = MANIFEST.all { (name, size, _) -> File(dir, name).let { it.isFile && it.length() == size } }

    data class Entry(val name: String, val size: Long, val sha256: String)

    companion object {
        const val SOURCE = "https://huggingface.co/nvkudva/laya-web-q8/resolve/main/v1/"
        /** nvkudva/laya-web-q8 v1 (Apache-2.0), verified against the Hub's LFS hashes 2026-09-23. */
        val MANIFEST = listOf(
            Entry("encoder_q8.onnx", 2_833_670, "5267b578a536bf12808ec84c1f354e87efd150d3c38d44099ef3dd29c5f43124"),
            Entry("encoder_q8.onnx.data", 467_980_288, "6375a3ae8f3ebfa4ca196e813fc7bdaf2e8ad8cf709218ef844ae8814dfffc59"),
            Entry("head_q8.onnx", 205_535, "e86c14c89a18b14f6115a7ccbfd039fce7ff97178fa0c07c14af936690a1f688"),
            Entry("head_q8.onnx.data", 53_076_992, "cfdf7c199378b07758c69f58935871cf517c97c29914328d4b11fcfbe50b7f8b"),
            Entry("tokenizer.json", 3_583_228, "6c8aaa9a542084f2457eab775d4eeb51f92a70c0fd9de28d5edb0ddec3c08d30"),
            Entry("rl_agent_config.json", 745, "ae287b56bbcf5f8c4f4541ae9dfd00c914c4c48b940b8398c3058af37ba92bbd"),
        )
        val TOTAL_BYTES = MANIFEST.sumOf { it.size }

        fun forContext(context: android.content.Context) =
            LayaFiles(File(context.getExternalFilesDir(null) ?: context.filesDir, "models/laya/v1"))
    }
}
