package com.freelapp.libs.locationfetcher.impl.util

import android.app.Activity
import com.freelapp.libs.locationfetcher.LocationFetcher

internal fun Boolean?.asPermissionStatus(): LocationFetcher.PermissionStatus = when (this) {
    null -> LocationFetcher.PermissionStatus.UNKNOWN
    false -> LocationFetcher.PermissionStatus.DENIED
    true -> LocationFetcher.PermissionStatus.ALLOWED
}

internal fun Boolean?.asSettingsStatus(): LocationFetcher.SettingsStatus = when (this) {
    null -> LocationFetcher.SettingsStatus.UNKNOWN
    false -> LocationFetcher.SettingsStatus.DISABLED
    true -> LocationFetcher.SettingsStatus.ENABLED
}

internal typealias ResultCode = Int

internal fun ResultCode.asSettingsStatus(): LocationFetcher.SettingsStatus = when (this) {
    Activity.RESULT_OK -> LocationFetcher.SettingsStatus.ENABLED
    Activity.RESULT_CANCELED -> LocationFetcher.SettingsStatus.DISABLED
    else -> LocationFetcher.SettingsStatus.DISABLED
}

internal infix fun LocationFetcher.PermissionStatus.or(
    other: LocationFetcher.PermissionStatus
): LocationFetcher.PermissionStatus =
    if (this == LocationFetcher.PermissionStatus.ALLOWED ||
        other == LocationFetcher.PermissionStatus.ALLOWED
    ) {
        LocationFetcher.PermissionStatus.ALLOWED
    } else if (
        this == LocationFetcher.PermissionStatus.DENIED ||
        other == LocationFetcher.PermissionStatus.DENIED
    ) {
        LocationFetcher.PermissionStatus.DENIED
    } else LocationFetcher.PermissionStatus.UNKNOWN

internal infix fun LocationFetcher.PermissionStatus.and(
    other: LocationFetcher.PermissionStatus
): LocationFetcher.PermissionStatus =
    if (this == LocationFetcher.PermissionStatus.ALLOWED &&
        other == LocationFetcher.PermissionStatus.ALLOWED
    ) {
        LocationFetcher.PermissionStatus.ALLOWED
    } else if (
        this == LocationFetcher.PermissionStatus.UNKNOWN &&
        other == LocationFetcher.PermissionStatus.UNKNOWN
    ) {
        LocationFetcher.PermissionStatus.UNKNOWN
    } else LocationFetcher.PermissionStatus.DENIED

internal infix fun LocationFetcher.SettingsStatus.or(
    other: LocationFetcher.SettingsStatus
): LocationFetcher.SettingsStatus =
    if (this == LocationFetcher.SettingsStatus.ENABLED ||
        other == LocationFetcher.SettingsStatus.ENABLED
    ) {
        LocationFetcher.SettingsStatus.ENABLED
    } else if (
        this == LocationFetcher.SettingsStatus.DISABLED ||
        other == LocationFetcher.SettingsStatus.DISABLED
    ) {
        LocationFetcher.SettingsStatus.DISABLED
    } else LocationFetcher.SettingsStatus.UNKNOWN

internal infix fun LocationFetcher.SettingsStatus.and(
    other: LocationFetcher.SettingsStatus
): LocationFetcher.SettingsStatus =
    if (this == LocationFetcher.SettingsStatus.ENABLED &&
        other == LocationFetcher.SettingsStatus.ENABLED
    ) {
        LocationFetcher.SettingsStatus.ENABLED
    } else if (
        this == LocationFetcher.SettingsStatus.UNKNOWN &&
        other == LocationFetcher.SettingsStatus.UNKNOWN
    ) {
        LocationFetcher.SettingsStatus.UNKNOWN
    } else LocationFetcher.SettingsStatus.DISABLED
