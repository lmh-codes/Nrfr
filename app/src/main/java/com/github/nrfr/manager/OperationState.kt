package com.github.nrfr.manager

import java.util.concurrent.atomic.AtomicBoolean

object OperationState {
    private val applying = AtomicBoolean(false)

    fun begin(): Boolean = applying.compareAndSet(false, true)

    fun end() {
        applying.set(false)
    }

    fun reset() {
        applying.set(false)
    }

    fun isRunning(): Boolean = applying.get()
}
