package com.freelapp.libs.locationfetcher.impl.singleton

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

internal const val TAG = "LocationFetcher"

internal val PERMISSION_STATUS = ConflatedFlow<Boolean>()
internal val SETTINGS_STATUS = ConflatedFlow<Boolean>()

@Suppress("FunctionName")
private fun <T> ConflatedFlow() = MutableSharedFlow<T>(
    1,
    0,
    BufferOverflow.DROP_OLDEST
)
