package com.freelapp.libs.locationfetcher

import android.location.Location
import arrow.core.Either
import arrow.core.Nel
import kotlinx.coroutines.flow.SharedFlow

public interface LocationSource {
    public val realLocation: SharedFlow<Either<Nel<LocationFetcher.Error>, Location>>
    public val location: SharedFlow<Either<Nel<LocationFetcher.Error>, Location>>
    public var customLocation: Location?
    public var locationSource: Source
    public enum class Source { REAL, CUSTOM }
}
