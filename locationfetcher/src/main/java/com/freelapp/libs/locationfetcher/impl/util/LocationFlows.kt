package com.freelapp.libs.locationfetcher.impl.util

import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

private val thread = HandlerThread("LocationFlowThread").apply { start() }
private val dispatcher = Handler(thread.looper).asCoroutineDispatcher("LocationFlowDispatcher")

internal fun LocationManager.locationFlowOf(
    request: LocationRequest,
    provider: LocationFetcher.Provider
): Flow<Location> =
    PERMISSION_STATUS
        .flatMapLatest {
            if (!it) emptyFlow()
            else asFlow(request, provider)
        }
        .flowOn(dispatcher)

internal fun LocationManager.asFlow(request: LocationRequest, provider: LocationFetcher.Provider) =
    callbackFlow {
        val listener = LocationListenerImpl { trySend(it) }
        try {
            requestLocationUpdates(
                provider.value,
                request.interval,
                request.smallestDisplacement,
                listener,
                thread.looper
            )
        } catch (e: SecurityException) {
            Log.d(TAG, "LocationManager.asFlow: exception", e)
            close(e)
        }
        awaitClose { removeUpdates(listener) }
    }

internal fun FusedLocationProviderClient.locationFlowOf(request: LocationRequest): Flow<Location> =
    PERMISSION_STATUS
        .flatMapLatest {
            if (!it) emptyFlow()
            else asFlow(request)
        }
        .flowOn(dispatcher)

internal fun FusedLocationProviderClient.asFlow(request: LocationRequest) = callbackFlow {
    val callback = LocationCallbackImpl { trySend(it) }
    try {
        requestLocationUpdates(request, callback, thread.looper)
    } catch (e: SecurityException) {
        Log.d(TAG, "FusedLocationProviderClient.asFlow: exception", e)
        close(e)
    }
    awaitClose { removeLocationUpdates(callback) }
}

internal fun LocationFetcher.Provider.asLocationFlow(
    fusedLocationProviderClient: FusedLocationProviderClient,
    locationManager: LocationManager,
    locationRequest: LocationRequest
) = when (this) {
    LocationFetcher.Provider.GPS,
    LocationFetcher.Provider.Network -> locationManager.locationFlowOf(locationRequest, this)
    LocationFetcher.Provider.Fused -> fusedLocationProviderClient.locationFlowOf(locationRequest)
}

internal fun List<LocationFetcher.Provider>.asLocationFlow(
    apiHolder: ApiHolder,
    locationRequest: LocationRequest
): Flow<Location> =
    asLocationFlows(apiHolder, locationRequest).merge()

internal fun List<LocationFetcher.Provider>.asLocationFlows(
    apiHolder: ApiHolder,
    locationRequest: LocationRequest
): List<Flow<Location>> =
    map { provider ->
        provider.asLocationFlow(
            apiHolder.fusedLocationClient,
            apiHolder.locationManager,
            locationRequest
        )
    }