package com.freelapp.libs.locationfetcher.impl.util

import android.content.Context
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import com.freelapp.libs.locationfetcher.LocationFetcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal open class ActivityRequester<I, O, T : ActivityResultContract<I, O>>(
    activity: FragmentActivity,
    contract: T
) {

    private val mutex = Mutex()
    private var continuation by eraseWhenRead<Continuation<O>>()
    private val requester = activity.registerForActivityResult(contract) {
        continuation!!.resume(it)
    }

    suspend fun request(input: I): O = mutex.withLock {
        suspendCoroutine { cont ->
            continuation = cont
            requester.launch(input)
        }
    }
}

internal class ResolutionResolver(
    activity: FragmentActivity
) : ActivityRequester<
        IntentSenderRequest,
        ActivityResult,
        ActivityResultContracts.StartIntentSenderForResult>(
    activity,
    ActivityResultContracts.StartIntentSenderForResult()
)

internal class PermissionRequester(
    activity: FragmentActivity,
    private val permissions: Array<String>,
    private val applicationContext: Context = activity.applicationContext
) : ActivityRequester<
        Array<String>,
        Map<String, Boolean>,
        ActivityResultContracts.RequestMultiplePermissions>(
    activity,
    ActivityResultContracts.RequestMultiplePermissions()
) {
    suspend fun requirePermissions(): LocationFetcher.PermissionStatus =
        applicationContext.hasPermissions(permissions) or
                request(permissions).values.all { it }.asPermissionStatus()
}
