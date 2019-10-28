package com.freelapp.libs.locationservice

import android.content.*
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import com.google.android.gms.common.api.ResolvableApiException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import java.lang.ClassCastException

/**
 * Service connection vars and binding code common to all classes is in this abstract class.
 */
abstract class LocationActivity : AppCompatActivity() {

    companion object {
        const val HAS_LOCATION_PERMISSION_CODE = 11666
        const val REQUEST_CHECK_SETTINGS = 11667
        val LOCATION_PERMISSION = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        var askForPermissionUntilGiven: Boolean = false
        var requestPermissionRationale: Int = R.string.need_location_permission
    }

    private sealed class LocationServiceMsg {
        class AddLocationListener(val listener: LocationChangeListener) : LocationServiceMsg()
        class RemoveLocationListener(val listener: LocationChangeListener) : LocationServiceMsg()
        object StartRequestingLocationUpdates : LocationServiceMsg()
        object StopRequestingLocationUpdates : LocationServiceMsg()
        object BroadcastLocation : LocationServiceMsg()
    }

    private lateinit var locationServiceActor: SendChannel<LocationServiceMsg>

    var locationService: LocationService? = null
        private set

    private var bound: Boolean = false
    private val locationPermissionListeners = mutableListOf<LocationPermissionListener>()
    private val locationServiceConnectionListeners = mutableListOf<LocationServiceConnectionListener>()
    private val locationSettingsListeners = mutableListOf<LocationSettingsListener>()

    private val locationServiceConn: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            logd( "LocationService bound. Notifying listeners: $locationServiceConnectionListeners")

            locationService = (service as LocationService.LocalBinder).service.apply {
                if (this@LocationActivity is LocationChangeListener)
                    addLocationListener(this@LocationActivity)
            }
            bound = true

            locationServiceConnectionListeners.forEach { it.onLocationServiceConnected() }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            logd("LocationService disconnected. Notifying listeners: $locationServiceConnectionListeners")

            locationService = null
            bound = false

