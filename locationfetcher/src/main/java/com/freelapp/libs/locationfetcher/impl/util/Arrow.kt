package com.freelapp.libs.locationfetcher.impl.util

import arrow.core.None
import arrow.core.invalidNel
import arrow.core.validNel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.mapLatest

internal fun <T> SharedFlow<Boolean>.asValidatedNelFlow(invalid: T) = mapLatest {
    if (it) None.validNel()
    else invalid.invalidNel()
}