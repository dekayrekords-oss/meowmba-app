package com.example.meowmba.core.agent.runtime

/** Bounded, conversation-scoped working context. History is reference data, never approval. */
class TurnContext(private val now: () -> Long = System::currentTimeMillis) {
    private var conversation = Long.MIN_VALUE
    private val turns = ArrayDeque<Pair<String, String>>()
    private var weatherUntil = 0L
    fun scope(id: Long) {
        if (conversation != id) { conversation = id; turns.clear(); weatherUntil = 0 }
    }
    fun add(role: String, text: String) {
        val turn = role to text.take(800)
        if (turns.lastOrNull() != turn) turns.addLast(turn)
        while (turns.size > 8) turns.removeFirst()
    }
    fun history(): String? = turns.takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.first}: ${it.second}" }
    fun awaitWeatherPlace() { weatherUntil = now() + 180_000 }
    fun awaitingWeatherPlace() = weatherUntil > now()
    fun clearWeather() { weatherUntil = 0 }
    companion object {
        fun dependent(text: String): Boolean {
            val n = text.lowercase().trim()
            return Regex("\\b(it|that|those|them|same|instead|there|again)\\b").containsMatchIn(n) ||
                Regex("^(yes|yeah|yep|sure|ok|okay|and|also|what about|how about)\\b").containsMatchIn(n)
        }
    }
}
