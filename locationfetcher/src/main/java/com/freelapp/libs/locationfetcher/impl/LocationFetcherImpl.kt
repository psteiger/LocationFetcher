package com.freelapp.libs.locationfetcher.impl

import android.Manifest
import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import arrow.core.*
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.entity.ApiHolder
import com.freelapp.libs.locationfetcher.impl.entity.createDataSources
import com.freelapp.libs.locationfetcher.impl.entity.invoke
import com.freelapp.libs.locationfetcher.impl.util.asLocationFlow
import com.freelapp.libs.locationfetcher.impl.util.asValidatedNelFlow
import com.freelapp.libs.locationfetcher.impl.singleton.LOCATION
import com.freelapp.libs.locationfetcher.impl.singleton.PERMISSION_STATUS
import com.freelapp.libs.locationfetcher.impl.singleton.SETTINGS_STATUS
import com.freelapp.libs.locationfetcher.impl.singleton.TAG
import com.freelapp.libs.locationfetcher.impl.util.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

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
    private val resolutionResolver: ResolutionResolver? by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        (owner as? ComponentActivity)?.resolutionResolver { activityResult ->
            val resolved = Activity.RESULT_OK == activityResult.resultCode
            logd("Got setting resolution result $resolved")
            SETTINGS_STATUS.tryEmit(resolved)
        }
    }
    private val permissionRequester: PermissionRequester? by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        (owner as? ComponentActivity)?.permissionRequester { map ->
            logd("Got permission result map $map")
            val result = map.values.all { it }
            PERMISSION_STATUS.tryEmit(result)
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
    override val location = LOCATION.asSharedFlow()
    override val permissionStatus = PERMISSION_STATUS.asSharedFlow()
    override val settingsStatus = SETTINGS_STATUS.asSharedFlow()
    private val lastUpdateTimestamp = AtomicLong(0L)

    init {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val perms = PERMISSION_STATUS
                    .onSubscription { requestLocationPermissions() }
                    .asValidatedNelFlow(LocationFetcher.Error.PermissionDenied)
                val settings = SETTINGS_STATUS
                    .onSubscription { requestEnableLocationSettings() }
                    .asValidatedNelFlow(LocationFetcher.Error.SettingDisabled)
                perms
                    .combine(settings) { perm, setting -> perm to setting }
                    .mapLatest { (perm, setting) -> perm.zip(setting) { _, _ -> None } }
                    .flatMapLatest { validatedNel ->
                        validatedNel.fold(
                            { errors -> flowOf(errors.invalid()) },
                            { _ ->
                                apiHolder
                                    .onEach { logd("apiHolder=$it") }
                                    .filterNotNull()
                                    .flatMapLatest {
                                        config.providers.asLocationFlow(it, locationRequest)
                                    }
                                    .filter { it.isValid() }
                                    .onEach { lastUpdateTimestamp.set(SystemClock.elapsedRealtime()) }
                                    .map { it.validNel() }
                            }
                        )
                    }
                    .onEach { logd("location=$it") }
                    .onEach { LOCATION.tryEmit(it.toEither()) }
                    .launchIn(this)
            }
        }
    }

    override suspend fun requestLocationPermissions() {
        val allowed = checkLocationPermissionsAllowed()
        logd("requestLocationPermissions: allowed=$allowed")
        if (allowed) {
            PERMISSION_STATUS.tryEmit(allowed)
        } else {
            requestPermissions()
        }
    }

    override suspend fun requestEnableLocationSettings() {
        logd("requestEnableLocationSettings")
        apiHolder { locationRequest.isSatisfiedBySettings() }?.let {
            when (it.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                    logd("requestEnableLocationSettings: Resolution required")
                    // Cast to a resolvable exception.
                    val resolvable = it as ResolvableApiException
                    logd("requestEnableLocationSettings: Resolution possible with $resolutionResolver.")
                    val request = IntentSenderRequest.Builder(resolvable.resolution).build()
                    resolutionResolver?.launch(request)
                    return
                } catch (e: Throwable) {
                    logd("requestEnableLocationSettings: Exception", e)
                    SETTINGS_STATUS.tryEmit(false)
                }
                else -> SETTINGS_STATUS.tryEmit(false)
            }
        } ?: run {
            logd("requestEnableLocationSettings: Settings satisfied")
            SETTINGS_STATUS.tryEmit(true)
        }
    }

    private suspend fun checkLocationPermissionsAllowed(): Boolean =
        applicationContext.hasPermissions(LOCATION_PERMISSIONS).also {
            PERMISSION_STATUS.tryEmit(it)
        }

    private fun Location.isValid(): Boolean {
        val lastLocation = location.replayCache.lastOrNull()?.orNull() ?: return true
        val interval = locationRequest.interval
        val timeElapsed = SystemClock.elapsedRealtime() - lastUpdateTimestamp.get()
        val displacement = distanceTo(lastLocation)
        val smallestDisplacement = locationRequest.smallestDisplacement
        val isValid = timeElapsed > interval && displacement > smallestDisplacement
        logd(
            "isValid: lastLocation=$lastLocation, newLocation=$this" +
                    ", interval=$interval, timeElapsed=$timeElapsed" +
                    ", displacement=$displacement, smallestDisplacement=$smallestDisplacement" +
                    ", isValid=$isValid"
        )
        return isValid
    }

    private fun requestPermissions() {
        permissionRequester?.launch(LOCATION_PERMISSIONS)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun logd(msg: String, e: Throwable? = null) {
        if (config.debug) Log.d(TAG, msg, e)
    }

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}