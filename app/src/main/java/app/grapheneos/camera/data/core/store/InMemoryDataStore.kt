package app.grapheneos.camera.data.core.store

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryDataStore<T>(
    initial: T,
) : DataStore<T> {

    private val stored = MutableStateFlow(initial)

    override val data: Flow<T> = stored.asStateFlow()

    private val writes = Mutex()

    override suspend fun updateData(transform: suspend (T) -> T): T {
        return writes.withLock {
            val updated = transform(stored.value)
            stored.value = updated
            updated
        }
    }
}
