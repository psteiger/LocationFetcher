package com.freelapp.libs.locationfetcher

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.freelapp.libs.locationfetcher.impl.LocationFetcherImpl
import com.freelapp.libs.locationfetcher.impl.LocationSourceImpl
import kotlinx.coroutines.CoroutineScope

public fun Fragment.locationFetcher(
    rationaleProducer: () -> String,
    configProducer: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(
        this,
        lazy { LocationFetcher.Config(rationaleProducer()).apply(configProducer) }
    )

public fun ComponentActivity.locationFetcher(
    rationaleProducer: () -> String,
    configProducer: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(
        this,
        lazy { LocationFetcher.Config(rationaleProducer()).apply(configProducer) }
    )

public fun Context.locationFetcher(
    owner: LifecycleOwner,
    rationaleProducer: () -> String,
    configProducer: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(
        this,
        owner,
        lazy { LocationFetcher.Config(rationaleProducer()).apply(configProducer) }
    )

public fun FragmentActivity.locationFetcher(
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(this, lazy { LocationFetcher.Config(rationale).apply(config) })

public fun Fragment.locationFetcher(
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(this, lazy { LocationFetcher.Config(rationale).apply(config) })

public fun ComponentActivity.locationFetcher(
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(this, lazy { LocationFetcher.Config(rationale).apply(config) })

public fun Context.locationFetcher(
    owner: LifecycleOwner,
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(this, owner, lazy { LocationFetcher.Config(rationale).apply(config) })

public fun LocationFetcher(
    activity: ComponentActivity,
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(activity, lazy { LocationFetcher.Config(rationale).apply(config) })

public fun LocationFetcher(
    fragment: Fragment,
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(fragment, lazy { LocationFetcher.Config(rationale).apply(config) })

public fun LocationFetcher(
    context: Context,
    owner: LifecycleOwner,
    rationale: String,
    config: LocationFetcher.Config.() -> Unit = { }
): LocationFetcher =
    LocationFetcher(context, owner, lazy { LocationFetcher.Config(rationale).apply(config) })

public fun LocationFetcher(
    activity: ComponentActivity,
    config: Lazy<LocationFetcher.Config>,
): LocationFetcher = LocationFetcherImpl(activity, lazy { config.value.copy() })

public fun LocationFetcher(
    fragment: Fragment,
    config: Lazy<LocationFetcher.Config>,
): LocationFetcher = LocationFetcherImpl(fragment, lazy { config.value.copy() })

public fun LocationFetcher(
    context: Context,
    owner: LifecycleOwner,
    config: Lazy<LocationFetcher.Config>
): LocationFetcher = LocationFetcherImpl(context, owner, lazy { config.value.copy() })

public fun LocationFetcher(
    activity: ComponentActivity,
    config: LocationFetcher.Config,
): LocationFetcher = LocationFetcherImpl(activity, lazy { config.copy() })

public fun LocationFetcher(
    fragment: Fragment,
    config: LocationFetcher.Config,
): LocationFetcher = LocationFetcherImpl(fragment, lazy { config.copy() })

public fun LocationFetcher(
    context: Context,
    owner: LifecycleOwner,
    config: LocationFetcher.Config
): LocationFetcher = LocationFetcherImpl(context, owner, lazy { config.copy() })

public fun LocationSource(
    scope: CoroutineScope,
    locationFetcher: LocationFetcher,
): LocationSource = LocationSourceImpl(scope, locationFetcher)
