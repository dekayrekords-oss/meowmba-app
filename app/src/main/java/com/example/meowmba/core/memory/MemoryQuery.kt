package com.example.meowmba.core.memory

/** Literal tokens only: user punctuation must never become an FTS operator. */
object MemoryQuery {
    private val stops = setOf("a", "an", "the", "i", "me", "my", "you", "your", "is", "are", "was", "were", "do", "did", "does", "what", "which", "when", "where", "why", "how", "to", "of", "in", "on", "and", "or", "it", "that", "this", "we", "our", "about", "remember")
    fun tokens(text: String): List<String> = Regex("[\\p{L}\\p{N}]+")
        .findAll(text.take(4000).lowercase()).map { it.value }.filter { it !in stops }
        .distinct().take(12).toList()
    fun queries(text: String): List<String> {
        val words = tokens(text)
        if (words.isEmpty()) return emptyList()
        return listOf(words.joinToString(" AND ") { "\"$it\"" }, words.joinToString(" OR ") { "\"$it\"" }).distinct()
    }
}
