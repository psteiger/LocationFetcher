package com.freelapp.libs.locationfetcher.impl.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

inline fun <reified T : LifecycleOwner> T.onLifecycleStateAtLeast(
    state: Lifecycle.State,
    crossinline onEnter: (T) -> Unit,
    crossinline onLeave: (T) -> Unit
) {
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            if (state == Lifecycle.State.CREATED && owner is T) onEnter(owner)
        }

        override fun onStart(owner: LifecycleOwner) {
            if (state == Lifecycle.State.STARTED && owner is T) onEnter(owner)
        }

        override fun onResume(owner: LifecycleOwner) {
            if (state == Lifecycle.State.RESUMED && owner is T) onEnter(owner)
        }

        override fun onPause(owner: LifecycleOwner) {
            if (state == Lifecycle.State.RESUMED && owner is T) onLeave(owner)
        }

        override fun onStop(owner: LifecycleOwner) {
            if (state == Lifecycle.State.STARTED && owner is T) onLeave(owner)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            if (state == Lifecycle.State.CREATED && owner is T) onLeave(owner)
        }
    })
}

inline fun <reified T : LifecycleOwner, U> T.lifecycleMutableStateFlow(
    state: Lifecycle.State,
    crossinline valueFactory: (T) -> U
): MutableStateFlow<U?> =
    MutableStateFlow<U?>(null).also { mutableStateFlow ->
        onLifecycleStateAtLeast(
            state = state,
            onEnter = { mutableStateFlow.value = valueFactory(it) },
            onLeave = { mutableStateFlow.value = null }
        )
    }

inline fun <CLASS, reified T : LifecycleOwner, U> T.lifecycle(
    state: Lifecycle.State,
    crossinline valueFactory: (T) -> U
): ReadOnlyProperty<CLASS, U?> =
    object : ReadOnlyProperty<CLASS, U?>, DefaultLifecycleObserver {
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