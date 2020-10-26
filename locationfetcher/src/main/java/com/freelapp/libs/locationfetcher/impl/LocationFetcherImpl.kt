package com.freelapp.libs.locationfetcher.impl

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.util.PermissionChecker
import com.freelapp.libs.locationfetcher.impl.util.PermissionRequester
import com.freelapp.libs.locationfetcher.impl.util.ResolutionResolver
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@ExperimentalCoroutinesApi
internal class LocationFetcherImpl private constructor(
    lifecycleOwner: LifecycleOwner,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationManager: LocationManager,
    private val settingsClient: SettingsClient,
    private val config: LocationFetcher.Config,
    private val permissionChecker: PermissionChecker,
    private val resolutionResolver: ResolutionResolver? = null,
    private val permissionRequester: PermissionRequester? = null,
) : LocationFetcher, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "LocationFetcher"
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val locationRequest: LocationRequest = LocationRequest().apply {
        fastestInterval = config.fastestInterval
        interval = config.interval
        maxWaitTime = config.maxWaitTime
        priority = config.priority
        smallestDisplacement = config.smallestDisplacement
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this@LocationFetcherImpl)
    }

    constructor(
        activity: FragmentActivity,
        config: LocationFetcher.Config
    ) : this(
        activity,
        LocationServices.getFusedLocationProviderClient(activity),
        ContextCompat.getSystemService(activity, LocationManager::class.java) as LocationManager,
        LocationServices.getSettingsClient(activity),
        config,
        PermissionChecker(activity, LOCATION_PERMISSIONS),
        ResolutionResolver(activity),
        PermissionRequester(activity, LOCATION_PERMISSIONS)
    )

    constructor(
        context: Context,
        config: LocationFetcher.Config
    ) : this(
        ProcessLifecycleOwner.get(),
        LocationServices.getFusedLocationProviderClient(context),
        ContextCompat.getSystemService(context, LocationManager::class.java) as LocationManager,
        LocationServices.getSettingsClient(context),
        config,
        PermissionChecker(context, LOCATION_PERMISSIONS)
    )

    private val _location = MutableStateFlow<Location?>(null)
    private val _permissionStatus = MutableStateFlow<Boolean?>(null)
    private val _settingsStatus = MutableStateFlow<Boolean?>(null)

    override val location: StateFlow<Location?> = _location.asStateFlow()
    override val permissionStatus: StateFlow<Boolean?> = _permissionStatus.asStateFlow()
    override val settingsStatus: StateFlow<Boolean?> = _settingsStatus.asStateFlow()

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            logd("LocationListener: Received update $location")
            if (location.isValid()) {
                _location.value = location
                lastUpdateTimestamp = SystemClock.elapsedRealtime()
            }
        }

        override fun onProviderDisabled(provider: String) {
        }

        override fun onProviderEnabled(provider: String) {
        }
    }

    private var lastUpdateTimestamp: Long = 0
    private val fusedLocationClientRequest get() = locationRequest
    private val fusedLocationClientCallback = locationListener.asFusedLocationClientCallback()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        owner.lifecycleScope.launch {
            _permissionStatus.value = permissionChecker.hasPermissions().also {
                if (!it && config.requestLocationPermissions) {
                    permissionRequester?.requirePermissions()
                }
            }
            _settingsStatus.value = requestEnableLocationSettings()
            requestLocationUpdates()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopRequestingLocationUpdates()
    }

    override suspend fun requestLocationPermissions(): Boolean =
        permissionRequester?.requirePermissions() ?: false

    override suspend fun requestEnableLocationSettings(): Boolean {
        logd("checkSettings")
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()
        val taskResult = settingsClient
            .checkLocationSettings(request)
            .awaitTaskResult()
        try {
            taskResult.getResult(ApiException::class.java)
            // All location settings are satisfied. The client can initialize location
            logd("checkSettings: Satisfied.")
            return true
        } catch (e: ApiException) {
            logd("checkSettings: Not satisfied. Status code: ${e.statusCode}.", e)
            when (e.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    logd("checkSettings: Resolution required")
                    try {
                        // Cast to a resolvable exception.
                        val resolvable = e as ResolvableApiException
                        logd(
                            "checkSettings: Resolution possible with $resolutionResolver." +
                                    " config.requestLocationSettingEnablement=" +
                                    "${config.requestLocationSettingEnablement}"
                        )
                        if (resolutionResolver == null || !config.requestLocationSettingEnablement) {
                            return false
                        }
                        val req = IntentSenderRequest.Builder(resolvable.resolution).build()
                        val result = resolutionResolver.request(req)
                        return when (val code = result.resultCode) {
                            Activity.RESULT_OK -> true
                            Activity.RESULT_CANCELED -> false
                            else -> false.also { logd("Unknown result code: $code") }
                        }
                    } catch (e: IntentSender.SendIntentException) {
                        logd("checkSettings: SendIntentException", e)
                    } catch (e: ClassCastException) {
                        logd("checkSettings: ClassCastException", e)
                        // Ignore, should be an impossible error.
                    }
                }
            }
        } catch (e: Exception) {
            logd("checkSettings: An unknown exception happened", e)
        }
        return false
    }

    private fun Location.isValid(): Boolean {
        val currentLocation = location.value ?: return true

        fun getDisplacement(): Float {
            val result = FloatArray(1)
            Location.distanceBetween(
                latitude,
                longitude,
                currentLocation.latitude,
                currentLocation.longitude,
                result
            )
            return result[0]
        }

        val interval = locationRequest.interval
        val timeElapsed = SystemClock.elapsedRealtime() - lastUpdateTimestamp
        val displacement = getDisplacement()
        val smallestDisplacement = locationRequest.smallestDisplacement
        val isValid = timeElapsed > interval && displacement > smallestDisplacement
        logd(
            "isValid: currentLocation=$currentLocation, newLocation=$this" +
                    ", interval=$interval, timeElapsed=$timeElapsed" +
                    ", displacement=$displacement, smallestDisplacement=$smallestDisplacement" +
                    ", isValid=$isValid"
        )
        return isValid
    }

    private fun requestLocationUpdates() {
        config.providers.forEach(::requestLocationUpdates)
    }

    private fun requestLocationUpdates(provider: LocationFetcher.Provider) {
        logd("requestLocationUpdates: provider=$provider")
        try {
            when (provider) {
                LocationFetcher.Provider.GPS,
                LocationFetcher.Provider.Network -> locationManager.requestLocationUpdates(
                    provider.value,
                    locationRequest.interval,
                    locationRequest.smallestDisplacement,
                    locationListener
                )
                LocationFetcher.Provider.Fused -> fusedLocationClient.requestLocationUpdates(
                    fusedLocationClientRequest,
                    fusedLocationClientCallback,
                    null
                )
            }
        } catch (e: SecurityException) { // no permission
            logd("requestLocationUpdates: Couldn't request location updates", e)
        } catch (e: IllegalArgumentException) { // provider doesn't exist
            logd("requestLocationUpdates: Couldn't request location updates", e)
        }
    }

    private fun stopRequestingLocationUpdates() {
        config.providers.forEach(::stopRequestingLocationUpdates)
    }

    private fun stopRequestingLocationUpdates(provider: LocationFetcher.Provider) {
        logd("stopRequestingLocationUpdates: provider=$provider")
        try {
            when (provider) {
                LocationFetcher.Provider.GPS,
                LocationFetcher.Provider.Network -> locationManager.removeUpdates(locationListener)
                LocationFetcher.Provider.Fused -> fusedLocationClient.removeLocationUpdates(
                    fusedLocationClientCallback
                )
            }
        } catch (e: SecurityException) { // no permission
            logd("stopRequestingLocationUpdates: Couldn't stop requesting location updates", e)
        } catch (e: IllegalArgumentException) { // provider doesn't exist
            logd("stopRequestingLocationUpdates: Couldn't stop requesting location updates", e)
        }
    }

    private fun LocationListener.asFusedLocationClientCallback() = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            this@asFusedLocationClientCallback.onLocationChanged(locationResult.lastLocation)
        }
    }

    private suspend inline fun <T> Task<out T>.awaitTaskResult() =
        withContext(Dispatchers.Default) {
            suspendCoroutine<Task<out T>> { continuation ->
                runCatching {
                    addOnCompleteListener { continuation.resume(it) }
                }.onFailure {
                    continuation.resume(TaskCompletionSource<T>().withException(Exception(it)))
                }
            }
        }

    private fun <T> TaskCompletionSource<T>.withException(e: Exception): Task<T> =
        apply { setException(e) }.task

    @Suppress("NOTHING_TO_INLINE")
    private inline fun logd(msg: String, e: Throwable? = null) {
        if (config.debug) Log.d(TAG, msg, e)
    }
}