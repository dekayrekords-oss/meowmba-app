package com.example.meowmba.ui.companion

import com.example.meowmba.core.neural.FallbackReason
import com.example.meowmba.core.neural.NeuralDiagnostics
import com.example.meowmba.core.neural.NeuralMode
import com.example.meowmba.core.neural.NeuralSubstrate
import java.util.Locale
import kotlin.math.roundToInt

internal data class BrainActivityMetric(
    val id: String,
    val label: String,
    /** Null is unavailable; a measured zero must remain zero. */
    val value: Float?,
    val scale: Float = 1f,
    val unit: String = "%",
) {
    val fraction: Float? get() = value?.let { (it / scale).coerceIn(0f, 1f) }
    val displayValue: String get() = when {
        value == null -> "—"
        unit == "%" -> "${(value * 100f).roundToInt()}%"
        value % 1f == 0f -> "${value.toInt()} $unit"
        else -> "${String.format(Locale.US, "%.1f", value)} $unit"
    }
}

/** Presentation only: opening the monitor must never enable a network or invent activity. */
internal data class NeuralInfoPresentation(
    val title: String,
    val explanation: String,
    val network: String,
    val measurementsAvailable: Boolean,
    val interactionEnabled: Boolean,
    val showLocalAction: Boolean,
    val showPairAction: Boolean,
    val metrics: List<BrainActivityMetric>,
)

internal fun neuralInfoPresentation(d: NeuralDiagnostics?, desktopPaired: Boolean): NeuralInfoPresentation {
    val remote = d?.substrate == NeuralSubstrate.MALECNS_REMOTE
    val classic = d?.mode == NeuralMode.LEGACY
    val cooling = d != null && (d.thermalTier >= 2 || d.fallback == FallbackReason.THERMAL_SUSPENDED)
    val connected = remote && desktopPaired && d?.core?.extra?.get("malecns.connected") == 1f
    val fallback = d != null && d.fallback != FallbackReason.NONE
    val measured = d != null && !classic && !cooling && !fallback &&
        (if (remote) connected else d.steps > 0 && d.core.units > 0)
    val (title, explanation) = when {
        d == null -> "Waiting for brain status" to "The controller has not sent its first update yet."
        classic -> "Classic · brain off" to "Classic controls the avatar. Start a local brain to try neural reactions."
        cooling -> "Paused for cooling" to "Neural activity resumes when the phone cools. Inputs are paused too."
        remote && !desktopPaired -> "Computer not paired" to "MaleCNS needs your paired computer and Meow. A local brain works offline."
        fallback && d.fallback != FallbackReason.REMOTE_UNAVAILABLE -> "Classic fallback · check brain" to when (d.fallback) {
            FallbackReason.NON_FINITE_OUTPUT -> "The network returned an invalid value. Start the local brain again or check Neural settings."
            FallbackReason.DEADLINE -> "The network took too long to respond. Start the local brain again or check Neural settings."
            else -> "The network could not complete a step. Start the local brain again or check Neural settings."
        }
        remote && (d.fallback == FallbackReason.REMOTE_UNAVAILABLE || !connected) ->
            "Meow unavailable · Classic active" to "No fresh brain reply. Check the paired computer and Meow, or use a local brain."
        !measured -> "Starting local brain" to "Waiting for the first measured step."
        d.mode == NeuralMode.SHADOW -> "Shadow · observing" to "The brain reacts to inputs; Classic still controls the avatar."
        else -> "Neural · controlling avatar" to "Pet or feed your avatar and watch the measured activity respond."
    }
    fun signal(value: Float?): Float? = value?.takeIf { measured && it.isFinite() && it >= 0f }
    val metrics = if (remote) {
        listOf("feed" to "Feeding", "escape" to "Escape", "groom" to "Grooming", "wing" to "Wings").map { (key, label) ->
            BrainActivityMetric(key, label, signal(d?.core?.extra?.get("malecns.${key}_hz")), scale = 400f, unit = "Hz")
        }
    } else {
        val core = d?.core
        val active = core?.takeIf { it.units > 0 && it.silentUnits in 0..it.units }
            ?.let { (it.units - it.silentUnits).toFloat() / it.units }
        listOf(
            BrainActivityMetric("mean", "Mean signal", signal(core?.meanAbsActivity)),
            BrainActivityMetric("peak", "Peak signal", signal(core?.maxAbsActivity)),
            BrainActivityMetric("active", "Active units", signal(active)),
        )
    }
    return NeuralInfoPresentation(title, explanation,
        network = when (d?.substrate) {
            NeuralSubstrate.RATE -> "Recurrent · on this phone"
            NeuralSubstrate.SPIKING -> "Spiking · on this phone"
            NeuralSubstrate.BANC_EXPERIMENTAL -> "BANC circuit · on this phone"
            NeuralSubstrate.MALECNS_REMOTE -> "MaleCNS · runs on Meow"
            null -> "Behavior network"
        },
        measurementsAvailable = measured,
        interactionEnabled = measured,
        showLocalAction = d == null || classic || (!cooling && (!measured && (remote || fallback))),
        showPairAction = remote && !desktopPaired,
        metrics = metrics,
    )
}
