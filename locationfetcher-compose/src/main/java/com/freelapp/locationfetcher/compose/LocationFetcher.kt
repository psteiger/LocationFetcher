package com.freelapp.locationfetcher.compose

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.*
import kotlinx.coroutines.awaitCancellation

/**
 * Location is delivered to all children composable by [LocalLocationFetcher.current].
 */
@Composable
public fun LocationFetcher(
    requestConfig: LocationRequest.() -> Unit = {},
    rationaleUi: RationaleUi = DefaultRationaleUi,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(context.hasLocationPermissions()) }
    var rationaleDismissed by remember { mutableStateOf(false) }
    val setHasPermissions: (Boolean) -> Unit = {
        rationaleDismissed = false
        hasPermissions = it
    }
    PermissionRequest(
        hasPermissions = hasPermissions,
        setHasPermissions = setHasPermissions,
        rationaleDismissed = rationaleDismissed,
        rationale = { rationaleUi { rationaleDismissed = true } },
    )
    if (hasPermissions) {
        val locationRequest = rememberLocationRequest(requestConfig)
        SettingsRequest(locationRequest)
        LocationProvider(locationRequest, content)
    }
}

public object LocalLocationFetcher {
    private val LocalLocationFetcher =
        compositionLocalOf<Location?> { null }

    public val current: Location?
        @Composable
        get() = LocalLocationFetcher.current

    internal infix fun provides(
        location: Location?
    ): ProvidedValue<Location?> =
        LocalLocationFetcher.provides(location)
}

public typealias RationaleUi = @Composable (onRationaleDismissed: () -> Unit) -> Unit

// ---- Implementation ----

private val DefaultRationaleUi: RationaleUi = { onRationaleDismissed ->
    AlertDialog(
        onDismissRequest = { onRationaleDismissed() },
        confirmButton = {
            Button(onClick = { onRationaleDismissed() }) {
                Text(text = "Ok")
            }
        }
    )
}

@Composable
private fun rememberLocationRequest(
    requestConfig: LocationRequest.() -> Unit
) = remember(requestConfig) {
    LocationRequest.create().apply(requestConfig)
}

@SuppressLint("MissingPermission")
@Composable
private fun LocationProvider(
    locationRequest: LocationRequest,
    content: @Composable () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val client = rememberFusedLocationClient()
    val location by produceState<Location?>(null, client, locationRequest, lifecycle) {
        val callback = locationCallback()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                awaitCancellation()
            } finally {
                client.removeLocationUpdates(callback)
            }
        }
    }
    CompositionLocalProvider(
        LocalLocationFetcher provides location,
        content = content
    )
}

@Composable
private fun rememberFusedLocationClient(): FusedLocationProviderClient {
    val context = LocalContext.current
    val activity = context.getActivity()
    return remember(context, activity) {
        when (activity) {
            null -> LocationServices.getFusedLocationProviderClient(context)
            else -> LocationServices.getFusedLocationProviderClient(activity)
        }
    }
}

private fun ProduceStateScope<Location?>.locationCallback() = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
        value = locationResult.lastLocation
    }

    override fun onLocationAvailability(locationAvailability: LocationAvailability) {
    }
}
