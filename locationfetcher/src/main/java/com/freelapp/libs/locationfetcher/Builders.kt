package com.freelapp.libs.locationfetcher

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import com.freelapp.libs.locationfetcher.impl.LocationFetcherImpl
import com.freelapp.libs.locationfetcher.impl.LocationSourceImpl
import kotlinx.coroutines.CoroutineScope

fun ComponentActivity.locationFetcher(
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(this@locationFetcher, LocationFetcher.Config().apply(config))

fun Context.locationFetcher(
    owner: LifecycleOwner,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(this@locationFetcher, owner, LocationFetcher.Config().apply(config))

fun LocationFetcher(
    activity: ComponentActivity,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(activity, LocationFetcher.Config().apply(config))

fun LocationFetcher(
    context: Context,
    owner: LifecycleOwner,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(context, owner, LocationFetcher.Config().apply(config))

fun LocationFetcher(
    activity: ComponentActivity,
    config: LocationFetcher.Config
): LocationFetcher = LocationFetcherImpl(activity, config.copy())

fun LocationFetcher(
    context: Context,
    owner: LifecycleOwner,
    config: LocationFetcher.Config
): LocationFetcher = LocationFetcherImpl(context, owner, config.copy())

fun LocationSource(
    scope: CoroutineScope,
    locationFetcher: LocationFetcher,
): LocationSource = LocationSourceImpl(scope, locationFetcher)