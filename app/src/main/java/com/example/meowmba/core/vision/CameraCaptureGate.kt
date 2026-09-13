package com.example.meowmba.core.vision

/** Explicit camera permission at the application layer, separate from Android's
 * grant. Tokens invalidate asynchronous work across OFF/ON and bind replacement.
 * Callers still check platform permission, lifecycle and thermal conditions.
 */
internal class CameraCaptureGate {
    private var enabled = false
    private var consentEpoch = 0L
    private var bindingEpoch = 0L
    @Synchronized fun setEnabled(value: Boolean) {
        if (value != enabled) { enabled = value; consentEpoch++; bindingEpoch++ }
    }
    @Synchronized fun captureToken(): Long? = if (enabled) consentEpoch else null
    @Synchronized fun acceptsCapture(token: Long): Boolean = enabled && token == consentEpoch
    @Synchronized fun nextBinding(): Long? = if (enabled) ++bindingEpoch else null
    @Synchronized fun invalidateBindings(): Long = ++bindingEpoch
    @Synchronized fun acceptsBinding(token: Long): Boolean = enabled && token == bindingEpoch
    @Synchronized fun currentBinding(token: Long): Boolean = token == bindingEpoch
}
