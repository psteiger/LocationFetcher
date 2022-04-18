package com.freelapp.libs.locationfetcher.impl.util

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner

internal typealias ResolutionResolver = ActivityResultLauncher<IntentSenderRequest>
internal typealias PermissionRequester = ActivityResultLauncher<Array<String>>

internal inline fun LifecycleOwner.permissionRequester(
    crossinline block: (Map<String, Boolean>) -> Unit
): PermissionRequester? = when (this) {
    is ComponentActivity -> activityPermissionRequester(block)
    is Fragment -> fragmentPermissionRequester(block)
    else -> null
}

internal inline fun LifecycleOwner.resolutionResolver(
    crossinline block: (ActivityResult) -> Unit
): ResolutionResolver? = when (this) {
    is ComponentActivity -> activityResolutionResolver(block)
    is Fragment -> fragmentResolutionResolver(block)
    else -> null
}

internal inline fun ComponentActivity.activityResolutionResolver(
    crossinline block: (ActivityResult) -> Unit
): ActivityResultLauncher<IntentSenderRequest> =
    registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        block(it)
    }

internal inline fun ComponentActivity.activityPermissionRequester(
    crossinline block: (Map<String, Boolean>) -> Unit
): ActivityResultLauncher<Array<String>> =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        block(it.toMap())
    }

internal inline fun Fragment.fragmentResolutionResolver(
    crossinline block: (ActivityResult) -> Unit
): ActivityResultLauncher<IntentSenderRequest> =
    registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        block(it)
    }

internal inline fun Fragment.fragmentPermissionRequester(
    crossinline block: (Map<String, Boolean>) -> Unit
): ActivityResultLauncher<Array<String>> =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        block(it.toMap())
    }

internal val ActivityResult.resolved get() = Activity.RESULT_OK == resultCode
