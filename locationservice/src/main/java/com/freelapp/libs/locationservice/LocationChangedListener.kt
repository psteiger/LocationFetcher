package com.freelapp.libs.locationservice

import android.location.Location

interface LocationChangedListener {
    fun onLocationReceived(l: Location)
}