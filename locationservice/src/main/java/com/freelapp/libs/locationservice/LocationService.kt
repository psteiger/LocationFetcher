package com.freelapp.libs.locationservice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.LocationSource
import com.google.firebase.auth.FirebaseAuth
import java.lang.ref.WeakReference

class LocationService : Service(),
    LocationSource,
    LocationPermissionListener,
    LocationSettingsListener {

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
    private var requestingLocationUpdatesFromGps = false
    private var requestingLocationUpdatesFromNetwork = false
    private var requestingLocationUpdatesFromFusedLocationClient = false
    private val locationChangedListeners = mutableSetOf<WeakReference<LocationChangeListener>>()
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this@LocationService) }
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(p0: Location?) {
            p0?.let {
                currentLocation = it
            }
        }
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

    override fun onBind(intent: Intent?): IBinder? {
        logd("onBind $intent")
        startRequestingLocationUpdates()
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        logd("onUnbind $intent")
        stopRequestingLocationUpdates()
        return super.onUnbind(intent)
    }

    /**
     * Updates whoever registers about location changes.
     */
    fun addLocationListener(listener: LocationChangeListener) {
        logd("Adding location listener $listener")
        locationChangedListeners.addWeakRef(listener)
        broadcastLocation()
    }

    fun removeLocationListener(listener: LocationChangeListener) {
        logd("Removing location listener $listener")
        locationChangedListeners.removeWeakRef(listener)
    }

    /**
     * Google Maps needs a listener of its own type.
     * Activates or deactivates map listener.
     */
    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        logd("Activating LocationSource for map listener $listener")
        mapListener = listener
    }

    override fun deactivate() {
        logd("Activating LocationSource for map")
        mapListener = null
    }

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
        } catch (e: SecurityException) {
            loge("Fused location client failed to get location", e)
        }
    }

    fun broadcastLocation() = currentLocation?.let { location ->
        logd("Broadcasting location $location to listeners $locationChangedListeners")
        locationChangedListeners.forEach {
            logd("Broadcasting location $location to listener $it")
            it.get()?.onLocationReceived(location)
        }

        mapListener?.run {
            logd("Broadcasting location $location to map listener $this")
            onLocationChanged(location)
        }
    }

    fun stopRequestingLocationUpdates() {
        logd("Stop requesting location updates")

        if (requestingLocationUpdatesFromGps) {
            logd("Stop requesting location updates from GPS")
            try {
                locationManager.removeUpdates(gpsLocationManagerListener)
                requestingLocationUpdatesFromGps = false
            } catch (e: Exception) {
                loge("Couldn't stop requesting location updates from GPS", e)
            }
        }

        if (requestingLocationUpdatesFromNetwork) {
            logd("Stop requesting location updates from cellular network")
            try {
                locationManager.removeUpdates(networkLocationManagerListener)
                requestingLocationUpdatesFromNetwork = false
            } catch (e: Exception) {
                loge("Couldn't stop requesting location updates from cellular network", e)
            }
        }

        if (requestingLocationUpdatesFromFusedLocationClient) {
            logd("Stop requesting location updates from fused location client")
            try {
                fusedLocationClient.removeLocationUpdates(fusedLocationClientCallback)
                requestingLocationUpdatesFromFusedLocationClient = false
            } catch (e: Exception) {
                loge("Couldn't stop requesting location updates from fused location client", e)
            }
        }
    }

    fun startRequestingLocationUpdates() {
        logd("Start requesting location updates")

        if (!requestingLocationUpdatesFromGps) {
            logd("Start requesting location updates from GPS")
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    locationRequest.interval,
                    locationRequest.smallestDisplacement,
                    gpsLocationManagerListener
                )
                requestingLocationUpdatesFromGps = true
            } catch (e: SecurityException) { // no permission
                loge("Couldn't request location updates from GPS", e)
            } catch (e: IllegalArgumentException) { // provider doesn't exist: gps
                loge("Couldn't request location updates from GPS", e)
            }
        }

        if (!requestingLocationUpdatesFromNetwork) {
            logd("Start requesting location updates from cellular network")
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    locationRequest.interval,
                    locationRequest.smallestDisplacement,
                    networkLocationManagerListener
                )
                requestingLocationUpdatesFromNetwork = true
            } catch (e: SecurityException) { // no permission
                loge("Couldn't request location updates from cellular network", e)
            } catch (e: IllegalArgumentException) { // provider doesn't exist: network
                loge("Couldn't request location updates from cellular network", e)
            }
        }

        if (!requestingLocationUpdatesFromFusedLocationClient) {
            logd("Start requesting location updates from fused location client")
            try {
                fusedLocationClient.requestLocationUpdates(
                    fusedLocationClientRequest,
                    fusedLocationClientCallback,
                    null
                )
                requestingLocationUpdatesFromFusedLocationClient = true
            } catch (e: SecurityException) {
                loge("Couldn't request location updates from fused location client", e)
            }
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

    private fun loge(msg: String, e: Exception) {
        Log.e("LocationService", msg, e)
    }

    override fun onLocationPermissionGranted() {
        startRequestingLocationUpdates()
    }

    override fun onLocationSettingsOn() {
        startRequestingLocationUpdates()
    }

    override fun onLocationSettingsOff() {
        stopRequestingLocationUpdates()
    }
}
