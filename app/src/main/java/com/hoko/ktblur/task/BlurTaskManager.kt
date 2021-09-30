package com.hoko.ktblur.task

import kotlinx.coroutines.*
import java.util.concurrent.Executors

object BlurTaskManager {
    private val TAG: String = BlurTaskManager::class.java.simpleName
    val WORKER_THREADS_COUNT: Int = if (Runtime.getRuntime().availableProcessors() <= 3) 1 else Runtime.getRuntime().availableProcessors() / 2
    private val BLUR_DISPATCHER = Executors.newFixedThreadPool(WORKER_THREADS_COUNT).asCoroutineDispatcher() + CoroutineName("async_blur")

    fun submit(block: suspend () -> Unit): Job {
        return CoroutineScope(BLUR_DISPATCHER).launch {
            try {
                block()
            } catch (ignored: CancellationException) {
                //do nothing
            }
        }
    }

    fun invokeAll(tasks: Collection<BlurSubTask>) = runBlocking {
        if (tasks.isEmpty()) {
            return@runBlocking
        }
        withContext(BLUR_DISPATCHER) {
            val blurDefers = mutableListOf<Deferred<Unit>>()
            for (task in tasks) {
                blurDefers.add(async { task.run() })
            }
            for (defer in blurDefers) {
                defer.await()
            }
        }
    }
}