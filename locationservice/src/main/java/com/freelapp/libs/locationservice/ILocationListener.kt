package com.freelapp.libs.locationservice

import android.location.Location

interface ILocationListener {
    fun onLocationReceived(l: Location)
}