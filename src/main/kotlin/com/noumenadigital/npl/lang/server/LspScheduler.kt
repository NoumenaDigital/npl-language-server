package com.noumenadigital.npl.lang.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LspScheduler(
    private val delayMs: Long,
) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val tasks = ConcurrentHashMap<String, ScheduledFuture<*>>()

    fun submit(
        key: String,
        action: () -> Unit,
    ) {
        // cancel any previously scheduled task for this key
        tasks[key]?.cancel(false)

        val future =
            scheduler.schedule({
                try {
                    action()
                } finally {
                    tasks.remove(key) // cleanup
                }
            }, delayMs, TimeUnit.MILLISECONDS)

        tasks[key] = future
    }

    fun shutdown() {
        scheduler.shutdownNow()
    }
}
