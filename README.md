# LocationFetcher

[![Download](https://img.shields.io/maven-central/v/app.freel/locationfetcher)](https://search.maven.org/artifact/app.freel/locationfetcher)

Simple location fetcher for Android Apps built with Kotlin and Coroutines.

Building location-aware Android apps can be a bit tricky. This library makes it as simple as:

```kotlin
class MyActivity : ComponentActivity() {

    private val locationFetcher = locationFetcher({ getString(R.string.location_rationale) }) {
        // custom configuration block
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationFetcher.location
                    .onEach { errorsOrLocation ->
                        errorsOrLocation.tap { location ->
                            // Got location
                        }.tapLeft { errors ->
                            // Optional. Handle errors. This is optional because errors 
                            // (no location permission, or setting disabled), will try to be
                            // automatically handled by lib.
                        }
                    }
                    .launchIn(this)

                // Optional, redundant as errors are already reported to 'location' flow.
                locationFetcher.settingsStatus
                    .onEach { /* Location got enabled or disabled in device settings */ }
                    .launchIn(this)

                // Optional, redundant as errors are already reported to 'location' flow.
                locationFetcher.permissionStatus
                    .onEach { /* App allowed or disallowed to access the device's location. */ }
                    .launchIn(this)
            }
        }
    }
}
```

This library provides a simple location component, `LocationFetcher`, requiring only an instance of either `ComponentActivity`, `Fragment` or `Context`, to make your Android app location-aware.

If the device's location services are disabled, or if your app is not allowed location permissions by the user, this library will automatically ask the user to enable location services in settings or to allow the necessary permissions as soon as you start collecting the `LocationFetcher.location` flow.

The service uses GPS and network as location providers by default and thus the app needs to declare use of the `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` permissions on its `AndroidManifest.xml`. Those permissions are already declared in this library, so manifest merging takes care of it.

You can personalize your `LocationRequest` to suit your needs using the custom configuration block.

## Installation with Gradle

### Setup Maven Central on project-level build.gradle

This library is hosted in Maven Central, so you must set it up for your project before adding the module-level dependency.

#### New way

The new way to install dependencies repositories is through the `dependencyResolutionManagement` DSL in `settings.gradle(.kts)`.

Kotlin or Groovy:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

OR

#### Old way

On project-level `build.gradle`:

Kotlin or Groovy:
```kotlin
allprojects {
  repositories {
    mavenCentral()
  }
}
```

### Add dependency

On app-level `build.gradle`, add dependency:

Groovy:
```groovy
dependencies {
  implementation 'app.freel:locationfetcher:8.1.0'
}
```

Kotlin:
```kotlin
dependencies {
  implementation("app.freel:locationfetcher:8.1.0")
}
```

## Usage

### Instantiating

On any `ComponentActivity`, `Fragment`, or `Context` class, you can instantiate a `LocationFetcher` by calling the extension functions on `ComponentActivity`, `Fragmnet`, or `Context`:

```kotlin
locationFetcher({ getString(R.string.location_rationale) }) {
    // configuration block
}
```

Alternatively, there are some `LocationFetcher()` method overloads. You can see all public builders [in here](https://github.com/psteiger/LocationFetcher/blob/master/locationfetcher/src/main/java/com/freelapp/libs/locationfetcher/Builders.kt).

If `LocationFetcher` is created with a `ComponentActivity` or `Fragment`, it will be able to show dialogs to request the user to enable permission in Android settings and to allow the app to obtain the device's location. If `LocationFetcher` is created with a non-UI `Context`, it won't be able to show dialogs.

#### Permission rationale

In accordance with Google's best practices and policies, if user denies location permission, we must tell the user the rationale for the need of the user location, then we can ask permission a last time. If denied again, we must respect the user's decision.

The rationale must be passed to `LocationFetcher` builders. It will be shown to the user as an `AlertDialog`.

### Collecting location

Once instantiated, the component gives you three `Flow`s to collect: one for new locations, one for settings status, and one for location permissions status. Usually, you only need to collect the location flow, as errors also flow through it already.

```kotlin
val LocationFetcher.location: SharedFlow<Either<Nel<LocationFetcher.Error>, Location>> // Nel stands for non-empty list.
val LocationFetcher.permissionStatus: SharedFlow<Boolean>
val LocationFetcher.settingsStatus: SharedFlow<Boolean>
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
locationFetcher("We need your permission to use your location for showing nearby items") {
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
    debug = false
}
```

Alternatively, you might prefer to create a standalone configuration instance. It is useful, for example, when sharing a common configuration between multiple `LocationFetcher` instances:

```kotlin
val config = LocationFetcher.config(
    rationale = "We need your permission to use your location for showing nearby items",
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
    debug = true
)
val locationFetcher = locationFetcher(config)
```
