package com.freelapp.libs.locationfetcher.impl.util

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

typealias ResolutionResolver = ActivityResultLauncher<IntentSenderRequest>
typealias PermissionRequester = ActivityResultLauncher<Array<String>>

inline fun ComponentActivity.resolutionResolver(
    crossinline block: (ActivityResult) -> Unit
): ActivityResultLauncher<IntentSenderRequest> =
    registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        block(it)
    }

inline fun ComponentActivity.permissionRequester(
    crossinline block: (Map<String, Boolean>) -> Unit
): ActivityResultLauncher<Array<String>> =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        block(it.toMap())
    }
