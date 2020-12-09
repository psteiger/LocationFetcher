package com.freelapp.libs.locationservice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.LocationSource
import com.google.firebase.auth.FirebaseAuth

class LocationService : Service(), LocationSource {

    companion object {
        var waitForFirebaseAuth: Boolean = false
        var locationRequest: LocationRequest = LocationRequest.create()
        var debug: Boolean = false
    }

    private var lastUpdate: Long = 0
    private var gotUpdates: Int = 0
    private var mapListener: LocationSource.OnLocationChangedListener? = null
    private var currentLocation: Location? = null
        set(value) {
            value?.let {
                val interval = locationRequest.interval
                val timeElapsed = SystemClock.elapsedRealtime() - lastUpdate
                val displacement = getDisplacement(it)
                val smallestDisplacement = locationRequest.smallestDisplacement
                logd("Got location $it")
                logd("Location update interval is $interval. Time since last update: $timeElapsed")
                logd("Displacement is $displacement, smallest displacement is $smallestDisplacement")
                if (currentLocation == null || timeElapsed > interval && getDisplacement(it) > smallestDisplacement) {
                    logd("Setting location to $it")

                    field = value
                    lastUpdate = SystemClock.elapsedRealtime()

                    if (locationRequest.numUpdates == ++gotUpdates) stopRequestingLocationUpdates()

                    broadcastLocation()
                } else {
                    logd("Ignoring location update.")
                }
            }
        }
    private var requestingLocationUpdates = false
    private val locationChangedListeners = mutableSetOf<LocationChangeListener>()
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this@LocationService) }
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(p0: Location?) = p0?.let {
            currentLocation = it
        } ?: Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String?) = Unit
        override fun onProviderDisabled(provider: String?) = Unit
    }
    private val gpsLocationManagerListener = locationListener
    private val networkLocationManagerListener = locationListener
    private val fusedLocationClientRequest = locationRequest
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

    /**
     * Overrides
     */
    override fun onCreate() {
        super.onCreate()

        logd("Creating Location Service")

        if (waitForFirebaseAuth) {
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

    // continue running until it is explicitly stopped: return sticky
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onBind(intent: Intent) = binder

    /**
     * Updates whoever registers about location changes.
     */
    fun addLocationListener(listener: LocationChangeListener) {
        logd("Adding location listener $listener")
        locationChangedListeners.add(listener)
        broadcastLocation()
    }

    fun removeLocationListener(listener: LocationChangeListener) {
        logd("Removing location listener $listener")
        locationChangedListeners.remove(listener)
    }

    /**
     * Google Maps needs a listener of its own type.
     * Activates or deactivates map listener.
     */
    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        mapListener = listener
    }

    override fun deactivate() = Unit

    /**
     * Location methods
     */
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

    fun broadcastLocation() = currentLocation?.let { location ->
        logd("Broadcasting location $location to listeners")
        locationChangedListeners.forEach {
            logd("Broadcasting location $location to listener $it")
            it.onLocationReceived(location)
        }

        mapListener?.run {
            logd("Broadcasting location $location to map listener $this")
            onLocationChanged(location)
        }
    }

    fun stopRequestingLocationUpdates() {
        if (requestingLocationUpdates) {
            logd("Stop requesting location updates")

            requestingLocationUpdates = false
            try {
                locationManager.removeUpdates(gpsLocationManagerListener)
            } catch (_: Throwable) {
            }

            try {
                locationManager.removeUpdates(networkLocationManagerListener)
            } catch (_: Throwable) {
            }

            try {
                fusedLocationClient.removeLocationUpdates(fusedLocationClientCallback)
            } catch (_: Throwable) {
            }
        }
    }

    fun startRequestingLocationUpdates() {
        if (!requestingLocationUpdates) {
            logd("Start requesting location updates")

            requestingLocationUpdates = true

            // can happen: SecurityException (permission) or IllegalArgumentException: 'provider doesn't exist: gps'
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    locationRequest.interval,
                    locationRequest.smallestDisplacement,
                    gpsLocationManagerListener
                )
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {} // provider doesn't exist: gps

            try {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    locationRequest.interval,
                    locationRequest.smallestDisplacement,
                    networkLocationManagerListener
                )
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {} // provider doesn't exist: network

            try {
                fusedLocationClient.requestLocationUpdates(fusedLocationClientRequest, fusedLocationClientCallback, null)
            } catch (_: SecurityException) {} // should never happen
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

    private fun getDisplacement(l: Location): Float = currentLocation?.run {
        val result = FloatArray(1)
        Location.distanceBetween(latitude, longitude, l.latitude, l.longitude, result)
        result[0]
    } ?: Float.MAX_VALUE

    private fun logd(msg: String) {
        if (debug) Log.d("LocationService", msg)
    }
}
