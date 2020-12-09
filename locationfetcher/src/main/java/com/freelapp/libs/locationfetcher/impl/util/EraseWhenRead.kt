package com.freelapp.libs.locationfetcher.impl.util

import kotlin.reflect.KProperty

interface EraseWhenRead<T> {
    var value: T?
}

fun <T> eraseWhenRead() = EraseWhenReadImpl<T>()

class EraseWhenReadImpl<T>(override var value: T? = null) : EraseWhenRead<T>

operator fun <T> EraseWhenRead<T>.getValue(thisRef: Any?, property: KProperty<*>): T? {
    val stored = value
    value = null
    return stored
}

operator fun <T> EraseWhenRead<T>.setValue(thisRef: Any?, property: KProperty<*>, newValue: T?) {
    require(!(value != null && newValue != null)) {
        """
        Value can't be set from non-null to non-null, as it means losing a continuation before resuming.
        value: $value; newValue: $newValue"
        """.trimIndent()
    }
    value = newValue
}