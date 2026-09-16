package com.example.meowmba.core.audio

/** Safe actionable errors without exposing response bodies or credentials. */
internal class CloudTranscriptionFailure(provider: String, status: Int) : Exception(when (status) {
    401, 403 -> "$provider rejected the API key or its permissions (HTTP $status)."
    402 -> "$provider requires payment or a higher credit limit (HTTP 402)."
    404 -> "$provider could not find the selected speech model (HTTP 404)."
    429 -> "$provider is rate limited (HTTP 429)."
    else -> "$provider transcription failed (HTTP $status)."
})
