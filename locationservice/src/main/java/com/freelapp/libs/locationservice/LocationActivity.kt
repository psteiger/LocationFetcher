package com.freelapp.libs.locationservice

import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import com.google.android.gms.common.api.ResolvableApiException
import java.lang.ClassCastException

/**
 * Service connection vars and binding code common to all classes is in this abstract class.
 */
abstract class LocationActivity : AppCompatActivity(), ILocationListener {

    companion object {
        const val HAS_LOCATION_PERMISSION_CODE = 11666
        const val REQUEST_CHECK_SETTINGS = 11667
        val LOCATION_PERMISSION = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        var askForPermissionUntilGiven: Boolean = false
        var requestPermissionRationale: Int = R.string.need_location_permission
    }

    var locationService: LocationService? = null
        private set

    private var bound: Boolean = false

    private val locationServiceConn: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            locationService = (service as LocationService.LocalBinder).service.apply {
                addLocationListener(this@LocationActivity)
            }
            bound = true
            onLocationServiceConnected() // optionally overridden by children activities
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            locationService = null
            bound = false
            onLocationServiceDisconnected()
        }
    }

    private fun checkSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(LocationRequest.create())
        val task = LocationServices.getSettingsClient(this).checkLocationSettings(builder.build())

        task.addOnCompleteListener {
            try {
                task.getResult(ApiException::class.java)
                // All location settings are satisfied. The client can initialize location
            } catch (e: ApiException) {
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        try {
                            // Cast to a resolvable exception.
                            val resolvable = e as ResolvableApiException
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            resolvable.startResolutionForResult(
                                this,
                                REQUEST_CHECK_SETTINGS
                            )
                        } catch (_: IntentSender.SendIntentException) {
                            // Ignore the error.
                        } catch (_: ClassCastException) {
                            // Ignore, should be an impossible error.
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkSettings()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                RESULT_OK -> askForPermissionAndBindIfNotAlready()
                RESULT_CANCELED -> {
                    showSnackbar(R.string.need_location_settings)
                    checkSettings()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (bound) {
            locationService?.removeLocationListener(this)
            unbindService(locationServiceConn)
            bound = false
        }
    }

    private fun bind() = bindService(
        Intent(this, LocationService::class.java),
        locationServiceConn,
        Context.BIND_AUTO_CREATE
    )

    private fun askForPermissionAndBindIfNotAlready() {
        if (!bound) {
            if (ContextCompat.checkSelfPermission(this,
                    LOCATION_PERMISSION.first()) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, HAS_LOCATION_PERMISSION_CODE)
            } else {
                bind()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            HAS_LOCATION_PERMISSION_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    bind()
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, LOCATION_PERMISSION.first())) {
                    showSnackbar(requestPermissionRationale)

                    if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION.first()) != PackageManager.PERMISSION_GRANTED &&
                        askForPermissionUntilGiven) {
                        ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, HAS_LOCATION_PERMISSION_CODE)
                    }
                }
            }
        }
    }

    private fun showSnackbar(res: Int) {
        Snackbar
            .make(
                findViewById(android.R.id.content),
                getString(res),
                Snackbar.LENGTH_INDEFINITE
            )
            .show()
    }

    private sealed class LocationServiceMsg {
        class AddLocationListener(val listener: ILocationListener) : LocationServiceMsg()
        class RemoveLocationListener(val listener: ILocationListener) : LocationServiceMsg()
        object StartRequestingLocationUpdates : LocationServiceMsg()
        object StopRequestingLocationUpdates : LocationServiceMsg()
        object BroadcastLocation : LocationServiceMsg()
    }

    @ObsoleteCoroutinesApi
    private val locationServiceActor = GlobalScope.actor<LocationServiceMsg>(Dispatchers.Main) {
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

    /* update whoever registers about location changes */
    @ObsoleteCoroutinesApi
    fun addLocationListener(listener: ILocationListener) = GlobalScope.launch {
        locationServiceActor.send(LocationServiceMsg.AddLocationListener(listener))
    }

    @ObsoleteCoroutinesApi
    fun removeLocationListener(listener: ILocationListener) = GlobalScope.launch {
        locationServiceActor.send(LocationServiceMsg.RemoveLocationListener(listener))
    }

    @ObsoleteCoroutinesApi
    fun startRequestingLocationUpdates() = GlobalScope.launch {
        locationServiceActor.send(LocationServiceMsg.StartRequestingLocationUpdates)
    }

    @ObsoleteCoroutinesApi
    fun stopRequestingLocationUpdates() = GlobalScope.launch {
        locationServiceActor.send(LocationServiceMsg.StopRequestingLocationUpdates)
    }

    @ObsoleteCoroutinesApi
    fun broadcastLocation() = GlobalScope.launch {
        locationServiceActor.send(LocationServiceMsg.BroadcastLocation)
    }

    // override this !
    protected open fun onLocationServiceConnected() = Unit
    protected open fun onLocationServiceDisconnected() = Unit
}