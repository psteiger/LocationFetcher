package com.freelapp.libs.locationservice

import java.lang.ref.WeakReference

fun <T> MutableSet<WeakReference<T>>.addWeakRef(item: T): Boolean {
    forEach {
        if (it.get() == item)
            return false
    }

    return add(WeakReference(item))
}

fun <T> MutableSet<WeakReference<T>>.removeWeakRef(item: T): Boolean {
    var weakRefToRemove: WeakReference<T>? = null

    forEach {
        if (it.get() == item)
            weakRefToRemove = it
    }

    weakRefToRemove?.let {
        return remove(it)
    }

    return false
}