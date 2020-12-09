package com.freelapp.libs.locationfetcher.impl.singleton

import android.location.Location
import com.freelapp.libs.locationfetcher.LocationFetcher
import kotlinx.coroutines.flow.MutableStateFlow

internal object GlobalState {
    val LOCATION = MutableStateFlow<Location?>(null)
    val PERMISSION_STATUS = MutableStateFlow(LocationFetcher.PermissionStatus.UNKNOWN)
    val SETTINGS_STATUS = MutableStateFlow(LocationFetcher.SettingsStatus.UNKNOWN)
}
