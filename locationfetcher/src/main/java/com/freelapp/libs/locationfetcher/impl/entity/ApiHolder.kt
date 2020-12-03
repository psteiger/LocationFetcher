package com.freelapp.libs.locationfetcher.impl.entity

import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.freelapp.libs.locationfetcher.impl.LocationFetcherImpl
import com.freelapp.libs.locationfetcher.impl.util.PermissionRequester
import com.freelapp.libs.locationfetcher.impl.util.ResolutionResolver
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.SettingsClient
import kotlinx.coroutines.ExperimentalCoroutinesApi

internal data class ApiHolder(
    val locationManager: LocationManager,
    val fusedLocationClient: FusedLocationProviderClient,
    val settingsClient: SettingsClient,
    val resolutionResolver: ResolutionResolver? = null,
    val permissionRequester: PermissionRequester? = null
) {
    companion object {
        @ExperimentalCoroutinesApi
        fun create(owner: LifecycleOwner, context: Context): ApiHolder =
            if (owner is FragmentActivity)
                owner.createDataSources()
            else context.createDataSources()

        @ExperimentalCoroutinesApi
        fun create(activity: FragmentActivity): ApiHolder = activity.createDataSources()

        @ExperimentalCoroutinesApi
        fun create(context: Context): ApiHolder = context.createDataSources()

        @ExperimentalCoroutinesApi
        private fun FragmentActivity.createDataSources() = ApiHolder(
            ContextCompat.getSystemService(this, LocationManager::class.java) as LocationManager,
            LocationServices.getFusedLocationProviderClient(this),
            LocationServices.getSettingsClient(this),
            ResolutionResolver(this),
            PermissionRequester(this, LocationFetcherImpl.LOCATION_PERMISSIONS)
        )

        @ExperimentalCoroutinesApi
        private fun Context.createDataSources() = ApiHolder(
            ContextCompat.getSystemService(this, LocationManager::class.java) as LocationManager,
            LocationServices.getFusedLocationProviderClient(this),
            LocationServices.getSettingsClient(this),
        )
    }
}