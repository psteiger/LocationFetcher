package com.freelapp.locationfetcher.compose

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.*
import kotlinx.coroutines.awaitCancellation

@Composable
public fun LocationFetcher(
    requestConfig: LocationRequest.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.checkLocationPermissionsAllowed()) }
    PermissionRequest(hasPermission) {
        hasPermission = it
    }
    if (hasPermission) {
        val locationRequest = LocationRequest.create().apply(requestConfig)
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

    public infix fun provides(
        location: Location?
    ): ProvidedValue<Location?> =
        LocalLocationFetcher.provides(location)
}

// ---- Implementation ----

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
