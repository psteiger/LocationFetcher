package com.freelapp.libs.locationfetcher.impl

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.freelapp.flowlifecycleobserver.observeIn
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.entity.ApiHolder
import com.freelapp.libs.locationfetcher.impl.ktx.asLocationFlow
import com.freelapp.libs.locationfetcher.impl.singleton.GlobalState.LOCATION
import com.freelapp.libs.locationfetcher.impl.singleton.GlobalState.PERMISSION_STATUS
import com.freelapp.libs.locationfetcher.impl.singleton.GlobalState.SETTINGS_STATUS
import com.freelapp.libs.locationfetcher.impl.util.PermissionChecker
import com.freelapp.libs.locationfetcher.impl.util.asSettingsStatus
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@ExperimentalCoroutinesApi
internal class LocationFetcherImpl private constructor(
    lifecycleOwner: LifecycleOwner,
    private val applicationContext: Context,
    private val config: LocationFetcher.Config,
) : LocationFetcher, DefaultLifecycleObserver {

    private var apiHolder: ApiHolder? = null
    private val permissionChecker = PermissionChecker(applicationContext, LOCATION_PERMISSIONS)

    private val locationRequest: LocationRequest = LocationRequest().apply {
        fastestInterval = config.fastestInterval
        interval = config.interval
        maxWaitTime = config.maxWaitTime
        priority = config.priority
        smallestDisplacement = config.smallestDisplacement
        numUpdates = config.numUpdates
    }

    constructor(
        activity: FragmentActivity,
        config: LocationFetcher.Config
    ) : this(
        activity,
        activity.applicationContext,
        config
    )

    constructor(
        context: Context,
        config: LocationFetcher.Config
    ) : this(
        ProcessLifecycleOwner.get(),
        context.applicationContext,
        config
    )

    override val location: StateFlow<Location?> = LOCATION.asStateFlow()
    override val permissionStatus: StateFlow<LocationFetcher.PermissionStatus> =
        PERMISSION_STATUS.asStateFlow()
    override val settingsStatus: StateFlow<LocationFetcher.SettingsStatus> =
        SETTINGS_STATUS.asStateFlow()

    private val lastUpdateTimestamp = AtomicLong(0L)

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        apiHolder = ApiHolder.create(owner, applicationContext).also {
            val locationFlows = config.providers.map { provider ->
                provider.asLocationFlow(
                    it.fusedLocationClient,
                    it.locationManager,
                    locationRequest
                )
            }.toTypedArray()

            merge(*locationFlows)
                .filter { it.isValid() }
                .onEach {
                    LOCATION.value = it
                    lastUpdateTimestamp.set(SystemClock.elapsedRealtime())
                }
                .observeIn(owner)
        }

        // Note: On older Android versions, there is no concept of onResume/onPause, so a dialog
        // is shown repeatedly (onStop called when dialog is showing, onStart called when it is
        // closed).
        owner.lifecycleScope.launchWhenStarted {
            if (shouldRequestLocationPermissions()) requestLocationPermissions()
            if (shouldRequestEnableLocationSettings()) requestEnableLocationSettings()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        apiHolder = null // avoid activity leaks
    }

    override suspend fun requestLocationPermissions(): LocationFetcher.PermissionStatus {
        val result = apiHolder?.permissionRequester?.requirePermissions()
            ?: LocationFetcher.PermissionStatus.UNKNOWN
        updatePermissionsStatusFlow(result)
        return result
    }

    override suspend fun requestEnableLocationSettings(): LocationFetcher.SettingsStatus {
        logd("checkSettings")
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()
        val taskResult = apiHolder
            ?.settingsClient
            ?.checkLocationSettings(request)
            ?.awaitTaskResult()
        if (taskResult == null) {
            updateSettingsStatusFlow(LocationFetcher.SettingsStatus.UNKNOWN)
            return LocationFetcher.SettingsStatus.UNKNOWN
        }
        try {
            taskResult.getResult(ApiException::class.java)
            // All location settings are satisfied. The client can initialize location
            logd("checkSettings: Satisfied.")
            updateSettingsStatusFlow(LocationFetcher.SettingsStatus.ENABLED)
            return LocationFetcher.SettingsStatus.ENABLED
        } catch (e: ApiException) {
            logd("checkSettings: Not satisfied. Status code: ${e.statusCode}.", e)
            when (e.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                    logd("checkSettings: Resolution required")
                    // Cast to a resolvable exception.
                    val resolvable = e as ResolvableApiException
                    val resolver = apiHolder?.resolutionResolver
                    logd("checkSettings: Resolution possible with $resolver.")
                    if (resolver == null) return SETTINGS_STATUS.value
                    val req = IntentSenderRequest.Builder(resolvable.resolution).build()
                    val result = resolver.request(req).resultCode.asSettingsStatus()
                    updateSettingsStatusFlow(result)
                    return result
                } catch (e: IntentSender.SendIntentException) {
                    logd("checkSettings: SendIntentException", e)
                } catch (e: ClassCastException) {
                    logd("checkSettings: ClassCastException", e)
                    // Ignore, should be an impossible error.
                }
            }
        } catch (e: Exception) {
            logd("checkSettings: An exception happened", e)
        }
        updateSettingsStatusFlow(LocationFetcher.SettingsStatus.DISABLED)
        return LocationFetcher.SettingsStatus.DISABLED
    }

    private fun updatePermissionsStatusFlow(permission: LocationFetcher.PermissionStatus) {
        PERMISSION_STATUS.value = permission
    }

    private fun updateSettingsStatusFlow(permission: LocationFetcher.SettingsStatus) {
        SETTINGS_STATUS.value = permission
    }

    private suspend fun shouldRequestLocationPermissions() =
        checkLocationPermissionsAllowed() != LocationFetcher.PermissionStatus.ALLOWED &&
                config.requestLocationPermissions

    private suspend fun shouldRequestEnableLocationSettings() =
        checkLocationSettingsEnabled() != LocationFetcher.SettingsStatus.ENABLED &&
                config.requestEnableLocationSettings

    private suspend fun checkLocationPermissionsAllowed(): LocationFetcher.PermissionStatus {
        val hasPermissions = permissionChecker.hasPermissions()
        updatePermissionsStatusFlow(hasPermissions)
        return hasPermissions
    }

    private suspend fun checkLocationSettingsEnabled(): LocationFetcher.SettingsStatus {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()
        val taskResult = apiHolder
            ?.settingsClient
            ?.checkLocationSettings(request)
            ?.awaitTaskResult()
        return runCatching {
            taskResult!!.getResult(ApiException::class.java)
            // All location settings are satisfied. The client can initialize location
            logd("checkLocationSettingsEnabled: Satisfied.")
            updateSettingsStatusFlow(LocationFetcher.SettingsStatus.ENABLED)
            LocationFetcher.SettingsStatus.ENABLED
        }.getOrElse {
            updateSettingsStatusFlow(LocationFetcher.SettingsStatus.DISABLED)
            LocationFetcher.SettingsStatus.DISABLED
        }
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
        val timeElapsed = SystemClock.elapsedRealtime() - lastUpdateTimestamp.get()
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

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    companion object {
        private const val TAG = "LocationFetcher"
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}