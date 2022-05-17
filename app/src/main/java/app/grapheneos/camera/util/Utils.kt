package app.grapheneos.camera.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

fun ExecutorService.executeIfAlive(r: Runnable) {
    try {
        execute(r)
    } catch (ignored: RejectedExecutionException) {
        check(this.isShutdown)
    }
}
