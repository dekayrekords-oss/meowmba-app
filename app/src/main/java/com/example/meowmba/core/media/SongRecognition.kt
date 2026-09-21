package com.example.meowmba.core.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicit, foreground-only song lookup. Audio is kept in memory and never written to disk. */
interface SongRecognizer {
    suspend fun recordSample(): ByteArray
    suspend fun identify(wav: ByteArray, token: String): SongMatch?
}

class SongRecognition : SongRecognizer {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()

    @SuppressLint("MissingPermission") // The caller must complete the Android permission flow first.
    override suspend fun recordSample(): ByteArray = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "This device cannot capture a music sample." }
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable. Close other recording apps and try again." }
            val pcm = ByteArray(RATE * SECONDS * 2)
            var read = 0
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start." }
            withTimeout(15_000) {
                while (read < pcm.size) {
                    currentCoroutineContext().ensureActive()
                    val count = recorder.read(pcm, read, minOf(4096, pcm.size - read), AudioRecord.READ_NON_BLOCKING)
                    check(count >= 0) { "The microphone stopped while listening. Please try again." }
                    if (count == 0) delay(10) else read += count
                }
            }
            wav(pcm)
        } catch (_: TimeoutCancellationException) {
            throw IOException("No complete audio sample arrived. Check microphone access and try again.")
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    override suspend fun identify(wav: ByteArray, token: String): SongMatch? {
        require(token.isNotBlank()) { "Add your AudD key in Avatar settings first." }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("api_token", token)
            .addFormDataPart("file", "song.wav", wav.toRequestBody("audio/wav".toMediaType())).build()
        val call = client.newCall(Request.Builder().url("https://api.audd.io/").post(body).build())
        val payload = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("Song lookup could not connect. Check your internet connection."))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val value = runCatching {
                            check(it.isSuccessful) { "Song lookup returned HTTP ${it.code}. Check your AudD account and try again." }
                            // Never expose provider request echoes, credentials or raw error bodies.
                            it.body?.string().orEmpty().also { text -> check(text.length <= 128_000) { "Song lookup returned an invalid response." } }
                        }
                        if (continuation.isActive) value.fold(continuation::resume, continuation::resumeWithException)
                    }
                }
            })
        }
        return parse(payload)
    }

    companion object {
        const val RATE = 16_000
        const val SECONDS = 10
        fun wav(pcm: ByteArray): ByteArray = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size); put(pcm)
        }.array()
        fun parse(payload: String): SongMatch? {
            val json = runCatching { JSONObject(payload) }.getOrElse { error("Song lookup returned an unreadable response.") }
            check(json.optString("status") == "success") { "Song recognition is unavailable. Check your AudD key and account allowance." }
            val result = json.optJSONObject("result") ?: return null
            val title = result.optString("title").trim().take(200)
            val artist = result.optString("artist").trim().take(200)
            return title.takeIf { it.isNotBlank() && it != "null" }?.let { SongMatch(it, artist.takeUnless { a -> a.isBlank() || a == "null" }) }
        }
        fun isRequest(text: String): Boolean = Regex(
            "(?i)^(?:please )?(?:what(?:’s|'s| is) (?:this song|playing|the song|the music)|what (?:song|music|track) (?:is (?:this|playing)|am i listening to)|who is (?:singing|the artist)|(?:identify|recognize|recognise|listen to) (?:this|the) (?:song|music)|what song (?:can you hear|do you hear))[?.!]*$"
        ).matches(text.trim())
    }
}

data class SongMatch(val title: String, val artist: String?) {
    val label get() = if (artist == null) title else "$title by $artist"
}
