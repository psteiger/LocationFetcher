package com.freelapp.libs.locationfetcher.impl

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.entity.ApiHolder
import com.freelapp.libs.locationfetcher.impl.entity.createDataSources
import com.freelapp.libs.locationfetcher.impl.ktx.asLocationFlow
import com.freelapp.libs.locationfetcher.impl.singleton.GlobalState.LOCATION
import com.freelapp.libs.locationfetcher.impl.singleton.GlobalState.PERMISSION_STATUS
import com.freelapp.libs.locationfetcher.impl.singleton.GlobalState.SETTINGS_STATUS
import com.freelapp.libs.locationfetcher.impl.util.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

internal class LocationFetcherImpl private constructor(
    owner: LifecycleOwner,
    private val applicationContext: Context,
    private val config: LocationFetcher.Config
) : LocationFetcher {

    constructor(
        activity: ComponentActivity,
        config: LocationFetcher.Config
    ) : this(
        activity,
        activity.applicationContext,
        config
    )

    constructor(
        context: Context,
        owner: LifecycleOwner,
        config: LocationFetcher.Config
    ) : this(
        owner,
        context.applicationContext,
        config
    )

    private val apiHolder: MutableStateFlow<ApiHolder?> =
        owner.lifecycleMutableStateFlow(Lifecycle.State.CREATED) {
            it.createDataSources(applicationContext)
        }
    private val resolutionResolver: ResolutionResolver? by owner.lifecycle(Lifecycle.State.CREATED) {
        (it as? ComponentActivity)?.resolutionResolver { activityResult ->
            val result = activityResult.resultCode.asSettingsStatus()
            updateSettingsStatusFlow(result)
        }
    }
    private val permissionRequester: PermissionRequester? by owner.lifecycle(Lifecycle.State.CREATED) {
        (it as? ComponentActivity)?.permissionRequester { map ->
            val result = map.values.all { it }.asPermissionStatus()
            updatePermissionsStatusFlow(result)
        }
    }
    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        fastestInterval = config.fastestInterval
        interval = config.interval
        maxWaitTime = config.maxWaitTime
        priority = config.priority
        smallestDisplacement = config.smallestDisplacement
        numUpdates = config.numUpdates
        isWaitForAccurateLocation = config.isWaitForAccurateLocation
    }
    override val location: StateFlow<Location?> = LOCATION.asStateFlow()
    override val permissionStatus: StateFlow<LocationFetcher.PermissionStatus> =
        PERMISSION_STATUS.asStateFlow()
    override val settingsStatus: StateFlow<LocationFetcher.SettingsStatus> =
        SETTINGS_STATUS.asStateFlow()
    private val lastUpdateTimestamp = AtomicLong(0L)

    init {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                apiHolder
                    .filterNotNull()
                    .flatMapLatest { apis ->
                        config.providers
                            .asLocationFlow(apis, locationRequest)
                            .filter { it.isValid() }
                            .onEach {
                                LOCATION.value = it
                                lastUpdateTimestamp.set(SystemClock.elapsedRealtime())
                            }
                    }
                    .launchIn(this)
            }
        }
        config.requestEnableLocationSettingsOnLifecycle?.let {
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(it) {
                    requestEnableLocationSettings()
                }
            }
        }
        config.requestLocationPermissionOnLifecycle?.let {
            owner.lifecycleScope.launch {
                owner.repeatOnLifecycle(it) {
                    requestLocationPermissions()
                }
            }
        }
    }

    override suspend fun requestLocationPermissions() {
        val status = checkLocationPermissionsAllowed()
        val alreadyAllowed = status == LocationFetcher.PermissionStatus.ALLOWED
        if (alreadyAllowed) {
            updatePermissionsStatusFlow(status)
        } else {
            requestPermissions()
        }
    }

    override suspend fun requestEnableLocationSettings() {
        logd("checkSettings")
        val request =
            LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build()
        val taskResult =
            apiHolder
                .filterNotNull()
                .first()
                .settingsClient
                .checkLocationSettings(request)
                .awaitTaskResult()
        try {
            taskResult.getResult(ApiException::class.java)
            // All location settings are satisfied. The client can initialize location
            logd("checkSettings: Satisfied.")
            updateSettingsStatusFlow(LocationFetcher.SettingsStatus.ENABLED)
            return
        } catch (e: ApiException) {
            logd("checkSettings: Not satisfied. Status code: ${e.statusCode}.", e)
            when (e.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                    logd("checkSettings: Resolution required")
                    // Cast to a resolvable exception.
                    val resolvable = e as ResolvableApiException
                    logd("checkSettings: Resolution possible with $resolutionResolver.")
                    val req = IntentSenderRequest.Builder(resolvable.resolution).build()
                    resolutionResolver?.launch(req)
                    return
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
    }

    private fun updatePermissionsStatusFlow(permission: LocationFetcher.PermissionStatus) {
        PERMISSION_STATUS.value = permission
    }

    private fun updateSettingsStatusFlow(permission: LocationFetcher.SettingsStatus) {
        SETTINGS_STATUS.value = permission
    }

    private suspend fun checkLocationPermissionsAllowed(): LocationFetcher.PermissionStatus =
        applicationContext.hasPermissions(LOCATION_PERMISSIONS).also {
            updatePermissionsStatusFlow(it)
        }

    private suspend fun checkLocationSettingsEnabled(): LocationFetcher.SettingsStatus {
        val request =
            LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build()
        val taskResult =
            apiHolder
                .filterNotNull()
                .first()
                .settingsClient
                .checkLocationSettings(request)
                .awaitTaskResult()
        return runCatching {
            taskResult.getResult(ApiException::class.java)
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
        suspendCancellableCoroutine<Task<out T>> { continuation ->
            runCatching {
                addOnCompleteListener { continuation.resume(it) }
            }.onFailure {
                continuation.resume(TaskCompletionSource<T>().withException(Exception(it)))
            }
        }

    private fun requestPermissions() {
        permissionRequester?.launch(LOCATION_PERMISSIONS)
    }

    private fun <T> TaskCompletionSource<T>.withException(e: Exception): Task<T> =
        apply { setException(e) }.task

    @Suppress("NOTHING_TO_INLINE")
    private inline fun logd(msg: String, e: Throwable? = null) {
        if (config.debug) Log.d(TAG, msg, e)
    }

    companion object {
        private const val TAG = "LocationFetcher"
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
