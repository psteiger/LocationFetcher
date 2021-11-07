package com.freelapp.libs.locationfetcher

import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.Lifecycle
import arrow.core.Either
import arrow.core.Nel
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.flow.SharedFlow

public interface LocationFetcher {
    private companion object {
        private val locationRequest = LocationRequest.create()
        private const val FUSED_PROVIDER = "fused"
    }

    public val location: SharedFlow<Either<Nel<Error>, Location>>
    public val permissionStatus: SharedFlow<Boolean>
    public val settingsStatus: SharedFlow<Boolean>

    public suspend fun requestLocationPermissions()
    public suspend fun requestEnableLocationSettings()
    public fun shouldShowRationale(): Boolean

    public data class Config(
        var rationale: String,
        var fastestInterval: Long = locationRequest.fastestInterval,
        var interval: Long = locationRequest.interval,
        var maxWaitTime: Long = locationRequest.maxWaitTime,
        var priority: Int = locationRequest.priority,
        var smallestDisplacement: Float = locationRequest.smallestDisplacement,
        var numUpdates: Int = locationRequest.numUpdates,
        var isWaitForAccurateLocation: Boolean = locationRequest.isWaitForAccurateLocation,
        var providers: List<Provider> = listOf(Provider.Fused, Provider.Network, Provider.GPS),
        var debug: Boolean = false
    )

    public sealed class Provider(public val value: String) {
        public object GPS : Provider(LocationManager.GPS_PROVIDER)
        public object Network : Provider(LocationManager.NETWORK_PROVIDER)
        public object Fused : Provider(FUSED_PROVIDER)
    }

    public sealed class Error {
        public object PermissionDenied : Error()
        public object SettingDisabled : Error()
    }

    @Deprecated("Replaced by LocationFetcher.Error.SettingDisabled")
    public enum class PermissionStatus { UNKNOWN, ALLOWED, DENIED }

    @Deprecated("Replaced by LocationFetcher.Error.SettingDisabled")
    public enum class SettingsStatus { UNKNOWN, ENABLED, DISABLED }
}
