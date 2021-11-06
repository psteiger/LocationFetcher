package com.freelapp.libs.locationfetcher.impl.dsl

import com.google.android.gms.location.LocationSettingsRequest

internal fun locationSettingsRequest(
    block: LocationSettingsRequest.Builder.() -> Unit
): LocationSettingsRequest =
    LocationSettingsRequest.Builder().apply(block).build()