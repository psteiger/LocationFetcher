# LocationFetcher-compose

[![Download](https://img.shields.io/maven-central/v/app.freel/locationfetcher-compose)](https://search.maven.org/artifact/app.freel/locationfetcher-compose)

Simple location fetcher for Android Apps built with Kotlin, Coroutines and Compose.

**This is a completely independent artifact from the Activity/Fragment LocationFetcher, and is built
exclusively for use with Jetpack Compose.**

```kotlin
@Composable
fun NearbyRestaurants() {
    LocationFetcher(
        requestConfig = {
            interval = 15.seconds.inWholeMilliseconds
            fastestInterval = 15.seconds.inWholeMilliseconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        },
        rationale = "We need your location for finding nearby restaurants."
    ) {
        val (locationResult, settingEnabled, permissionsGranted) = LocalLocationFetcher.current
        when (locationResult) {
            null -> MissingLocation()
            else -> NearbyRestaurants(locationResult)
        }
    }
}
```

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

On app-level `build.gradle(.kts)`, add dependency:

```kotlin
dependencies {
  implementation("app.freel:locationfetcher-compose:9.0.0")
}
```

## Usage

On any `Composable`, create a `LocationFetcher` composable. All children composables will have
access to a `CompositionLocal` called `LocalLocationFetcher.current`, which returns a
`LocationState`:

```kotlin
@Immutable
public data class LocationState(
    /** Latest location results, or null if no locations were reported lately. */
    val locationResult: LocationResult?,
    /** Whether location setting is enabled, or null if status is unknown. */
    val settingEnabled: Boolean?,
    /** Whether location permissions are granted. */
    val permissionsGranted: Boolean
)
```

See `LocationFetcher` inline documentation for customizations.