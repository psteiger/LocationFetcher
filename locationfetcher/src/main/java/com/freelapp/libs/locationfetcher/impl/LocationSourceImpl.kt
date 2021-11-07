package com.freelapp.libs.locationfetcher.impl

import android.location.Location
import arrow.core.right
import com.freelapp.libs.locationfetcher.LocationFetcher
import com.freelapp.libs.locationfetcher.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

internal class LocationSourceImpl(
    scope: CoroutineScope,
    private val locationFetcher: LocationFetcher
) : LocationSource {

    private val _locationSource = MutableStateFlow(LocationSource.Source.REAL)
    override var locationSource: LocationSource.Source
        get() = _locationSource.value
        set(value) { _locationSource.value = value }

    private val _customLocation = MutableStateFlow<Location?>(null)
    override var customLocation: Location?
        get() = _customLocation.value
        set(value) { _customLocation.value = value }

    override val realLocation get() = locationFetcher.location

    override val location =
        _locationSource
            .flatMapLatest { source ->
                when (source) {
                    LocationSource.Source.REAL -> realLocation
                    LocationSource.Source.CUSTOM -> _customLocation.filterNotNull().map { it.right() }
                }
            }
            .shareIn(scope, SharingStarted.WhileSubscribed(), 1)
}