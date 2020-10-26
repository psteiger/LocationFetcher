package com.freelapp.libs.locationfetcher.impl.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PermissionChecker(
    private val context: Context,
    private val permissions: Array<String>
) {
    suspend fun hasPermissions(): Boolean = context.hasPermissions(permissions)
}

suspend fun Context.hasPermissions(permissions: Array<String>): Boolean =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true
        } else {
            permissions.all {
                ActivityCompat.checkSelfPermission(
                    this@hasPermissions,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
