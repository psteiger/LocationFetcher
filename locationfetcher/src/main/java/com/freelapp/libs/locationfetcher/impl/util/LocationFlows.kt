package com.freelapp.libs.locationfetcher.impl.util

import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.entity.ApiHolder
import com.freelapp.libs.locationfetcher.impl.listener.LocationCallbackImpl
import com.freelapp.libs.locationfetcher.impl.listener.LocationListenerImpl
import com.freelapp.libs.locationfetcher.impl.singleton.PERMISSION_STATUS
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

internal fun Iterable<LocationFetcher.Provider>.asLocationFlow(
    apiHolder: ApiHolder,
    locationRequest: LocationRequest,
): Flow<Location> = locationContext(apiHolder, locationRequest) {
    asLocationFlows()
        .merge()
        .onCompletion { cancelContext(it) }
        .flowOn(dispatcher.immediate)
}

private inline fun <T> locationContext(
    apiHolder: ApiHolder,
    locationRequest: LocationRequest,
    block: LocationContext.() -> T
): T = LocationContext(apiHolder, locationRequest).block()

private class LocationContext(
    private val apiHolder: ApiHolder,
    private val locationRequest: LocationRequest,
) {
    private val thread = HandlerThread("LocationFlowThread").apply { start() }
    val dispatcher = Handler(thread.looper).asCoroutineDispatcher("LocationFlowDispatcher")

    fun Iterable<LocationFetcher.Provider>.asLocationFlows(): List<Flow<Location>> =
        map { it.asLocationFlow() }

    private fun LocationFetcher.Provider.asLocationFlow() = when (this) {
        is LocationFetcher.Provider.GPS,
        is LocationFetcher.Provider.Network -> apiHolder.locationManager.locationFlowOf(this)
        is LocationFetcher.Provider.Fused -> apiHolder.fusedLocationClient.locationFlowOf()
    }

    private fun LocationManager.locationFlowOf(provider: LocationFetcher.Provider): Flow<Location> =
        PERMISSION_STATUS.flatMapLatest {
            if (!it) emptyFlow() else asFlow(provider)
        }

    private fun LocationManager.asFlow(provider: LocationFetcher.Provider) =
        callbackFlow {
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
                cancel("LocationManager.requestLocationUpdates error", e)
            }
            awaitClose { removeUpdates(listener) }
        }

    private fun FusedLocationProviderClient.locationFlowOf(): Flow<Location> =
        PERMISSION_STATUS.flatMapLatest {
            if (!it) emptyFlow() else asFlow()
        }

    private fun FusedLocationProviderClient.asFlow() =
        callbackFlow {
            val callback = LocationCallbackImpl { trySend(it) }
            try {
                requestLocationUpdates(locationRequest, callback, looper)
            } catch (e: SecurityException) {
                cancel("FusedLocationProviderClient.requestLocationUpdates error", e)
            }
            awaitClose { removeLocationUpdates(callback) }
        }

    fun cancelContext(cause: Throwable?) {
        dispatcher.cancel(CancellationException("Cancelling context", cause))
        thread.quit()
    }
}
