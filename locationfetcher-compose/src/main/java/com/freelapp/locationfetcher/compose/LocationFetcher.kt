package com.freelapp.locationfetcher.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult

@Immutable
public data class LocationState(
    val locationResult: LocationResult?,
    val settingEnabled: Boolean?,
    val permissionsGranted: Boolean
)

/**
 * Obtain periodic location updates from the device. The [LocationResult] is delivered to all
 * children composable by [LocalLocationFetcher.current].
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
 *         val (locationResult, settingEnabled, permissionsGranted) = LocalLocationFetcher.current
 *         when (locationResult) {
 *             null -> MissingLocation()
 *             else -> NearbyRestaurants(locationResult)
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
    val rationaleUi: RationaleUi = remember(rationale) {
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
 * Obtain periodic location updates from the device. The [LocationResult] is delivered to all
 * children composable by [LocalLocationFetcher.current].
 *
 * [LocationRequest] configurations can be set by [requestConfig].
 *
 * A custom rationale UI will be used to present the user a rationale for requesting the location
 * permissions. **The rationale UI must call the `onRationaleDismissed` callback once user dismisses
 * the UI**. The callback will be the trigger for actually asking for the location permissions.
 *
 * If you prefer to use a predefined rationale UI instead, see the overload that takes in a
 * rationale String instead of a rationale UI.
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
 *         val (locationResult, settingEnabled, permissionsGranted) = LocalLocationFetcher.current
 *         when (locationResult) {
 *             null -> MissingLocation()
 *             else -> NearbyRestaurants(locationResult)
 *         }
 *     }
 * }
 * ```
 */
@Composable
public fun LocationFetcher(
    requestConfig: LocationRequest.() -> Unit = {},
    rationaleUi: RationaleUi,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val state = remember { LocationContext(context.hasLocationPermissions()) }
    PermissionRequest(
        permissionsGranted = state.permissionsGranted,
        setPermissionsGranted = state::updatePermissionsGranted,
        rationaleDismissed = state.rationaleDismissed
    ) {
        rationaleUi(
            onRationaleDismissed = state::rationaleDismissed
        )
    }
    if (state.permissionsGranted) {
        val locationRequest = rememberLocationRequest(requestConfig)
        SettingsRequest(
            locationRequest = locationRequest,
            setSettingEnabled = state::updateSettingEnabled
        )
        LocationProvider(
            locationRequest = locationRequest,
            setLocation = state::updateLocation
        )
    }
    val locationState by derivedStateOf {
        LocationState(
            locationResult = state.location?.locationResult,
            settingEnabled = state.settingEnabled,
            permissionsGranted = state.permissionsGranted
        )
    }
    CompositionLocalProvider(
        LocalLocationFetcher provides locationState,
        content = content
    )
}

public object LocalLocationFetcher {
    private val LocalLocationFetcher =
        compositionLocalOf<LocationState> {
            throw IllegalStateException("Can only be called through LocationFetcher")
        }

    public val current: LocationState
        @Composable
        get() = LocalLocationFetcher.current

    internal infix fun provides(
        location: LocationState
    ): ProvidedValue<LocationState> =
        LocalLocationFetcher provides location
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
private fun rememberLocationRequest(requestConfig: LocationRequest.() -> Unit) =
    remember(requestConfig) {
        LocationRequestWrapper(LocationRequest.create().apply(requestConfig))
    }

@Stable
private class LocationContext(permissionsGranted: Boolean) {
    var location by mutableStateOf<LocationResultWrapper?>(null)
        private set
    var permissionsGranted by mutableStateOf(permissionsGranted)
        private set
    var settingEnabled by mutableStateOf<Boolean?>(null)
        private set
    var rationaleDismissed by mutableStateOf(false)
        private set

    fun updatePermissionsGranted(granted: Boolean) {
        rationaleDismissed = false
        permissionsGranted = granted
    }

    fun updateSettingEnabled(enabled: Boolean) {
        settingEnabled = enabled
    }

    fun updateLocation(location: LocationResultWrapper?) {
        this.location = location
    }

    fun rationaleDismissed() {
        rationaleDismissed = true
    }
}
