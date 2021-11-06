package com.freelapp.libs.locationfetcher.impl.util

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal suspend inline fun <T> Task<T>.awaitComplete(): Task<out T> =
    if (isComplete) this else suspendCancellableCoroutine { continuation ->
        addOnCompleteListener({ it.run() }) {
            if (it.isCanceled) continuation.cancel(CancellationException("Task was cancelled"))
            else continuation.resume(it)
        }
    }
