package com.freelapp.libs.locationfetcher

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.activity.ComponentActivity
import com.freelapp.libs.locationfetcher.impl.LocationFetcherImpl
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

@ExperimentalCoroutinesApi
fun ComponentActivity.locationFetcher(
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher = LocationFetcher.create(this@locationFetcher, LocationFetcher.Config().apply(config))

@ExperimentalCoroutinesApi
fun Context.locationFetcher(
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher = LocationFetcher.create(this@locationFetcher, LocationFetcher.Config().apply(config))

interface LocationFetcher {

    companion object {
        private val locationRequest = LocationRequest.create()
        private const val FUSED_PROVIDER = "fused"

        @ExperimentalCoroutinesApi
        fun create(
            activity: ComponentActivity,
            config: Config
        ): LocationFetcher = LocationFetcherImpl(activity, config.copy())

        @ExperimentalCoroutinesApi
        fun create(
            context: Context,
            config: Config
        ): LocationFetcher = LocationFetcherImpl(context, config.copy())

        @ExperimentalCoroutinesApi
        fun create(
            activity: ComponentActivity,
            config: Config.() -> Unit = { }
        ): LocationFetcher = create(activity, Config().apply(config))

        @ExperimentalCoroutinesApi
        fun create(
            context: Context,
            config: Config.() -> Unit = { }
        ): LocationFetcher = create(context, Config().apply(config))
    }

    val location: StateFlow<Location?>
    val permissionStatus: StateFlow<PermissionStatus>
    val settingsStatus: StateFlow<SettingsStatus>

    suspend fun requestLocationPermissions()
    suspend fun requestEnableLocationSettings()

    data class Config(
        var fastestInterval: Long = locationRequest.fastestInterval,
        var interval: Long = locationRequest.interval,
        var maxWaitTime: Long = locationRequest.maxWaitTime,
        var priority: Int = locationRequest.priority,
        var smallestDisplacement: Float = locationRequest.smallestDisplacement,
        var numUpdates: Int = locationRequest.numUpdates,
        var isWaitForAccurateLocation: Boolean = locationRequest.isWaitForAccurateLocation,
        var providers: List<Provider> = listOf(Provider.Fused, Provider.Network, Provider.GPS),
        var requestLocationPermissions: Boolean = true,
        var requestEnableLocationSettings: Boolean = true,
        var debug: Boolean = false
    )

    sealed class Provider(val value: String) {
        object GPS : Provider(LocationManager.GPS_PROVIDER)
        object Network : Provider(LocationManager.NETWORK_PROVIDER)
        object Fused : Provider(FUSED_PROVIDER)
    }

    enum class PermissionStatus { UNKNOWN, ALLOWED, DENIED }

    enum class SettingsStatus { UNKNOWN, ENABLED, DISABLED }
}
