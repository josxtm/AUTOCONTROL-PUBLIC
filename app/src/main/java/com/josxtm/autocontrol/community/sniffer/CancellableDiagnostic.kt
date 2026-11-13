package com.josxtm.autocontrol.community.sniffer

import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Coroutine cancellation actively closes the Java operation, including a blocked SSH read. */
internal suspend fun <T> cancellableDiagnostic(work: (CancellationToken) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val token = CancellationToken()
        continuation.invokeOnCancellation { token.cancel() }
        // This worker must finish resource cleanup even when the caller's coroutine is cancelled.
        CoroutineScope(Dispatchers.IO).launch {
            try { continuation.resume(work(token)) }
            catch (error: Exception) { continuation.resumeWithException(error) }
        }
    }
