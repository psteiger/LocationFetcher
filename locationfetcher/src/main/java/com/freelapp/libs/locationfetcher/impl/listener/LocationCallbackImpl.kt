package com.freelapp.libs.locationfetcher.impl.listener

import android.location.Location
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult

internal class LocationCallbackImpl(private val callback: (Location) -> Unit) : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
        callback(locationResult.lastLocation)
    }

    override fun onLocationAvailability(locationAvailability: LocationAvailability) {
    }
}