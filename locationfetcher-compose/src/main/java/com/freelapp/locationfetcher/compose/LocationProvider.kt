package com.freelapp.locationfetcher.compose

import android.annotation.SuppressLint
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.*
import kotlinx.coroutines.awaitCancellation

@SuppressLint("MissingPermission")
@Composable
internal fun LocationProvider(
    locationRequest: LocationRequestWrapper,
    setLocation: (LocationResultWrapper?) -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val client = rememberFusedLocationClient()
    val context = remember(lifecycle, client) { lifecycle + client }
    LocationProvider(
        context = context,
        locationRequest = locationRequest,
        setLocation = setLocation
    )
}

@Composable
private fun LocationProvider(
    context: ProduceLocationContext,
    locationRequest: LocationRequestWrapper,
    setLocation: (LocationResultWrapper?) -> Unit
) {
    val location by produceState<LocationResultWrapper?>(null, context, locationRequest) {
        produceLocationScope(context) {
            produceLocation(locationRequest.locationRequest)
        }
    }
    setLocation(location)
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

@Immutable
private interface ProduceLocationContext {
    val lifecycle: Lifecycle
    val client: FusedLocationProviderClient
}

private data class ProduceLocationContextImpl(
    override val lifecycle: Lifecycle,
    override val client: FusedLocationProviderClient
) : ProduceLocationContext

private operator fun Lifecycle.plus(client: FusedLocationProviderClient): ProduceLocationContext =
    ProduceLocationContextImpl(this, client)

@Immutable
private interface ProduceLocationScope :
    ProduceLocationContext,
    ProduceStateScope<LocationResultWrapper?>

private suspend fun ProduceStateScope<LocationResultWrapper?>.produceLocationScope(
    context: ProduceLocationContext,
    block: suspend ProduceLocationScope.() -> Unit
) {
    (this + context).block()
}

private operator fun ProduceStateScope<LocationResultWrapper?>.plus(context: ProduceLocationContext) =
    object : ProduceLocationScope,
        ProduceStateScope<LocationResultWrapper?> by this,
        ProduceLocationContext by context {}

@SuppressLint("MissingPermission")
private suspend fun ProduceLocationScope.produceLocation(locationRequest: LocationRequest) {
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

private fun ProduceStateScope<LocationResultWrapper?>.locationCallback() =
    object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            value = LocationResultWrapper(locationResult)
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
        }
    }
