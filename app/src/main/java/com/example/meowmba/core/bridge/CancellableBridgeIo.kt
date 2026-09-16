package com.example.meowmba.core.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.EmptyCoroutineContext

/** Socket teardown can itself block; neither a read nor disconnect may pin the UI. */
internal suspend fun <T> cancellableBridgeIo(disconnect: () -> Unit, read: () -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { runCatching(disconnect) })
        }
        Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
            if (continuation.isActive) continuation.resumeWith(runCatching(read))
        })
    }
