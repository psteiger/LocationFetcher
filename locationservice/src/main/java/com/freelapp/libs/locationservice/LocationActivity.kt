package com.freelapp.libs.locationservice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor

/**
 * Service connection vars and binding code common to all classes is in this abstract class.
 */
abstract class LocationActivity : AppCompatActivity(), ILocationListener {

    companion object {
        const val HAS_LOCATION_PERMISSION_CODE = 11666
        val LOCATION_PERMISSION = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        var askForPermissionUntilGiven: Boolean = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askForPermissionAndBindIfNotAlready()
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
                    Snackbar
                        .make(
                            findViewById(android.R.id.content),
                            getString(R.string.need_location_permission),
                            Snackbar.LENGTH_LONG
                        )
                        .show()

                    if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION.first()) != PackageManager.PERMISSION_GRANTED &&
                            askForPermissionUntilGiven) {
                        ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, HAS_LOCATION_PERMISSION_CODE)
                    }
                }
            }
        }
    }

    private sealed class LocationServiceMsg {
        class AddLocationListener(val listener: ILocationListener) : LocationServiceMsg()
        class RemoveLocationListener(val listener: ILocationListener) : LocationServiceMsg()
        object StartRequestingLocationUpdates : LocationServiceMsg()
        object StopRequestingLocationUpdates : LocationServiceMsg()
    }

    @ObsoleteCoroutinesApi
    private val locationServiceActor = GlobalScope.actor<LocationServiceMsg>(Dispatchers.Main) {
        for (msg in channel) {
            while (!bound) delay(200) // avoids processing messages while service not bound

            when (msg) {
                is LocationServiceMsg.AddLocationListener -> locationService?.addLocationListener(msg.listener)
                is LocationServiceMsg.RemoveLocationListener -> locationService?.removeLocationListener(msg.listener)
                LocationServiceMsg.StartRequestingLocationUpdates -> locationService?.startRequestingLocationUpdates()
                LocationServiceMsg.StopRequestingLocationUpdates -> locationService?.stopRequestingLocationUpdates()
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

    // override this if you want to run code after service is connected/disconnected.
    // note this is not about getting a location or not.
    protected open fun onLocationServiceConnected() = Unit
    protected open fun onLocationServiceDisconnected() = Unit
}
