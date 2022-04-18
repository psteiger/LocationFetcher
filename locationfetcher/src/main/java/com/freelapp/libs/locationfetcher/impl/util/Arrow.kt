package com.freelapp.libs.locationfetcher.impl.util

import arrow.core.*
import kotlinx.coroutines.flow.*

internal fun <T> Flow<Boolean>.asValidatedNelFlow(invalid: T) = mapLatest {
    if (it) None.validNel()
    else invalid.invalidNel()
}

internal inline fun <A, B, R> Flow<Either<A, B>>.flatMapLatestRight(
    crossinline transform: (B) -> Flow<R>
) = flatMapLatest { either ->
    either.fold(
        { e -> flowOf(e.left()) },
        { a -> transform(a).map { it.right() }}
    )
}