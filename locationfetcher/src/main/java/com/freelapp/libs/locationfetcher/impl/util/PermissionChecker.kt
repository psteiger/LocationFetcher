package com.freelapp.libs.locationfetcher.impl.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun Context.hasPermissions(
    permissions: Array<String>
): Boolean =
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
