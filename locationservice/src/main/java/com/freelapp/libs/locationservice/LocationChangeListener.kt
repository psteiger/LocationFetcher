package com.freelapp.libs.locationservice

import android.location.Location

interface LocationChangeListener {
    fun onLocationReceived(l: Location)
}