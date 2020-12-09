package com.freelapp.libs.locationfetcher.impl.util

import kotlin.reflect.KProperty

internal interface EraseWhenRead<T> {
    var value: T?
}

internal fun <T> eraseWhenRead() = EraseWhenReadImpl<T>()

internal class EraseWhenReadImpl<T>(override var value: T? = null) : EraseWhenRead<T>

internal operator fun <T> EraseWhenRead<T>.getValue(thisRef: Any?, property: KProperty<*>): T? {
    val stored = value
    value = null
    return stored
}

internal operator fun <T> EraseWhenRead<T>.setValue(thisRef: Any?, property: KProperty<*>, newValue: T?) {
    require(!(value != null && newValue != null)) {
        """
        Value can't be set from non-null to non-null, as it means losing a continuation before resuming.
        value: $value; newValue: $newValue"
        """.trimIndent()
    }
    value = newValue
}
