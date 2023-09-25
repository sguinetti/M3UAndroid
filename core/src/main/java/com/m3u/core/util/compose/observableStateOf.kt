package com.m3u.core.util.compose

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.StateFactoryMarker

@StateFactoryMarker
fun <T> observableStateOf(
    value: T,
    onChanged: (T) -> Unit
): MutableState<T> {
    val delegate = mutableStateOf(value)
    return ObservableState(value, delegate, onChanged)
}

private class ObservableState<T>(
    defaultValue: T,
    private val delegate: MutableState<T>,
    private val onChanged: (T) -> Unit
) : MutableState<T> {
    override var value: T = defaultValue
        get() = delegate.value
        set(value) {
            field = value
            onChanged(value)
        }

    override fun component1(): T = delegate.component1()
    override fun component2(): (T) -> Unit = delegate.component2()
}
