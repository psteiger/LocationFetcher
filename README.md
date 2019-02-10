# location-service

Building location-based Android apps can be a bit tricky.

This library provides a simple location service that makes your Android app location-aware.

The service uses GPS and Network and needs ACCESS_FINE_LOCATION.

## Installation

### Using Gradle

```
implementation 'com.github.psteiger:location-service:0.4'
```

### On Manifest

On root level:

```
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### On Activity

TODO: Abstract that activity into the library.

Setup the activity or fragment you want to be location-aware as follows.

First, we need to deal with service binding.

```
private var bound: Boolean = false

private val locationServiceConn: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        locationService = (service as LocationService.LocalBinder).service
        bound = true
    }

    override fun onServiceDisconnected(arg0: ComponentName) {
        bound = false
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (!bound) {
        bindService(Intent(this, LocationService::class.java), 
              locationServiceConn, 
              Context.BIND_AUTO_CREATE)
    }
}

override fun onDestroy() {
    super.onDestroy()
    if (bound) {
        unbindService(locationServiceConn)
        bound = false
    }
}
```

We'll need to explicitly ask the user for permission to access location.

Substitute `goOn()` with code you need to run after permission is granted.

```
override fun onStart() {
    super.onStart()

    if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION.first()) != PackageManager.PERMISSION_GRANTED)
        ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, HAS_LOCATION_PERMISSION_CODE)
    else
        goOn()
}

override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
    when (requestCode) {
        App.HAS_LOCATION_PERMISSION_CODE ->
            // If request is cancelled, the result arrays are empty.
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                goOn()
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, App.LOCATION_PERMISSION[0])) {
                Snackbar
                    .make(findViewById(android.R.id.content), getString(R.string.need_permissions), Snackbar.LENGTH_INDEFINITE)
                    .show()

                if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION.first()) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, App.LOCATION_PERMISSION, App.HAS_LOCATION_PERMISSION_CODE)
                }
            }
    }
}
```

Make sure it implements `ILocationListener`

```
import com.freelapp.libs.locationservice.ILocationListener
```

Make it listen for a new location:

```
addLocationListener(this)
```

When a location is gotten, following function will be called.

```
override fun onLocationReceived(l: Location) {
    // Do what you want with the received location
    currentLocation = l
    // Once you got it, you may want to stop listening
    removeLocationListener(this)
}
```

Location Service stops asking Android for location updates once a location is gotten. I plan to make it a configurable feature on future releases.

### 
