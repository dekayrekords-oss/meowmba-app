package com.example.meowmba.core.agent

/** One use, short-lived approval bound to the exact visible app contents. */
class PhoneConfirmation(
    private val app: String,
    private val screen: String,
    private val createdAt: Long,
) {
    private val consumed = java.util.concurrent.atomic.AtomicBoolean(false)
    fun consume(currentApp: String, currentScreen: String, now: Long): Boolean =
        consumed.compareAndSet(false, true) && now - createdAt in 0..120_000 &&
            currentApp == app && currentScreen == screen
}
