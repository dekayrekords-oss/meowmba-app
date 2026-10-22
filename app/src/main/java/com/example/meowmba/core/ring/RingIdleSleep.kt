package com.example.meowmba.core.ring

/** Local standby keeps the Bluetooth link available for a double-tap wake. */
class RingIdleSleep(
    private val now: () -> Long,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val publish: (Boolean) -> Unit,
) {
    var sleeping = false; private set
    private var active = false
    private var timeout = 30_000L
    private var lastActivity = 0L
    private var cancelTimer: (() -> Unit)? = null
    private var generation = 0L
    fun configure(enabled: Boolean, seconds: Int) {
        val delay = seconds.coerceIn(5, 300) * 1_000L
        if (enabled == active && delay == timeout) return
        cancel(); active = enabled; timeout = delay
        setSleeping(false)
        if (active) { lastActivity = now(); arm(timeout) }
    }
    fun checkIdle() { if (active && !sleeping && now() - lastActivity >= timeout) sleepNow() }
    fun activity() {
        checkIdle()
        if (!active || sleeping) return
        lastActivity = now(); arm(timeout)
    }
    private fun arm(delay: Long) {
        cancel()
        val token = generation
        cancelTimer = schedule(delay.coerceAtLeast(1)) {
            if (token == generation && active && !sleeping) {
                val remaining = timeout - (now() - lastActivity)
                if (remaining > 0) arm(remaining) else sleepNow()
            }
        }
    }
    private fun cancel() { generation++; cancelTimer?.invoke(); cancelTimer = null }
    private fun setSleeping(value: Boolean) {
        if (sleeping != value) { sleeping = value; publish(value) }
    }
    fun sleepNow() { if (active) { cancel(); setSleeping(true) } }
    fun wake() { if (active) { lastActivity = now(); setSleeping(false); arm(timeout) } }
    /** True means consumed: the waking double tap must never also perform Back/click. */
    fun consumeSleepingGesture(gesture: String): Boolean {
        if (!sleeping) return false
        if (gesture == "Double tap") wake()
        return true
    }
}
