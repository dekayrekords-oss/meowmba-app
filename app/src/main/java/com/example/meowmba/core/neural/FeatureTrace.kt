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
 * cores on a laptop, and it is nothing a person would mind existing.
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
)

/** Header written once per file so the trainer can check layouts before reading samples. */
@Serializable
data class FeatureTraceHeader(
    val version: Int = 1,
    val inputLayout: List<String>,
    val outputLayout: List<String>,
    val biasFields: List<String>,
    val driveFields: List<String> = listOf("approach", "social", "curiosity", "caution", "energy"),
    val checkpoint: String,
    val startedAtEpochMs: Long,
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
    private var writer: java.io.Writer? = null
    private var currentFile: File? = null
    private var bytesWritten = 0L
    private var pending = 0
    var samplesWritten: Long = 0L
        private set
    var dropped: Long = 0L
        private set

    @Synchronized
    fun record(sample: FeatureTraceSample) {
        try {
            val w = writer ?: open()
            val line = json.encodeToString(sample)
            if (bytesWritten + line.length + 1 > maxBytesPerFile) {
                rotate()
                return record(sample)
            }
            w.write(line)
            w.write("\n")
            bytesWritten += line.length + 1
            samplesWritten++
            if (++pending >= flushEvery) {
                w.flush()
                pending = 0
            }
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
        val file = File(directory, "trace-${System.currentTimeMillis()}$EXTENSION")
        val w = file.bufferedWriter()
        val head = json.encodeToString(header.copy(startedAtEpochMs = System.currentTimeMillis()))
        w.write(head)
        w.write("\n")
        bytesWritten = head.length + 1L
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
