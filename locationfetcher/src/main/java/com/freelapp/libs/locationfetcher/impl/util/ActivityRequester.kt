package com.freelapp.libs.locationfetcher.impl.util

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

internal typealias ResolutionResolver = ActivityResultLauncher<IntentSenderRequest>
internal typealias PermissionRequester = ActivityResultLauncher<Array<String>>

internal inline fun ComponentActivity.resolutionResolver(
    crossinline block: (ActivityResult) -> Unit
): ActivityResultLauncher<IntentSenderRequest> =
    registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        block(it)
    }

internal inline fun ComponentActivity.permissionRequester(
    crossinline block: (Map<String, Boolean>) -> Unit
): ActivityResultLauncher<Array<String>> =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        block(it.toMap())
    }
