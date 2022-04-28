package com.freelapp.locationfetcher.compose

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
internal fun PermissionRequest(
    hasPermissions: Boolean,
    setHasPermissions: (Boolean) -> Unit,
    rationaleDismissed: Boolean,
    rationale: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.getActivity()

    val requester = rememberPermissionRequester { result ->
        val hasAllPermissions = result.values.all { it }
        setHasPermissions(hasAllPermissions)
    }
    val shouldShowRationale = activity?.shouldShowLocationPermissionsRationale() == true
    if (!hasPermissions) {
        if (shouldShowRationale && !rationaleDismissed) {
            rationale()
        } else {
            SideEffect {
                requester.launch(LOCATION_PERMISSIONS)
            }
        }
    }
}

private typealias PermissionRequester = ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>

@Composable
private fun rememberPermissionRequester(
    onResult: (Map<String, Boolean>) -> Unit
): PermissionRequester = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
    onResult
)

internal fun Context.hasLocationPermissions() = hasPermissions(LOCATION_PERMISSIONS)

private fun Context.hasPermissions(permissions: Array<String>) =
    permissions.all { hasPermission(it) }

private fun Context.hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(
        this,
        permission
    ) == PackageManager.PERMISSION_GRANTED

private fun Activity.shouldShowLocationPermissionsRationale() =
    shouldShowRationale(LOCATION_PERMISSIONS)

private fun Activity.shouldShowRationale(permissions: Array<String>) =
    permissions.any { shouldShowRationale(it) }

private fun Activity.shouldShowRationale(permission: String) =
    ActivityCompat.shouldShowRequestPermissionRationale(this, permission)

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
)
