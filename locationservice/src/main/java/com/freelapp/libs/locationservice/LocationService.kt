package com.freelapp.libs.locationservice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.LocationSource
import com.google.firebase.auth.FirebaseAuth

class LocationService : Service(), LocationSource {

    companion object {
        var WAIT_FOR_FIREBASE_AUTH = false
    }

    private var mapListener: LocationSource.OnLocationChangedListener? = null

    private var currentLocation: Location? = null
        set(value) {
            value?.let {
                logd("Setting location to $it")
                field = value
                stopRequestingLocationUpdates()
                broadcastLocation()
            }
        }
    private var requestingLocationUpdates = false
    private val locationChangedListeners = mutableSetOf<ILocationListener>()
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this@LocationService) }
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(p0: Location?) = p0?.let {
            currentLocation = it
            logd("Location listener got location $it")
        } ?: Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String?) = Unit
        override fun onProviderDisabled(provider: String?) = Unit
    }
    private val gpsLocationManagerListener = locationListener
    private val networkLocationManagerListener = locationListener
    private val fusedLocationClientRequest = LocationRequest.create()
    private val fusedLocationClientCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            currentLocation = locationResult.lastLocation
        }
    }
    private val binder = LocalBinder()


    /**
     * end vars
     */

    /* update whoever registers about location changes */
    fun addLocationListener(listener: ILocationListener) {
        logd("Adding location listener $listener")
        locationChangedListeners.add(listener)
        broadcastLocation()
    }

    fun removeLocationListener(listener: ILocationListener) {
        logd("Removing location listener $listener")
        locationChangedListeners.remove(listener)
    }

    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        mapListener = listener
    }

    override fun deactivate() = Unit

    override fun onCreate() {
        super.onCreate()

        logd("Creating Location Service")

        if (WAIT_FOR_FIREBASE_AUTH) {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                logd("Adding Firebase Auth State listener $auth")
                if (auth.currentUser != null) {
                    logd("User authenticated: ${auth.currentUser}")
                    fetchLocation()
                }
            }
        } else {
            fetchLocation()
        }
    }

    private fun fetchLocation() {
        startRequestingLocationUpdates()
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    logd("Fused location client got location $it")
                    currentLocation = it
                } // In some rare situations this can be null
            }
        } catch (e: SecurityException) { /* should never happen */
        }
    }
    // continue running until it is explicitly stopped: return sticky
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int) = Service.START_NOT_STICKY

    override fun onBind(intent: Intent) = binder

    /**
     * Location methods
     */
    private fun broadcastLocation() = currentLocation?.let { location ->
        logd("Broadcasting lcoation $location to listeners")
        locationChangedListeners.forEach {
            logd("Broadcasting location $location to listener $it")
            it.onLocationReceived(location)
        }

        mapListener?.run {
            logd("Broadcasting location $location to map listener $this")
            onLocationChanged(location)
        }
    }


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: LocationService
            get() = this@LocationService
    }

    private fun stopRequestingLocationUpdates() {
        logd("Stop requesting location updates")
        requestingLocationUpdates = false
        try {
            locationManager.removeUpdates(gpsLocationManagerListener)
        } catch (_: Exception) {}

        try {
            locationManager.removeUpdates(networkLocationManagerListener)
        } catch (_: Exception) {}

        try {
            fusedLocationClient.removeLocationUpdates(fusedLocationClientCallback)
        } catch (_: Exception) {}
    }

    private fun startRequestingLocationUpdates() {
        if (!requestingLocationUpdates) {
            logd("Start requesting location updates")

            requestingLocationUpdates = true

            // can happen: SecurityException (permission) or IllegalArgumentException: 'provider doesn't exist: gps'
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, gpsLocationManagerListener)
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {} /* provider doesn't exist: gps */

            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, networkLocationManagerListener)
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {}

            try {
                fusedLocationClient.requestLocationUpdates(fusedLocationClientRequest, fusedLocationClientCallback, null)
            } catch (_: SecurityException) { /* should never happen */ }
        }
    }

    private fun logd(msg: String) {
        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, msg)
    }
}
