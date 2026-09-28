package com.example.meowmba.core.audio

/**
 * What the home screen says when the background voice-model download fails. The raw exception
 * ("Unable to resolve host …", "Software caused connection abort") means nothing to people.
 */
object VoicePackIssue {
    const val OFFLINE = "Voice models need an internet connection. Meow will try again the next time it opens, or retry in Voice recognition."
    const val FAILED = "Voice models couldn't be set up. Open Voice recognition to try again."

    fun label(failure: Throwable?): String? {
        if (failure == null) return null
        val network = generateSequence(failure) { it.cause }.any {
            it is java.net.UnknownHostException || it is java.net.SocketException || it is java.net.SocketTimeoutException ||
                it is javax.net.ssl.SSLException || it is java.io.InterruptedIOException
        }
        return if (network) OFFLINE else FAILED
    }
}
