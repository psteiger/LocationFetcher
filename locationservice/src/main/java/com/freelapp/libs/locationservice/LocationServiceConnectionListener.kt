package com.freelapp.libs.locationservice

interface LocationServiceConnectionListener {
    fun onLocationServiceConnected()
    fun onLocationServiceDisconnected()
}