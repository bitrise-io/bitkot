package io.bitrise.bitkot.utils

import java.util.concurrent.atomic.AtomicBoolean

class Once {
    private val done = AtomicBoolean()
    fun doOnce(task: Runnable) = task
        .takeIf { done.compareAndSet(false, true) }
        ?.run()
}