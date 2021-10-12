package com.freelapp.libs.locationfetcher.impl.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal inline fun <reified T : LifecycleOwner> T.onLifecycleStateAtLeast(
    state: Lifecycle.State,
    noinline onEnter: (T) -> Unit,
    noinline onLeave: (T) -> Unit
) {
    val enterEvent = Lifecycle.Event.upTo(state)
    val leaveEvent = Lifecycle.Event.downFrom(state)
    val observer = LifecycleEventObserver { owner, event ->
        if (owner !is T) return@LifecycleEventObserver
        when (event) {
            enterEvent -> onEnter(owner)
            leaveEvent -> onLeave(owner)
            else -> {}
        }
    }
    lifecycle.addObserver(observer)
}

internal inline fun <reified T : LifecycleOwner, U> T.lifecycleMutableStateFlow(
    state: Lifecycle.State,
    noinline valueFactory: (T) -> U
): MutableStateFlow<U?> =
    MutableStateFlow<U?>(null).also { mutableStateFlow ->
        onLifecycleStateAtLeast(
            state = state,
            onEnter = { mutableStateFlow.value = valueFactory(it) },
            onLeave = { mutableStateFlow.value = null }
        )
    }

internal inline fun <CLASS, reified T : LifecycleOwner, U> T.lifecycle(
    state: Lifecycle.State,
    noinline valueFactory: (T) -> U
): ReadOnlyProperty<CLASS, U?> =
    object : ReadOnlyProperty<CLASS, U?> {
        var value: U? = null

        init {
            onLifecycleStateAtLeast(
                state = state,
                onEnter = { value = valueFactory(it) },
                onLeave = { value = null }
            )
        }

        override fun getValue(thisRef: CLASS, property: KProperty<*>): U? {
            return value
        }
    }