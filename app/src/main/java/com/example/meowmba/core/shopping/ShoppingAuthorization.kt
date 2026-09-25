package com.example.meowmba.core.shopping

/** One-shot authority from accepted HUMAN input, not a model's inferred store or a task goal. */
class ShoppingAuthorization {
    private data class Pending(val request: ShoppingRequest, val conversation: String, val atMs: Long)
    private var pending: Pending? = null

    @Synchronized fun acceptUserText(text: String, conversation: String, nowMs: Long) {
        pending = ShoppingRequest.parse(text)?.let { Pending(it, conversation, nowMs) }
    }

    @Synchronized fun clear() { pending = null }

    @Synchronized fun consume(request: ShoppingRequest, conversation: String, nowMs: Long): Boolean {
        val grant = pending ?: return false
        if (grant.request != request || grant.conversation != conversation || nowMs - grant.atMs !in 0L..90_000L) return false
        pending = null
        return true
    }
}
