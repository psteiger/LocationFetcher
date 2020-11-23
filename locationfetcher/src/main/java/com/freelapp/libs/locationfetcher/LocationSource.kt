package com.freelapp.libs.locationfetcher

import android.location.Location
import com.freelapp.libs.locationfetcher.impl.LocationSourceImpl
import kotlinx.coroutines.flow.SharedFlow

interface LocationSource {
    companion object {
        fun create(
            locationFetcher: LocationFetcher
        ): LocationSource = LocationSourceImpl(locationFetcher)
    }

    val realLocation: SharedFlow<Location?>
    val location: SharedFlow<Location?>
    fun setCustomLocation(location: Location)
    fun setPreferredSource(source: Source)
    enum class Source { REAL, CUSTOM }
}
