package com.example.meowmba.core.neural

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One controller tick, as recorded for offline analysis and training.
 *
 * Only *features* are stored: the encoded input vector, both controllers'
 * outputs and the legacy drives. Never audio, never video, never transcripts.
 * That is enough to fit a readout offline and to replay a session through the
 * cores on a laptop. These behavioral features still require recording consent.
 */
@Serializable
data class FeatureTraceSample(
    val tMs: Long,
    val mode: String,
    val input: FloatArray,
    val legacyBias: FloatArray,
    val neuralReadout: FloatArray,
    val neuralBias: FloatArray? = null,
    val legacyDrives: FloatArray,
    /** Actual core integration interval; null means an older, timing-ambiguous trace. */
    val dtMs: Float? = null,
    val stepIndex: Long? = null,
    val resetGeneration: Long? = null,
)

/** Header written once per file so the trainer can check layouts before reading samples. */
@Serializable
data class FeatureTraceHeader(
    val version: Int = 2,
    val inputLayout: List<String>,
    val outputLayout: List<String>,
    val biasFields: List<String>,
    val driveFields: List<String> = listOf("approach", "social", "curiosity", "caution", "energy"),
    val checkpoint: String,
    val startedAtEpochMs: Long,
    /** Only the first segment captured atomically with a core reset can assert this. */
    val startsFromReset: Boolean = false,
    val checkpointJson: String? = null,
)

/**
 * Append-only JSONL writer with a hard size cap and a retention cap on the
 * number of files kept. Recording is off unless the user opted in through the
 * Settings section; the controller simply has no trace sink otherwise.
 *
 * The first line of every file is a [FeatureTraceHeader]; every later line is
 * one [FeatureTraceSample]. Files are named by start time so they sort.
 */
class FeatureTraceRecorder(
    private val directory: File,
    private val header: FeatureTraceHeader,
    private val maxBytesPerFile: Long = 8L * 1024 * 1024,
    private val maxFiles: Int = 6,
    private val flushEvery: Int = 25,
) : AutoCloseable {
    private val json = Json { encodeDefaults = true }
    private val headerBytes = maxOf(json.encodeToString(header).toByteArray(Charsets.UTF_8).size,
        json.encodeToString(header.copy(startsFromReset = false)).toByteArray(Charsets.UTF_8).size) + 1L
    private var writer: java.io.Writer? = null
    private var currentFile: File? = null
    private var bytesWritten = 0L
    private var pending = 0
    private var segment = 0L
    var samplesWritten: Long = 0L
        private set
    var dropped: Long = 0L
        private set

    @Synchronized
    fun record(sample: FeatureTraceSample) {
        try {
            val line = json.encodeToString(sample)
            val lineBytes = line.toByteArray(Charsets.UTF_8).size + 1L
            // Never recursively rotate a sample that cannot fit even an empty file.
            if (lineBytes + headerBytes > maxBytesPerFile) { dropped++; return }
            var w = writer ?: open()
            if (bytesWritten + lineBytes > maxBytesPerFile) {
                rotate()
                w = writer ?: open()
            }
            w.write(line)
            w.write("\n")
            bytesWritten += lineBytes
            samplesWritten++
            if (++pending >= flushEvery) {
                w.flush()
                pending = 0
            }
        } catch (_: IllegalArgumentException) {
            dropped++
        } catch (_: java.io.IOException) {
            dropped++
        }
    }

    @Synchronized
    fun currentFile(): File? = currentFile

    /** All trace files, oldest first. */
    fun files(): List<File> =
        directory.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION) }?.sortedBy { it.name } ?: emptyList()

    @Synchronized
    override fun close() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: java.io.IOException) {
        }
        writer = null
    }

    private fun open(): java.io.Writer {
        directory.mkdirs()
        val file = File.createTempFile("trace-${System.currentTimeMillis()}-", EXTENSION, directory)
        val w = file.bufferedWriter()
        val head = json.encodeToString(header.copy(startsFromReset = header.startsFromReset && segment == 0L))
        w.write(head)
        w.write("\n")
        segment++
        bytesWritten = head.toByteArray(Charsets.UTF_8).size + 1L
        writer = w
        currentFile = file
        pending = 0
        prune()
        return w
    }

    private fun rotate() {
        close()
        open()
    }

    private fun prune() {
        val all = files()
        if (all.size <= maxFiles) return
        for (f in all.take(all.size - maxFiles)) {
            if (f != currentFile) f.delete()
        }
    }

    companion object {
        const val EXTENSION = ".jsonl"

        fun headerFor(checkpoint: NeuralCheckpoint): FeatureTraceHeader = FeatureTraceHeader(
            inputLayout = checkpoint.inputLayout,
            outputLayout = checkpoint.outputLayout,
            biasFields = DriveBias.FIELD_NAMES,
            checkpoint = checkpoint.name,
            startedAtEpochMs = 0L,
        )

        /** Parse a trace file; the header is validated against [expectedInput] when given. */
        fun read(file: File, expectedInput: FeatureLayout? = null): Pair<FeatureTraceHeader, List<FeatureTraceSample>> {
            val json = Json { ignoreUnknownKeys = true }
            val lines = file.readLines().filter { it.isNotBlank() }
            require(lines.isNotEmpty()) { "empty trace ${file.name}" }
            val header = json.decodeFromString<FeatureTraceHeader>(lines.first())
            if (expectedInput != null) {
                require(header.inputLayout == expectedInput.names) { "trace ${file.name} has a different input layout" }
            }
            val samples = lines.drop(1).map { json.decodeFromString<FeatureTraceSample>(it) }
            return header to samples
        }
    }
}
