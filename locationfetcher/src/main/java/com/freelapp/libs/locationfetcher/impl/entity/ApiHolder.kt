package com.freelapp.libs.locationfetcher.impl.entity

import android.content.Context
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.freelapp.libs.locationfetcher.impl.dsl.locationSettingsRequest
import com.freelapp.libs.locationfetcher.impl.util.awaitComplete
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

internal suspend inline operator fun <T> Flow<ApiHolder?>.invoke(block: ApiHolder.() -> T): T =
    filterNotNull().first().block()

internal fun LifecycleOwner.createDataSources(context: Context): ApiHolder =
    when (this) {
        is ComponentActivity -> createDataSources()
        is Fragment -> requireActivity().createDataSources()
        else -> context.createDataSources()
    }

internal fun ComponentActivity.createDataSources(): ApiHolder =
    ApiHolder(
        ContextCompat.getSystemService(this, LocationManager::class.java) as LocationManager,
        LocationServices.getFusedLocationProviderClient(this),
        LocationServices.getSettingsClient(this)
    )

internal fun Context.createDataSources(): ApiHolder =
    ApiHolder(
        ContextCompat.getSystemService(this, LocationManager::class.java) as LocationManager,
        LocationServices.getFusedLocationProviderClient(this),
        LocationServices.getSettingsClient(this)
    )

internal data class ApiHolder(
    val locationManager: LocationManager,
    val fusedLocationClient: FusedLocationProviderClient,
    val settingsClient: SettingsClient
) {
    suspend fun LocationRequest.isSatisfiedBySettings(): ApiException? {
        val request = locationSettingsRequest { addLocationRequest(this@isSatisfiedBySettings) }
        return try {
            settingsClient.checkLocationSettings(request)
                .awaitComplete()
                .getResult(ApiException::class.java)
            null
        } catch (e: ApiException) {
            e
        }
    }
}