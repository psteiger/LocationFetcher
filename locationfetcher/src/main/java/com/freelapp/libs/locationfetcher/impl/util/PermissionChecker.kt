package com.freelapp.libs.locationfetcher.impl.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.freelapp.libs.locationfetcher.LocationFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PermissionChecker(
    context: Context,
    private val permissions: Array<String>,
    private val applicationContext: Context = context.applicationContext
) {
    suspend fun hasPermissions(): LocationFetcher.PermissionStatus =
        applicationContext.hasPermissions(permissions)
}

internal suspend fun Context.hasPermissions(
    permissions: Array<String>
): LocationFetcher.PermissionStatus =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            LocationFetcher.PermissionStatus.ALLOWED
        } else {
            permissions.all {
                ActivityCompat.checkSelfPermission(
                    this@hasPermissions,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }.asPermissionStatus()
        }
    }
