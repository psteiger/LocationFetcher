package com.freelapp.locationfetcher.compose

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

@Composable
internal fun PermissionRequest(
    hasPermission: Boolean,
    onResult: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.getActivity()

    val requester = rememberPermissionRequester {
        onResult(it.values.all { true })
    }
    val showRationale = activity?.shouldShowRationale() == true
    var rationaleShown by remember { mutableStateOf(false) }
    if (!hasPermission) {
        if (showRationale && !rationaleShown) {
            AlertDialog(
                onDismissRequest = { rationaleShown = true },
                confirmButton = {
                    Button(onClick = { rationaleShown = true }) {
                        Text(text = "Ok")
                    }
                }
            )
        } else {
            SideEffect {
                requester.launch(LOCATION_PERMISSIONS)
            }
        }
    }
}

internal fun Context.checkLocationPermissionsAllowed() = hasPermissions(LOCATION_PERMISSIONS)

private typealias PermissionRequester = ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>

@Composable
private fun rememberPermissionRequester(
    onResult: (Map<String, Boolean>) -> Unit
): PermissionRequester = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
    onResult
)

private fun Context.hasPermissions(
    permissions: Array<String>
): Boolean = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
    else -> permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Activity.shouldShowRationale(permission: String) =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            shouldShowRequestPermissionRationale(permission)
        else -> false
    }

private fun Activity.shouldShowRationale() = LOCATION_PERMISSIONS.any {
    shouldShowRationale(it)
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
)
