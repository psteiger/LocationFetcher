package com.freelapp.libs.locationfetcher

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import com.freelapp.libs.locationfetcher.impl.LocationFetcherImpl
import com.freelapp.libs.locationfetcher.impl.LocationSourceImpl
import kotlinx.coroutines.CoroutineScope

public fun ComponentActivity.locationFetcher(
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(this, LocationFetcher.Config(rationale).apply(config))

public fun Context.locationFetcher(
    owner: LifecycleOwner,
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(this, owner, LocationFetcher.Config(rationale).apply(config))

public fun LocationFetcher(
    activity: ComponentActivity,
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(activity, LocationFetcher.Config(rationale).apply(config))

public fun LocationFetcher(
    context: Context,
    owner: LifecycleOwner,
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(context, owner, LocationFetcher.Config(rationale).apply(config))

public fun LocationFetcher(
    activity: ComponentActivity,
    config: LocationFetcher.Config,
): LocationFetcher = LocationFetcherImpl(activity, config.copy())

public fun LocationFetcher(
    context: Context,
    owner: LifecycleOwner,
    config: LocationFetcher.Config
): LocationFetcher = LocationFetcherImpl(context, owner, config.copy())

public fun LocationSource(
    scope: CoroutineScope,
    locationFetcher: LocationFetcher,
): LocationSource = LocationSourceImpl(scope, locationFetcher)