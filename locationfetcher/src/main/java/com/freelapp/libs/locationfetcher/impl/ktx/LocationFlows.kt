package com.freelapp.libs.locationfetcher.impl.ktx

import android.location.Location
import android.location.LocationManager
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.listener.LocationCallbackImpl
import com.freelapp.libs.locationfetcher.impl.listener.LocationListenerImpl
import com.freelapp.libs.locationfetcher.impl.singleton.GlobalState.PERMISSION_STATUS
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.coroutines.cancellation.CancellationException

@ExperimentalCoroutinesApi
internal fun LocationManager.locationFlowOf(
    locationRequest: LocationRequest,
    provider: LocationFetcher.Provider
): Flow<Location> =
    PERMISSION_STATUS
        .filter { it == LocationFetcher.PermissionStatus.ALLOWED }
        .flatMapLatest {
            callbackFlow {
                val listener = LocationListenerImpl { runCatching { offer(it) } }
                try {
                    requestLocationUpdates(
                        provider.value,
                        locationRequest.interval,
                        locationRequest.smallestDisplacement,
                        listener
                    )
                } catch (e: SecurityException) {
                    cancel(CancellationException(e))
                }
                awaitClose { removeUpdates(listener) }
            }
        }

@ExperimentalCoroutinesApi
internal fun FusedLocationProviderClient.locationFlowOf(
    locationRequest: LocationRequest
): Flow<Location> =
    PERMISSION_STATUS
        .filter { it == LocationFetcher.PermissionStatus.ALLOWED }
        .flatMapLatest {
            callbackFlow {
                val callback = LocationCallbackImpl { runCatching { offer(it) } }
                try {
                    requestLocationUpdates(locationRequest, callback, null)
                } catch (e: SecurityException) {
                    cancel(CancellationException(e))
                }
                awaitClose { removeLocationUpdates(callback) }
            }
        }

@ExperimentalCoroutinesApi
internal fun LocationFetcher.Provider.asLocationFlow(
    fusedLocationProviderClient: FusedLocationProviderClient,
    locationManager: LocationManager,
    locationRequest: LocationRequest
) = when (this) {
    LocationFetcher.Provider.GPS,
    LocationFetcher.Provider.Network -> locationManager.locationFlowOf(locationRequest, this)
    LocationFetcher.Provider.Fused -> fusedLocationProviderClient.locationFlowOf(locationRequest)
}
