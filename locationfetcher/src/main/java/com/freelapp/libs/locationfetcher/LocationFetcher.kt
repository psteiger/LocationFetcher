package com.freelapp.libs.locationfetcher

import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.Lifecycle
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.flow.StateFlow

interface LocationFetcher {
    companion object {
        private val locationRequest = LocationRequest.create()
        private const val FUSED_PROVIDER = "fused"
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
        var requestLocationPermissionOnLifecycle: Lifecycle.State? = Lifecycle.State.STARTED,
        var requestEnableLocationSettingsOnLifecycle: Lifecycle.State? = Lifecycle.State.STARTED,
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
