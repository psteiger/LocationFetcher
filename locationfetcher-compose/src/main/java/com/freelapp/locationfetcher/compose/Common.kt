package com.freelapp.locationfetcher.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Immutable
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult

internal tailrec fun Context.getActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

// Wrappers are for avoiding recomposition, as LocationRequest and LocationResult
// are inferred to be unstable.

@Immutable
internal data class LocationRequestWrapper(val locationRequest: LocationRequest)

@Immutable
internal data class LocationResultWrapper(val locationResult: LocationResult)
