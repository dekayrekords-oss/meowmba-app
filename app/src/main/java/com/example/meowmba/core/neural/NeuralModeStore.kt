package com.example.meowmba.core.neural

import com.example.meowmba.core.security.SecureStorageManager
import java.io.File

/**
 * Persists the user's neural-layer choices and loads the checkpoint the app
 * should run. Small on purpose: three preferences and one file lookup.
 *
 * A checkpoint placed at `filesDir/neural/core.json` (for example exported by
 * the offline trainer and pushed with adb) takes precedence over the built-in
 * default, but only if it validates; an unreadable file is reported and the
 * default is used, so a bad training run cannot brick the character.
 */
class NeuralModeStore(
    private val storage: SecureStorageManager,
    private val filesDir: File,
) {
    fun mode(): NeuralMode =
        storage.getPreference(KEY_MODE)?.let { runCatching { NeuralMode.valueOf(it) }.getOrNull() } ?: NeuralMode.SHADOW

    fun setMode(mode: NeuralMode) = storage.savePreference(KEY_MODE, mode.name)

    fun substrate(): NeuralSubstrate =
        storage.getPreference(KEY_SUBSTRATE)?.let { runCatching { NeuralSubstrate.valueOf(it) }.getOrNull() }
            ?: NeuralSubstrate.RATE

    fun setSubstrate(substrate: NeuralSubstrate) = storage.savePreference(KEY_SUBSTRATE, substrate.name)

    fun traceConsent(): Boolean = storage.getBooleanPreference(KEY_TRACE_CONSENT, false)

    fun setTraceConsent(enabled: Boolean) = storage.saveBooleanPreference(KEY_TRACE_CONSENT, enabled)

    val neuralDir: File get() = File(filesDir, DIR)
    val traceDir: File get() = File(neuralDir, "traces")
    val customCheckpointFile: File get() = File(neuralDir, CHECKPOINT_FILE)

    /** The checkpoint to run for [substrate], and a note about where it came from. */
    fun loadCheckpoint(substrate: NeuralSubstrate): Pair<NeuralCheckpoint, String> {
        val custom = customCheckpointFile
        if (custom.isFile) {
            try {
                val ckpt = NeuralCheckpoint.fromJson(custom.readText())
                if (ckpt.substrate == substrate) return ckpt to "custom:${ckpt.name}"
                return default(substrate) to "default (custom file is for ${ckpt.substrate.name})"
            } catch (e: IllegalArgumentException) {
                return default(substrate) to "default (custom file rejected: ${e.message?.take(120)})"
            } catch (e: java.io.IOException) {
                return default(substrate) to "default (custom file unreadable)"
            }
        }
        return default(substrate) to "default"
    }

    fun coreFactory(): (NeuralSubstrate) -> NeuralCore = { substrate ->
        val (ckpt, _) = loadCheckpoint(substrate)
        when (substrate) {
            NeuralSubstrate.RATE -> LeakyRecurrentCore(ckpt)
            NeuralSubstrate.SPIKING -> SpikingCore(ckpt)
            NeuralSubstrate.BANC_EXPERIMENTAL -> BancExperimentalCore()
            NeuralSubstrate.MALECNS_REMOTE -> MaleCnsRemoteCore()
        }
    }

    private fun default(substrate: NeuralSubstrate): NeuralCheckpoint = when (substrate) {
        NeuralSubstrate.RATE -> NeuralCheckpoint.default()
        NeuralSubstrate.SPIKING -> NeuralCheckpoint.defaultSpiking()
        NeuralSubstrate.BANC_EXPERIMENTAL -> BancExperimentalCheckpoint.value
        NeuralSubstrate.MALECNS_REMOTE -> BancExperimentalCheckpoint.value.copy(
            name = "malecns-v1-retained-remote", substrate = NeuralSubstrate.MALECNS_REMOTE,
            notes = "Metadata only. Remote MaleCNS state is not a locally replayable checkpoint.")
    }

    companion object {
        const val KEY_MODE = "neural_mode"
        const val KEY_SUBSTRATE = "neural_substrate"
        const val KEY_TRACE_CONSENT = "neural_trace_consent"
        const val DIR = "neural"
        const val CHECKPOINT_FILE = "core.json"
    }
}
