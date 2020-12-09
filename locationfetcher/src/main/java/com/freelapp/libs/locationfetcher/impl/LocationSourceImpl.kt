package com.freelapp.libs.locationfetcher.impl

import android.location.Location
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.LocationSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

internal class LocationSourceImpl(locationFetcher: LocationFetcher) : LocationSource {

    private val locationSource = MutableStateFlow(LocationSource.Source.REAL)
    private val _customLocation = MutableStateFlow<Location?>(null)
    private val customLocation: SharedFlow<Location?> =
        _customLocation
            .shareIn(
                ProcessLifecycleOwner.get().lifecycleScope,
                SharingStarted.WhileSubscribed(),
                1
            )

    override val realLocation: SharedFlow<Location?> =
        locationFetcher
            .location
            .shareIn(
                ProcessLifecycleOwner.get().lifecycleScope,
                SharingStarted.WhileSubscribed(),
                1
            )

    @ExperimentalCoroutinesApi
    override val location: SharedFlow<Location?> =
        locationSource
            .flatMapLatest {
                when (it) {
                    LocationSource.Source.REAL -> realLocation
                    LocationSource.Source.CUSTOM -> customLocation
                }
            }
            .shareIn(
                ProcessLifecycleOwner.get().lifecycleScope,
                SharingStarted.WhileSubscribed(),
                1
            )

    override fun setCustomLocation(location: Location) {
        _customLocation.value = location
    }

    override fun setPreferredSource(source: LocationSource.Source) {
        locationSource.value = source
    }
}