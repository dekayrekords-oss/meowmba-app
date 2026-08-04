package com.example.meowmba.core.media

interface MusicGenerator {
    fun isAvailable(): Boolean
    suspend fun generate(prompt: String, durationSec: Int = 30): Result<ByteArray>
}
