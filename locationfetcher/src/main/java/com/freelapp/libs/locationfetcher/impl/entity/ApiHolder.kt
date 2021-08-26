package com.freelapp.libs.locationfetcher.impl.entity

import android.content.Context
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.SettingsClient

internal fun LifecycleOwner.createDataSources(context: Context): ApiHolder =
    if (this is ComponentActivity) createDataSources() else context.createDataSources()

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
)