# LocationFetcher

[![](https://jitpack.io/v/psteiger/LocationFetcher.svg)](https://jitpack.io/#psteiger/LocationFetcher)

Simple location fetcher for Android Apps built with Kotlin and Coroutines.

Building location-aware Android apps can be a bit tricky. This library makes it as simple as:

```kotlin
class MyActivity : ComponentActivity() {

    private val locationFetcher by lazy {
        locationFetcher()   // extension on ComponentActivity
    }

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                with (locationFetcher) {
                    location
                        .onEach { /* Location received */ }
                        .launchIn(lifecycleScope)

                    settingsStatus
                        .onEach { /* Location got enabled or disabled in device settings */ }
                        .launchIn(lifecycleScope)

                    permissionStatus
                        .onEach { /* App allowed or disallowed to access the device's location. */ }
                        .launchIn(lifecycleScope)
                }
            }
        }
    }
}
```

This library provides a simple location component, `LocationFetcher`, requiring only either an `ComponentActivity` instance or a `Context` instance, to make your Android app location-aware.

The service uses GPS and network as location providers by default and thus the app needs to declare use of the `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` permissions on its `AndroidManifest.xml`.

You can personalize your `LocationRequest` to suit your needs.

If the device's location services are disabled, or if your app is not allowed location permissions by the user, this library can (optionally) automatically ask the user to enable location services in settings or to allow the necessary permissions.

## Installation

### Using Gradle

On project-level `build.gradle`, add [Jitpack](https://jitpack.io/) repository:

```groovy
allprojects {
  repositories {
    maven { url 'https://jitpack.io' }
  }
}
```

On app-level `build.gradle`, add dependency:

```groovy
dependencies {
  implementation 'com.github.psteiger:locationfetcher:6.00'
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

On any `ComponentActivity` or `Context` class, you can instantiate a `LocationFetcher` by calling the extension functions on `ComponentActivity` or `Context`:

```kotlin
locationFetcher()
```

Alternatively, there are two `LocationFetcher.create()` method overloads: `LocationFetcher.create(Context)` and `LocationFetcher.create(ComponentActivity)`

```kotlin
LocationFetcher.create(this)
```

If `LocationFetcher` is created with a `ComponentActivity`, it will be able to show dialogs to request the user to enable permission in Android settings and to allow the app to obtain the device's location. If `LocationFetcher` is created with a non-`ComponentActivity` `Context`, it won't be able to show dialogs.

Once instantiated, the component gives you three `Flow`s to collect: one for new locations, one for settings status, and one for location permissions status:

```kotlin
LocationFetcher.location: StateFlow<Location?>
LocationFetcher.permissionStatus: StateFlow<PermissionStatus>
LocationFetcher.settingsStatus: StateFlow<SettingsStatus>
```

To manually request location permissions or location settings enablement, you can call the following APIs:

```kotlin
suspend fun requestLocationPermissions()
suspend fun requestEnableLocationSettings()
```

Results will be delivered on the aforementioned flows.

### Options

`LocationFetcher` supports the following configurations for location fetching when creating the component:

(Note: for GPS and Network providers, only `interval` and `smallestDisplacement` are used. If you want to use all options, limit providers to `LocationRequest.Provider.Fused`)

```kotlin
LocationFetcher.create(this) {
    fastestInterval = 5000
    interval = 15000
    maxWaitTime = 100000
    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    smallestDisplacement = 50f
    isWaitForAccurateLocation = false
    providers = listOf(
        LocationRequest.Provider.GPS,
        LocationRequest.Provider.Network, 
        LocationRequest.Provider.Fused
    )
    numUpdates = Int.MAX_VALUE
    requestLocationPermissions = true    // no effect if built with Context
    requestEnableLocationSettings = true // no effect if built with Context
    debug = true
}
```

Alternatively, you might prefer to create a standalone configuration instance. It is useful, for example, when sharing a common configuration between multiple `LocationFetcher` instances:

```kotlin
val config = LocationFetcher.config(
    fastestInterval = 5000,
    interval = 15000,
    maxWaitTime = 100000,
    priority = LocationRequest.PRIORITY_HIGH_ACCURACY,
    smallestDisplacement = 50f,
    isWaitForAccurateLocation = false,
    providers = listOf(
        LocationRequest.Provider.GPS,
        LocationRequest.Provider.Network,
        LocationRequest.Provider.Fused
    ),
    numUpdates = Int.MAX_VALUE,
    requestLocationPermissions = true,    // no effect if built with Context
    requestEnableLocationSettings = true, // no effect if built with Context
    debug = true
)
LocationFetcher.create(this, config)
```
