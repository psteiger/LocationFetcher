package com.freelapp.libs.locationfetcher

import android.location.Location
import kotlinx.coroutines.flow.StateFlow

interface LocationSource {
    val realLocation: StateFlow<Location?>
    val location: StateFlow<Location?>
    var customLocation: Location?
    var locationSource: Source
    enum class Source { REAL, CUSTOM }
}
