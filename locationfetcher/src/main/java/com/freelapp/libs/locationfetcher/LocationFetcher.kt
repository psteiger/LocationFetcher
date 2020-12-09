package com.freelapp.libs.locationfetcher

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.fragment.app.FragmentActivity
import com.freelapp.libs.locationfetcher.impl.LocationFetcherImpl
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.flow.StateFlow

interface LocationFetcher {

    companion object {
        private val locationRequest = LocationRequest()
        private const val FUSED_PROVIDER = "fused"

        fun create(
            activity: FragmentActivity,
            config: Config.() -> Unit = { }
        ): LocationFetcher = LocationFetcherImpl(activity, Config().apply { config() })

        fun create(
            context: Context,
            config: Config.() -> Unit = { }
        ): LocationFetcher = LocationFetcherImpl(context, Config().apply { config() })
    }

    val location: StateFlow<Location?>
    val permissionStatus: StateFlow<PermissionStatus>
    val settingsStatus: StateFlow<SettingsStatus>

    suspend fun requestLocationPermissions(): PermissionStatus
    suspend fun requestEnableLocationSettings(): SettingsStatus

    data class Config(
        var fastestInterval: Long = locationRequest.fastestInterval,
        var interval: Long = locationRequest.interval,
        var maxWaitTime: Long = locationRequest.maxWaitTime,
        var priority: Int = locationRequest.priority,
        var smallestDisplacement: Float = locationRequest.smallestDisplacement,
        var numUpdates: Int = locationRequest.numUpdates,
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
