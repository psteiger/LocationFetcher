# location-service

Building location-aware Android apps can be a bit tricky.

This library provides a simple location service and base activity to make your Android app location-aware.

The service uses GPS and network by default and needs ACCESS_FINE_LOCATION permission, but you can personalize your `locationRequest` to suit your needs.


## Installation

### Using Gradle

```
implementation 'com.github.psteiger:location-service:3.4'
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

1. Extend `LocationActivity()` and implement `LocationChangeListener`.
2. Override `onLocationReceived(l: Location)`.
3. (Optional) Implement `LocationServiceConnectionListener` and override `onLocationServiceConnected()` and `onLocationServiceDisconnected()` to run code after service connection and disconnection. Note that this refers to service connection, not location changes.
4. (Optional) Implement `LocationPermissionListener` and override `onLocationPermissionGranted()` to run code after user granted location permission to the app.
5. (Optional) Implement `LocationSettingsListener` and override `onLocationSettingsOn()` and `onLocationSettingsOff()` to run code after location settings state changes (checked on `onStart()`).

```
import com.freelapp.libs.locationservice.LocationActivity

class MyActivity : LocationActivity() : LocationChangeListener {

    private var currentLocation: Location? = null

    override fun onLocationReceived(l: Location) {
        currentLocation = l
    }
}
```

#### Creating your own logic to deal with the service: an example

You may check source code to implement your own Activity logic.

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
    askForPermissionUntilGiven = false       // insist on asking for permission until given
    askForEnabledSettingsUntilGiven = false  // insist on asking until location setting is enabled
    requestPermissionRationale = R.string.need_location_permission      // String resource of rationale for permission
}
```

