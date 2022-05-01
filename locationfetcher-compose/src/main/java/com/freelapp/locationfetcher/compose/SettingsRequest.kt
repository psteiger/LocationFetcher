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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
internal fun SettingsRequest(
    locationRequest: LocationRequestWrapper,
    setSettingEnabled: (Boolean) -> Unit,
) {
    val state = remember { SettingsRequestState() }
    val context = rememberSettingsRequestContext(
        locationRequest = locationRequest,
        onResult = state::handleActivityResult
    )
    SettingsRequest(
        state = state,
        context = context,
        setSettingEnabled = setSettingEnabled
    )
}

// ---- Implementation ----

@Composable
private fun SettingsRequest(
    state: SettingsRequestState,
    context: SettingsRequestContext,
    setSettingEnabled: (Boolean) -> Unit
) {
    with(context) {
        LaunchRepeatCheckSettings(state = state)
        LaunchMaybeResolve(state = state)
    }
    state.isSettingEnabled?.let(setSettingEnabled)
}

@Composable
private fun SettingsRequestContext.LaunchRepeatCheckSettings(state: SettingsRequestState) {
    LaunchedEffect(this, state) {
        repeatCheckSettingsState(state)
    }
}

@Composable
private fun SettingsRequestContext.LaunchMaybeResolve(state: SettingsRequestState) {
    LaunchedEffect(this, state, state.isResolvable, state.userRefused) {
        maybeResolve(state)
    }
}

@Composable
private fun rememberSettingsRequestContext(
    locationRequest: LocationRequestWrapper,
    onResult: (ActivityResult) -> Unit,
): SettingsRequestContext {
    val lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle
    val client: SettingsClient = rememberSettingsClient()
    val request: LocationSettingsRequest = rememberLocationSettingsRequest(locationRequest)
    val resolver = rememberResolutionResolver(onResult)
    return remember(lifecycle, client, request, resolver) {
        SettingsRequestContext(lifecycle, client, request, resolver)
    }
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

@Composable
private fun rememberLocationSettingsRequest(locationRequest: LocationRequestWrapper) =
    remember(locationRequest) {
        LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest.locationRequest)
            .build()
    }

@Composable
private fun rememberResolutionResolver(
    onResult: (ActivityResult) -> Unit
): ResolutionResolver = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
    onResult
)

private fun SettingsRequestState.handleActivityResult(activityResult: ActivityResult) {
    when (activityResult.resultCode) {
        Activity.RESULT_OK -> settingsState = SettingsState.Resolved
        else -> userRefused = true
    }
}

private suspend fun SettingsRequestContext.repeatCheckSettingsState(state: SettingsRequestState) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        state.apply {
            userRefused = false
            settingsState = checkLocationSettings()
        }
    }
}

private fun SettingsRequestContext.maybeResolve(state: SettingsRequestState) {
    state.userRefused.takeIf { !it } ?: return
    val resolvable = state.settingsState as? SettingsState.Resolvable ?: return
    val intent = resolvable.exception.resolution
    val request = IntentSenderRequest.Builder(intent).build()
    resolver.launch(request)
}

private suspend fun SettingsRequestContext.checkLocationSettings() =
    client.checkLocationSettingsForRequest(request)

private typealias ResolutionResolver = ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>

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

private val SettingsRequestState.isSettingEnabled: Boolean?
    get() = when (settingsState) {
        null -> null
        SettingsState.Resolved -> true
        else -> false
    }

private data class SettingsRequestContext(
    val lifecycle: Lifecycle,
    val client: SettingsClient,
    val request: LocationSettingsRequest,
    val resolver: ResolutionResolver,
)

private class SettingsRequestState {
    var settingsState by mutableStateOf<SettingsState?>(null)
    var userRefused by mutableStateOf(false)

    // avoid launching different effects as two Resolvable instances can be different
    val isResolvable by derivedStateOf { settingsState is SettingsState.Resolvable }
}

private sealed class SettingsState {
    object Resolved : SettingsState()
    object Unresolvable : SettingsState()
    data class Resolvable(@Stable val exception: ResolvableApiException) : SettingsState()
}
