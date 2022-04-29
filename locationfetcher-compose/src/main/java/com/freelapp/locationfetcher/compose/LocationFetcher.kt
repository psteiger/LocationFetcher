package com.freelapp.locationfetcher.compose

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.*
import kotlinx.coroutines.awaitCancellation

/**
 * Obtain periodic location updates from the device. Location is delivered to all children
 * composable by [LocalLocationFetcher.current].
 *
 * [LocationRequest] configurations can be set by [requestConfig].
 *
 * A [Material3 AlertDialog][AlertDialog] will be used to show the permission [rationale] if needed.
 * If you prefer to use a custom composable instead, see the overload that takes
 * in a rationale UI instead of a rationale String.
 *
 * Example:
 *
 * ```
 * @Composable
 * fun NearbyRestaurants() {
 *     LocationFetcher(
 *         requestConfig = {
 *             interval = 15.seconds.inWholeMilliseconds
 *             fastestInterval = 15.seconds.inWholeMilliseconds
 *             priority = LocationRequest.PRIORITY_HIGH_ACCURACY
 *         },
 *         rationale = "We need your location for searching restaurants nearby."
 *     ) {
 *         val location = LocalLocationFetcher.current
 *         when (location) {
 *             null -> MissingLocation()
 *             else -> NearbyRestaurants(location)
 *         }
 *     }
 * }
 * ```
 */
@Composable
public fun LocationFetcher(
    requestConfig: LocationRequest.() -> Unit = {},
    rationale: String,
    content: @Composable () -> Unit
) {
    val rationaleUi: RationaleUi = remember {
        { onRationaleDismissed ->
            Rationale(
                text = rationale,
                onRationaleDismissed = onRationaleDismissed,
            )
        }
    }
    LocationFetcher(
        requestConfig = requestConfig,
        rationaleUi = rationaleUi,
        content = content,
    )
}

/**
 * Obtain periodic location updates from the device. Location is delivered to all children
 * composable by [LocalLocationFetcher.current].
 *
 * [LocationRequest] configurations can be set by [requestConfig].
 *
 * A custom rationale UI will be used to present the user a rationale for requesting the location
 * permissions. **The rationale UI must call the `onRationaleDismissed` callback once user dismisses
 * the UI**. The callback will be the trigger for actually asking for the location permissions.
 *
 * Example:
 *
 * ```
 * @Composable
 * fun NearbyRestaurants() {
 *     LocationFetcher(
 *         requestConfig = {
 *             interval = 15.seconds.inWholeMilliseconds
 *             fastestInterval = 15.seconds.inWholeMilliseconds
 *             priority = LocationRequest.PRIORITY_HIGH_ACCURACY
 *         },
 *         rationaleUi = { onRationaleDismissed ->
 *             LocationPermissionRationale(
 *                 onDismiss = onRationaleDismissed
 *             )
 *         },
 *     ) {
 *         val location = LocalLocationFetcher.current
 *         when (location) {
 *             null -> MissingLocation()
 *             else -> NearbyRestaurants(location)
 *         }
 *     }
 * }
 * ```
 *
 * If you prefer to use a custom composable instead, see the [LocationFetcher] overload that takes
 * in a rationale UI instead of a rationale String.
 */
@Composable
public fun LocationFetcher(
    requestConfig: LocationRequest.() -> Unit = {},
    rationaleUi: RationaleUi,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(context.hasLocationPermissions()) }
    var rationaleDismissed by remember { mutableStateOf(false) }
    val setHasPermissions: (Boolean) -> Unit = remember {
        {
            rationaleDismissed = false
            hasPermissions = it
        }
    }
    PermissionRequest(
        hasPermissions = hasPermissions,
        setHasPermissions = setHasPermissions,
        rationaleDismissed = rationaleDismissed,
        rationale = { rationaleUi { rationaleDismissed = true } },
    )
    if (hasPermissions) {
        val locationRequest = rememberLocationRequest(requestConfig)
        SettingsRequest(locationRequest)
        LocationProvider(locationRequest, content)
    }
}

public object LocalLocationFetcher {
    private val LocalLocationFetcher =
        compositionLocalOf<Location?> { null }

    public val current: Location?
        @Composable
        get() = LocalLocationFetcher.current

    internal infix fun provides(
        location: Location?
    ): ProvidedValue<Location?> =
        LocalLocationFetcher.provides(location)
}

public typealias RationaleUi = @Composable (onRationaleDismissed: () -> Unit) -> Unit

// ---- Implementation ----

@Composable
private fun Rationale(
    text: String,
    onRationaleDismissed: () -> Unit
) {
    AlertDialog(
        text = { Text(text = text) },
        onDismissRequest = onRationaleDismissed,
        confirmButton = {
            Button(onClick = onRationaleDismissed) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        }
    )
}

@Composable
private fun rememberLocationRequest(
    requestConfig: LocationRequest.() -> Unit
) = remember(requestConfig) {
    LocationRequest.create().apply(requestConfig)
}

@SuppressLint("MissingPermission")
@Composable
private fun LocationProvider(
    locationRequest: LocationRequest,
    content: @Composable () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val client = rememberFusedLocationClient()
    val location by produceState<Location?>(null, client, locationRequest, lifecycle) {
        val callback = locationCallback()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                awaitCancellation()
            } finally {
                client.removeLocationUpdates(callback)
            }
        }
    }
    CompositionLocalProvider(
        LocalLocationFetcher provides location,
        content = content
    )
}

@Composable
private fun rememberFusedLocationClient(): FusedLocationProviderClient {
    val context = LocalContext.current
    val activity = context.getActivity()
    return remember(context, activity) {
        when (activity) {
            null -> LocationServices.getFusedLocationProviderClient(context)
            else -> LocationServices.getFusedLocationProviderClient(activity)
        }
    }
}

private fun ProduceStateScope<Location?>.locationCallback() = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
        value = locationResult.lastLocation
    }

    override fun onLocationAvailability(locationAvailability: LocationAvailability) {
    }
}
