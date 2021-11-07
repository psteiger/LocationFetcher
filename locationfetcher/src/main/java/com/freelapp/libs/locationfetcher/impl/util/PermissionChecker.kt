package com.freelapp.libs.locationfetcher.impl.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

internal fun Context.hasPermissions(
    permissions: Array<String>
): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true else permissions.all {
        ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
