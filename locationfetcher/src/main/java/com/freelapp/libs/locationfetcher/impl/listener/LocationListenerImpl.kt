package com.freelapp.libs.locationfetcher.impl.listener

import android.location.Location
import android.location.LocationListener
import android.os.Bundle

internal class LocationListenerImpl(private val callback: (Location) -> Unit) : LocationListener {
    override fun onLocationChanged(location: Location) {
        callback(location)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // For compatibility with API < Q
    }

    override fun onProviderDisabled(provider: String) {
    }

    override fun onProviderEnabled(provider: String) {
    }
}