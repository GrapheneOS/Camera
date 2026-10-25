package app.grapheneos.camera.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

internal suspend fun <T> CoroutineScope.asyncAndAwait(
    dispatcher: CoroutineDispatcher,
    block: suspend () -> T,
): T {
    return async(dispatcher) { block() }.await()
}
