package com.freelapp.libs.locationfetcher.impl.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.freelapp.libs.locationfetcher.LocationFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PermissionChecker(
    private val context: Context,
    private val permissions: Array<String>
) {
    suspend fun hasPermissions(): LocationFetcher.PermissionStatus =
        context.hasPermissions(permissions)
}

internal suspend fun Context.hasPermissions(permissions: Array<String>): LocationFetcher.PermissionStatus =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            LocationFetcher.PermissionStatus.ALLOWED
        } else {
            permissions.all {
                (ActivityCompat.checkSelfPermission(
                    this@hasPermissions,
                    it
                ) == PackageManager.PERMISSION_GRANTED)
            }.asPermissionStatus()
        }
    }


