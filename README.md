# location-service

Building location-aware Android apps can be a bit tricky.

This library provides a simple location service and base activity to make your Android app location-aware.

The service uses GPS and network and needs ACCESS_FINE_LOCATION permission.

## Installation

### Using Gradle

```
implementation 'com.github.psteiger:location-service:2.2'
```

### On Manifest

On root level, allow permission:

```
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

On app level, declare service:

```
<service
    android:name="com.freelapp.libs.locationservice.LocationService"
    android:enabled="true"
    android:exported="false" />
```

### On Activity

Now, the activity that will use the service must deal with:

1. asking the user for permission to access device, 
2. creating an instance of the location service, 
3. binding (and unbinding) to the location service instance,
4. listening to location updates from the location service instance.

There are two options for achieving this: use the base abstract activity provided by the library (recommended), or implement your own logic by hand.

#### Using provided base activity

This is the simplest and recommended solution.

Make your Activity:

1. Extend `LocationActivity()`
2. Override `onLocationReceived(l: Location)`
3. (Optional) Override `onLocationServiceConnected()` and `onLocationServiceDisconnected()` to run code after service connection and disconnection. Note that this refers to service connection, not location changes.

```
import com.freelapp.libs.locationservice.LocationActivity

class MyActivity : LocationActivity() {

    private var currentLocation: Location? = null
    
    override fun onLocationReceived(l: Location) {
        currentLocation = l
    }
}
```

#### Creating your own logic to deal with the service: an example

If you need something more personalized, you can setup the activity or fragment you want to be location-aware as the follow example.

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
val HAS_LOCATION_PERMISSION_CODE = 1
val LOCATION_PERMISSION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

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

## Customizations

### Using Firebase Auth?

If you want to make the location service wait for Firebase user authentication before asking the device for location updates, you can:

```
LocationService.waitForFirebaseAuth = true
```

on Activity's `onCreate()` or App's `onCreate()`.

This is useful when, on location changes, you trigger Firebase database changes that demands the user to be authenticated for permission to read/write to the database.

### Other customizations

On Activity's `onCreate()` or App's `onCreate()`, you can apply the following customizations.

```
LocationService.apply {
    debug = true                        // prints debug info
    locationRequest.apply {
        numUpdates = 4                  // defaults to Int.MAX_VALUE
        interval = 1000                 // defaults to 0 (milliseconds)
        smallestDisplacement = 10f      // defaults to 0f (meters)
    }
}
LocationActivity.apply {
    askForPermissionUntilGiven = false  // insist on asking for permission until given
    requestPermissionRationale = R.string.need_location_permission      // String resource of rationale for permission
}
```

