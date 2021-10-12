package com.freelapp.libs.locationfetcher.impl.ktx

import android.location.Location
import android.location.LocationManager
import android.os.HandlerThread
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.entity.ApiHolder
import com.freelapp.libs.locationfetcher.impl.listener.LocationCallbackImpl
import com.freelapp.libs.locationfetcher.impl.listener.LocationListenerImpl
import com.freelapp.libs.locationfetcher.impl.singleton.GlobalState.PERMISSION_STATUS
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException

internal fun LocationManager.locationFlowOf(
    locationRequest: LocationRequest,
    provider: LocationFetcher.Provider
): Flow<Location> =
    PERMISSION_STATUS
        .filter { it == LocationFetcher.PermissionStatus.ALLOWED }
        .flatMapLatest {
            callbackFlow {
                val thread = HandlerThread("LocationManagerThread").apply { start() }
                val listener = LocationListenerImpl { trySend(it) }
                try {
                    requestLocationUpdates(
                        provider.value,
                        locationRequest.interval,
                        locationRequest.smallestDisplacement,
                        listener,
                        thread.looper
                    )
                } catch (e: SecurityException) {
                    cancel(CancellationException(e))
                }
                awaitClose {
                    removeUpdates(listener)
                    thread.quit()
                }
            }
        }
        .flowOn(Dispatchers.IO)

internal fun FusedLocationProviderClient.locationFlowOf(
    locationRequest: LocationRequest
): Flow<Location> =
    PERMISSION_STATUS
        .filter { it == LocationFetcher.PermissionStatus.ALLOWED }
        .flatMapLatest {
            callbackFlow {
                val thread = HandlerThread("FusedLocationProviderClientThread").apply { start() }
                val callback = LocationCallbackImpl { trySend(it) }
                try {
                    requestLocationUpdates(locationRequest, callback, thread.looper)
                } catch (e: SecurityException) {
                    cancel(CancellationException(e))
                }
                awaitClose {
                    removeLocationUpdates(callback)
                    thread.quit()
                }
            }
        }
        .flowOn(Dispatchers.IO)

internal fun LocationFetcher.Provider.asLocationFlow(
    fusedLocationProviderClient: FusedLocationProviderClient,
    locationManager: LocationManager,
    locationRequest: LocationRequest
) = when (this) {
    LocationFetcher.Provider.GPS,
    LocationFetcher.Provider.Network -> locationManager.locationFlowOf(locationRequest, this)
    LocationFetcher.Provider.Fused -> fusedLocationProviderClient.locationFlowOf(locationRequest)
}

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

internal fun List<LocationFetcher.Provider>.asLocationFlow(
    apiHolder: ApiHolder,
    locationRequest: LocationRequest
): Flow<Location> =
    merge(*asLocationFlows(apiHolder, locationRequest).toTypedArray())
