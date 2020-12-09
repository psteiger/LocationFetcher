# LocationFetcher

Simple location fetcher for Android Apps built with Kotlin and Coroutines.

Building location-aware Android apps can be a bit tricky. This library makes it as simple as:

```kotlin
class MyActivity : AppCompatActivity() {

    private val locationFetcher: LocationFetcher by lazy {
        LocationFetcher.create(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        lifecycleScope.launchWhenStarted {
            location.collect { location: Location? ->
                // New location received.
            }
            settingsStatus.collect { enabled: Boolean? ->
                // Location got enabled or disabled in device settings.
            }
            permissionStatus.collect { allowed: Boolean? ->
                // App got allowed or disallowed to know the device's location.
            }
        }
    }
}
```

This library provides a simple location component, `LocationFetcher`, for use in any `FragmentActivity` class, or any `Context` class, to make your Android app location-aware.

The service uses GPS and network by default and needs `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.

You can personalize your `LocationRequest` to suit your needs.

If the device's location services are disabled, or if your app is not allowed location permissions by the user, this library can (optionally) automatically ask the user to enable location services in settings or to allow the necessary permissions.

## Installation

### Using Gradle

On app-level build.gradle, add dependency:

```groovy
dependencies {
    implementation 'com.github.psteiger:locationfetcher:5.4'
}
```

### On Manifest

On root level, allow permission:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yourapp">
    
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
</manifest>
```

## Usage

On any `FragmentActivity` or `Context` class, you can instantiate a `LocationFetcher` by calling:

```kotlin
LocationFetcher.create(this)
```

There are two method signatures: `LocationFetcher.create(Context)` and `LocationFetcher.create(FragmentActivity)`

If `LocationFetcher` is created with a `FragmentActivity`, it will be able to show dialogs to request the user to enable permission in Android settings and to allow the app to obtain the device's location. If `LocationFetcher` is created with a non-`FragmentActivity` `Context`, it won't be able to show dialogs.

Once instantiated, the component gives you three `Flow`s to collect: one for new locations, one for settings status, and one for location permissions status.

To manually request location permissions or location settings enablement, you can call the following APIs:

```kotlin
suspend fun requestLocationPermissions(): Boolean?
suspend fun requestEnableLocationSettings(): Boolean
```

### Options

`LocationFetcher` supports the following configurations for location fetching when creating the component:

```kotlin
LocationFetcher.create(this) {
    fastestInterval = 5000
    interval = 15000
    maxWaitTime = 100000
    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    smallestDisplacement = 50f
    providers = listOf(
        LocationRequest.Provider.GPS,
        LocationRequest.Provider.Network, 
        LocationRequest.Provider.Fused
    )
    requestLocationPermissions = true    // no effect if built with Context
    requestEnableLocationSettings = true // no effect if built with Context
    debug = true
}
```
