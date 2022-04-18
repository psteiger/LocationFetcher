package com.freelapp.libs.locationfetcher.impl

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import arrow.core.*
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.impl.entity.createDataSources
import com.freelapp.libs.locationfetcher.impl.entity.invoke
import com.freelapp.libs.locationfetcher.impl.singleton.PERMISSION_STATUS
import com.freelapp.libs.locationfetcher.impl.singleton.SETTINGS_STATUS
import com.freelapp.libs.locationfetcher.impl.singleton.TAG
import com.freelapp.libs.locationfetcher.impl.util.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
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

    override val permissionStatus = PERMISSION_STATUS.asSharedFlow()
    override val settingsStatus = SETTINGS_STATUS.asSharedFlow()

    private val apiHolder = owner.lifecycleMutableStateFlow(Lifecycle.State.CREATED) {
        it.createDataSources(applicationContext.value)
    }
    private val resolutionResolver by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        owner.resolutionResolver { result ->
            val resolved = result.resolved
            logd("Got setting resolution result $resolved")
            SETTINGS_STATUS.tryEmit(resolved)
        }
    }
    private val permissionRequester by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        owner.permissionRequester { map ->
            logd("Got permission result map $map")
            checkLocationPermissionsAllowed()
        }
    }
    private val shouldShowPermissionRationale by owner.lifecycle(Lifecycle.State.CREATED) { owner ->
        {
            LOCATION_PERMISSIONS.any { permission ->
                owner.shouldShowRequestPermissionRationale(permission)
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
            config.value.fastestInterval?.let { fastestInterval = it.inWholeMilliseconds }
            config.value.interval?.let { interval = it.inWholeMilliseconds }
            config.value.maxWaitTime?.let { maxWaitTime = it.inWholeMilliseconds }
            config.value.priority?.let { priority = it }
            config.value.smallestDisplacement?.let { smallestDisplacement = it }
            config.value.numUpdates?.let { numUpdates = it }
            config.value.isWaitForAccurateLocation?.let { isWaitForAccurateLocation = it }
        }
    }

    override val location = run {
        val perms = PERMISSION_STATUS
            .onSubscription {
                if (!checkLocationPermissionsAllowed()) {
                    if (shouldShowRationale()) showRationale()
                    requestLocationPermissions()
                }
            }
            .asValidatedNelFlow(LocationFetcher.Error.PermissionDenied)
            .distinctUntilChanged()
        val settings = SETTINGS_STATUS
            .onSubscription { requestEnableLocationSettings() }
            .asValidatedNelFlow(LocationFetcher.Error.SettingDisabled)
            .distinctUntilChanged()
        combine(perms, settings, ::Pair)
            .mapLatest { (perm, setting) ->
                perm.zip(setting) { _, _ -> None }
                    .toEither()
            }
            .onEach { logd("Environmental issues (missing permission/disabled settings): $it") }
            .flatMapLatestRight {
                apiHolder.filterNotNull()
                    .onEach { logd("API holder: $it") }
                    .flatMapLatest { config.value.providers.asLocationFlow(it, locationRequest) }
                    .onEach { logd("Location: $it") }
            }
            .onCompletion { logd("Completing flow with exception=$it") }
            .flowWithLifecycle(owner.lifecycle, Lifecycle.State.STARTED)
            .shareIn(owner.lifecycleScope, SharingStarted.WhileSubscribed(), 1)
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

private fun LifecycleOwner.shouldShowRequestPermissionRationale(permission: String) =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> when (this) {
            is Activity -> shouldShowRequestPermissionRationale(permission)
            is Fragment -> shouldShowRequestPermissionRationale(permission)
            else -> false
        }
        else -> false
    }
