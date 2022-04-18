package com.freelapp.libs.locationfetcher.impl.util

import arrow.core.*
import kotlinx.coroutines.flow.*

internal fun <T> Flow<Boolean>.asValidatedNelFlow(invalid: T) = mapLatest {
    if (it) None.validNel()
    else invalid.invalidNel()
}

internal inline fun <E, A, R> Flow<ValidatedNel<E, A>>.flatMapLatestRight(
    crossinline transform: (A) -> Flow<R>
) = flatMapLatest { validatedNel ->
    validatedNel.fold(
        { e -> flowOf(e.invalid()) },
        { a -> transform(a).map { it.validNel() }}
    )
}