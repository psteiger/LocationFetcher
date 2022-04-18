package com.freelapp.libs.locationfetcher.impl.util

import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.entity.ApiHolder
import com.freelapp.libs.locationfetcher.impl.listener.LocationCallbackImpl
import com.freelapp.libs.locationfetcher.impl.listener.LocationListenerImpl
import com.freelapp.libs.locationfetcher.impl.singleton.PERMISSION_STATUS
import com.freelapp.libs.locationfetcher.impl.singleton.TAG
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

internal fun Iterable<LocationFetcher.Provider>.asLocationFlow(
    apiHolder: ApiHolder,
    locationRequest: LocationRequest,
): Flow<Location> {
    val thread = HandlerThread("LocationFlowThread").apply { start() }
    val dispatcher = Handler(thread.looper).asCoroutineDispatcher("LocationFlowDispatcher")
    return asLocationFlows(apiHolder, locationRequest, thread.looper)
        .merge()
        .onCompletion {
            thread.quit()
            dispatcher.cancel()
        }
        .flowOn(dispatcher)
}

private fun Iterable<LocationFetcher.Provider>.asLocationFlows(
    apiHolder: ApiHolder,
    locationRequest: LocationRequest,
    looper: Looper
): List<Flow<Location>> =
    map { provider ->
        provider.asLocationFlow(
            apiHolder.fusedLocationClient,
            apiHolder.locationManager,
            locationRequest,
            looper
        )
    }

private fun LocationFetcher.Provider.asLocationFlow(
    fusedLocationProviderClient: FusedLocationProviderClient,
    locationManager: LocationManager,
    locationRequest: LocationRequest,
    looper: Looper
) = when (this) {
    LocationFetcher.Provider.GPS,
    LocationFetcher.Provider.Network -> locationManager.locationFlowOf(
        locationRequest,
        this,
        looper
    )
    LocationFetcher.Provider.Fused -> fusedLocationProviderClient.locationFlowOf(
        locationRequest,
        looper
    )
}

private fun LocationManager.locationFlowOf(
    request: LocationRequest,
    provider: LocationFetcher.Provider,
    looper: Looper
): Flow<Location> =
    PERMISSION_STATUS.flatMapLatest {
        if (!it) emptyFlow()
        else asFlow(request, provider, looper)
    }

private fun FusedLocationProviderClient.locationFlowOf(
    request: LocationRequest,
    looper: Looper
): Flow<Location> =
    PERMISSION_STATUS.flatMapLatest {
        if (!it) emptyFlow()
        else asFlow(request, looper)
    }

private fun LocationManager.asFlow(
    request: LocationRequest,
    provider: LocationFetcher.Provider,
    looper: Looper
) = callbackFlow {
    val listener = LocationListenerImpl { trySend(it) }
    try {
        requestLocationUpdates(
            provider.value,
            request.interval,
            request.smallestDisplacement,
            listener,
            looper
        )
    } catch (e: SecurityException) {
        Log.d(TAG, "LocationManager.asFlow: exception", e)
        close(e)
    }
    awaitClose { removeUpdates(listener) }
}

private fun FusedLocationProviderClient.asFlow(
    request: LocationRequest,
    looper: Looper
) = callbackFlow {
    val callback = LocationCallbackImpl { trySend(it) }
    try {
        requestLocationUpdates(request, callback, looper)
    } catch (e: SecurityException) {
        Log.d(TAG, "FusedLocationProviderClient.asFlow: exception", e)
        close(e)
    }
    awaitClose { removeLocationUpdates(callback) }
}
