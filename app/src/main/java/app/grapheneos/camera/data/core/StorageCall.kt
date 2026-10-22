package app.grapheneos.camera.data.core

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runStorageCall(
    dispatcher: CoroutineDispatcher,
    tag: String,
    block: () -> T,
): Result<T> {
    return withContext(dispatcher) {
        try {
            Result.success(block())
        } catch (exception: Exception) {
            Log.w(tag, "the storage refused the request", exception)

            Result.failure(exception)
        }
    }
}
