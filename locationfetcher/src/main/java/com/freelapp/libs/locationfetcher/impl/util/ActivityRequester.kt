package com.freelapp.libs.locationfetcher.impl.util

import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import com.freelapp.libs.locationfetcher.LocationFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal open class ActivityRequester<I, O, T : ActivityResultContract<I, O>>(
    activity: FragmentActivity,
    contract: T
) : DefaultLifecycleObserver {

    private val mutex = Mutex()
    private var continuation by eraseWhenRead<Continuation<O>>()
    private val requester = activity.registerForActivityResult(contract) {
        Log.d("ActivityRequester", "mutex4 isLocked=${mutex.isLocked}")
        continuation!!.resume(it)
        Log.d("ActivityRequester", "mutex5 isLocked=${mutex.isLocked}")
    }

    suspend fun request(input: I): O = mutex.withLock {
        Log.d("ActivityRequester", "mutex isLocked=${mutex.isLocked}")
        val result = suspendCoroutine<O> { cont ->
            continuation = cont
            Log.d("ActivityRequester", "mutex2 isLocked=${mutex.isLocked}")
            requester.launch(input)
            Log.d("ActivityRequester", "mutex3 isLocked=${mutex.isLocked}")
        }
        Log.d("ActivityRequester", "mutex6 isLocked=${mutex.isLocked}")
        result
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
    private val activity: FragmentActivity,
    private val permissions: Array<String>
) : ActivityRequester<
        Array<String>,
        Map<String, Boolean>,
        ActivityResultContracts.RequestMultiplePermissions>(
    activity,
    ActivityResultContracts.RequestMultiplePermissions()
) {

    suspend fun requirePermissions(): LocationFetcher.PermissionStatus {
        return hasPermissions() or requestPermissions().values.all { it }.asPermissionStatus()
    }

    private suspend fun hasPermissions(): LocationFetcher.PermissionStatus =
        withContext(Dispatchers.Default) {
            activity.hasPermissions(permissions)
        }

    private suspend fun requestPermissions() = request(permissions)
}