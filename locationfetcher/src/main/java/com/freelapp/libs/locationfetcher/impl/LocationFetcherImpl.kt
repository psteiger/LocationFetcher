package com.freelapp.libs.locationfetcher.impl

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import arrow.core.None
import arrow.core.invalid
import arrow.core.validNel
import arrow.core.zip
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.entity.createDataSources
import com.freelapp.libs.locationfetcher.impl.entity.invoke
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

internal class LocationFetcherImpl private constructor(
    owner: LifecycleOwner,
    private val applicationContext: Lazy<Context>,
    private val config: Lazy<LocationFetcher.Config>,
) : LocationFetcher {

    constructor(
        activity: ComponentActivity,
        config: Lazy<LocationFetcher.Config>
    ) : this(
        activity,
        lazy { activity.applicationContext },
        config
    )

    constructor(
        fragment: Fragment,
        config: Lazy<LocationFetcher.Config>
    ) : this(
        fragment,
        lazy { fragment.requireContext().applicationContext },
        config
    )

    constructor(
        context: Context,
        owner: LifecycleOwner,
        config: Lazy<LocationFetcher.Config>
    ) : this(
        owner,
        lazy { context.applicationContext },
        config
    )

    override val location = LOCATION.asSharedFlow()
    override val permissionStatus = PERMISSION_STATUS.asSharedFlow()
    override val settingsStatus = SETTINGS_STATUS.asSharedFlow()

    private val apiHolder = owner.lifecycleMutableStateFlow(Lifecycle.State.CREATED) {
        it.createDataSources(applicationContext.value)
    }
    private val resolutionResolver by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        val resolver = when (owner) {
            is ComponentActivity -> owner::resolutionResolver
            is Fragment -> owner::resolutionResolver
            else -> null
        }
        resolver?.invoke { activityResult ->
            val resolved = Activity.RESULT_OK == activityResult.resultCode
            logd("Got setting resolution result $resolved")
            SETTINGS_STATUS.tryEmit(resolved)
        }
    }
    private val permissionRequester by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        val requester = when (owner) {
            is ComponentActivity -> owner::permissionRequester
            is Fragment -> owner::permissionRequester
            else -> null
        }
        requester?.invoke { map ->
            logd("Got permission result map $map")
            checkLocationPermissionsAllowed()
        }
    }
    private val shouldShowPermissionRationale by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        {
            LOCATION_PERMISSIONS.any {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val rationaleFunction = when (owner) {
                        is ComponentActivity -> owner::shouldShowRequestPermissionRationale
                        is Fragment -> owner::shouldShowRequestPermissionRationale
                        else -> null
                    }
                    rationaleFunction?.invoke(it) ?: false
                } else {
                    false
                }
            }
        }
    }
    private val rationaleDialogBuilder by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        val context = when (owner) {
            is Activity -> owner
            is Fragment -> owner.requireContext()
            else -> null
        }
        context?.let {
            AlertDialog.Builder(it).setMessage(config.value.rationale)
        }
    }
    private val locationRequest by lazy {
        LocationRequest.create().apply {
            fastestInterval = config.value.fastestInterval
            interval = config.value.interval
            maxWaitTime = config.value.maxWaitTime
            priority = config.value.priority
            smallestDisplacement = config.value.smallestDisplacement
            numUpdates = config.value.numUpdates
            isWaitForAccurateLocation = config.value.isWaitForAccurateLocation
        }
    }
    private val lastUpdateTimestamp = AtomicLong(0L)

    init {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val perms = PERMISSION_STATUS
                    .onSubscription {
                        if (!checkLocationPermissionsAllowed()) {
                            if (shouldShowRationale()) showRationale()
                            requestLocationPermissions()
                        }
                    }
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
                                        config.value.providers.asLocationFlow(it, locationRequest)
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
        requestPermissions()
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

    private fun checkLocationPermissionsAllowed(): Boolean =
        applicationContext.value.hasPermissions(LOCATION_PERMISSIONS).also {
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

    override fun shouldShowRationale(): Boolean =
        shouldShowPermissionRationale?.invoke() ?: false

    private suspend fun showRationale() = rationaleDialogBuilder?.let { builder ->
        suspendCancellableCoroutine<Unit> { cont ->
            val dialog = builder
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .create()
                .apply { setOnDismissListener { cont.resume(Unit) } }
                .also { it.show() }
            cont.invokeOnCancellation {
                dialog.dismiss()
            }
        }
    } ?: false

    @Suppress("NOTHING_TO_INLINE")
    private inline fun logd(msg: String, e: Throwable? = null) {
        if (config.value.debug) Log.d(TAG, msg, e)
    }

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}