            locationServiceConnectionListeners.forEach { it.onLocationServiceDisconnected() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (this is LocationPermissionListener)
            addLocationPermissionListener(this)
        if (this is LocationServiceConnectionListener)
            addLocationServiceConnectionListener(this)
        if (this is LocationSettingsListener)
            addLocationSettingsListener(this)

        locationServiceActor = lifecycleScope.actor(Dispatchers.Main) {
            for (msg in channel) {
                // avoids processing messages while service not bound, but instead of ignoring command, wait for binding.
                // that is why actors are used here.
                while (!bound) delay(200)

                when (msg) {
                    is LocationServiceMsg.AddLocationListener -> locationService?.addLocationListener(msg.listener)
                    is LocationServiceMsg.RemoveLocationListener -> locationService?.removeLocationListener(msg.listener)
                    LocationServiceMsg.StartRequestingLocationUpdates -> locationService?.startRequestingLocationUpdates()
                    LocationServiceMsg.StopRequestingLocationUpdates -> locationService?.stopRequestingLocationUpdates()
                    LocationServiceMsg.BroadcastLocation -> locationService?.broadcastLocation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (this is LocationPermissionListener)
            removeLocationPermissionListener(this)
        if (this is LocationServiceConnectionListener)
            removeLocationServiceConnectionListener(this)
        if (this is LocationSettingsListener)
            removeLocationSettingsListener(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                RESULT_OK -> {
                    logd("Location is enabled in Settings")
                    locationSettingsListeners.forEach { it.onLocationSettingsOn() }
                    askForPermissionIfNeededAndBindIfNotAlready()
                }
                RESULT_CANCELED -> {
                    logd("Location is NOT enabled in Settings")
                    locationSettingsListeners.forEach { it.onLocationSettingsOff() }
                    showSnackbar(R.string.need_location_settings)
                    checkSettings()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkSettings()
    }

    override fun onPause() {
        super.onPause()

        logd("onPause")

        if (bound) {
            logd("onPause: Unbinding service...")
            if (this@LocationActivity is LocationChangeListener)
                locationService?.removeLocationListener(this)
            unbindService(locationServiceConn)
            bound = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            HAS_LOCATION_PERMISSION_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.firstOrNull() == PERMISSION_GRANTED) {
                    permissionGrantedCallCallbackAndBind()
                } else if (shouldShowRationale()) {
                    logd("Permission failed")
                    showSnackbar(requestPermissionRationale)

                    if (!hasPermission() && askForPermissionUntilGiven)
                        askForPermission()
                }
            }
        }
    }

    /**
     * Private methods
     */
    private fun showSnackbar(res: Int) {
        Snackbar
            .make(
                findViewById(android.R.id.content),
                getString(res),
                Snackbar.LENGTH_LONG
            )
            .show()
    }

    private fun bind() = bindService(
        Intent(this, LocationService::class.java),
        locationServiceConn,
        Context.BIND_AUTO_CREATE
    ).also { logd("LocationService - bindService() called.") }

    private fun checkSettings() {
        logd("Checking settings")

        val builder = LocationSettingsRequest.Builder().addLocationRequest(LocationRequest.create())
        val task = LocationServices.getSettingsClient(this).checkLocationSettings(builder.build())

        task.addOnCompleteListener {
            try {
                task.getResult(ApiException::class.java)
                // All location settings are satisfied. The client can initialize location
                logd("Checking settings - Satisfied.")
                locationSettingsListeners.forEach { it.onLocationSettingsOn() }
                askForPermissionIfNeededAndBindIfNotAlready()
            } catch (e: ApiException) {
                logd("Checking settings - Not satisfied. Status code: ${e.statusCode}: ${e.localizedMessage}.")
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        logd("Checking settings - Resolution required")
                        locationSettingsListeners.forEach { it.onLocationSettingsOff() }

                        try {
                            // Cast to a resolvable exception.
                            val resolvable = e as ResolvableApiException
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().

                            logd("Checking settings - Resolution is possible. Requesting...")

                            resolvable.startResolutionForResult(
                                this,
                                REQUEST_CHECK_SETTINGS
                            )

                        } catch (_: IntentSender.SendIntentException) {
                            // Ignore the error.
                            logd("Checking settings - SendIntentException")
                        } catch (_: ClassCastException) {
                            logd("Checking settings - ClassCastException")
                            // Ignore, should be an impossible error.
                        }
                    }
                }
            }
        }
    }

    private fun askForPermissionIfNeededAndBindIfNotAlready() {
        if (!bound) {
            if (!hasPermission())
                askForPermission()
            else
                permissionGrantedCallCallbackAndBind()
        }
    }

    private fun permissionGrantedCallCallbackAndBind() {
        logd("Permission granted. Binding service.")
        locationPermissionListeners.forEach { it.onLocationPermissionGranted() }
        bind()
    }

    private fun askForPermission() {
        logd("asking for permission")
        ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, HAS_LOCATION_PERMISSION_CODE)
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION.first()) == PERMISSION_GRANTED

    private fun shouldShowRationale() =
        ActivityCompat.shouldShowRequestPermissionRationale(this, LOCATION_PERMISSION.first())

    /**
     * Public API
     */
    /* update whoever registers about location changes */
    fun addLocationListener(listener: LocationChangeListener) {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            locationServiceActor.send(LocationServiceMsg.AddLocationListener(listener))
        }
    }

    fun removeLocationListener(listener: LocationChangeListener) {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            locationServiceActor.send(LocationServiceMsg.RemoveLocationListener(listener))
        }
    }

    fun startRequestingLocationUpdates() {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            locationServiceActor.send(LocationServiceMsg.StartRequestingLocationUpdates)
        }
    }

    fun stopRequestingLocationUpdates() {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            locationServiceActor.send(LocationServiceMsg.StopRequestingLocationUpdates)
        }
    }

    fun broadcastLocation() {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            locationServiceActor.send(LocationServiceMsg.BroadcastLocation)
        }
    }

    fun addLocationPermissionListener(listener: LocationPermissionListener) {
        locationPermissionListeners.add(listener)
    }

    fun removeLocationPermissionListener(listener: LocationPermissionListener) {
        locationPermissionListeners.remove(listener)
    }

    fun addLocationSettingsListener(listener: LocationSettingsListener) {
        locationSettingsListeners.add(listener)
    }

    fun removeLocationSettingsListener(listener: LocationSettingsListener) {
        locationSettingsListeners.remove(listener)
    }

    fun addLocationServiceConnectionListener(listener: LocationServiceConnectionListener) {
        locationServiceConnectionListeners.add(listener)
    }

    fun removeLocationServiceConnectionListener(listener: LocationServiceConnectionListener) {
        locationServiceConnectionListeners.remove(listener)
    }


    private fun logd(msg: String) {
        if (LocationService.debug) Log.d("LocationActivity", msg)
    }
}