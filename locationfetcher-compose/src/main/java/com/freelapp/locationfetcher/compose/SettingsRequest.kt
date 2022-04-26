package com.freelapp.locationfetcher.compose

import android.app.Activity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
internal fun SettingsRequest(
    locationRequest: LocationRequest,
    onResult: (Boolean) -> Unit = {}
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val client = rememberSettingsClient()
    val request = remember(locationRequest) {
        LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()
    }
    val resolver = rememberResolutionResolver {
        onResult(it.resultCode == Activity.RESULT_OK)
    }
    val result by produceState<SettingsState?>(null, client, request, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = client.checkLocationSettingsForRequest(request)
        }
    }
    when (result) {
        is SettingsState.Resolvable -> {
            val resolutionIntent = (result as SettingsState.Resolvable).exception.resolution
            val resolutionRequest = IntentSenderRequest.Builder(resolutionIntent).build()
            SideEffect {
                resolver.launch(resolutionRequest)
            }
        }
        SettingsState.Resolved -> onResult(true)
        SettingsState.Unresolvable -> onResult(false)
        null -> {}
    }
}

@Composable
private fun rememberResolutionResolver(
    onResult: (ActivityResult) -> Unit
): ResolutionResolver = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
    onResult
)

private typealias ResolutionResolver = ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>

private sealed class SettingsState {
    object Resolved : SettingsState()
    object Unresolvable : SettingsState()
    data class Resolvable(val exception: ResolvableApiException) : SettingsState()
}

@Composable
private fun rememberSettingsClient(): SettingsClient {
    val context = LocalContext.current
    val activity = context.getActivity()
    return remember(context, activity) {
        when (activity) {
            null -> LocationServices.getSettingsClient(context)
            else -> LocationServices.getSettingsClient(activity)
        }
    }
}

private suspend fun SettingsClient.checkLocationSettingsForRequest(
    request: LocationSettingsRequest,
): SettingsState =
    try {
        checkLocationSettings(request)
            .awaitComplete()
            .getResult(ApiException::class.java)
        SettingsState.Resolved
    } catch (e: ApiException) {
        try {
            SettingsState.Resolvable(e as ResolvableApiException)
        } catch (_: ClassCastException) {
            SettingsState.Unresolvable
        }
    } catch (_: Throwable) {
        SettingsState.Unresolvable
    }

private suspend inline fun <T> Task<T>.awaitComplete(): Task<out T> =
    if (isComplete) this else suspendCancellableCoroutine { continuation ->
        addOnCompleteListener({ it.run() }) {
            if (it.isCanceled) continuation.cancel(CancellationException("Task was cancelled"))
            else continuation.resume(it)
        }
    }
