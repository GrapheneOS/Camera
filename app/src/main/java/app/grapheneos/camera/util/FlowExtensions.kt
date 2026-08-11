package app.grapheneos.camera.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

inline fun unitFlow(
    crossinline block: suspend FlowCollector<Unit>.() -> Unit,
): Flow<Unit> {
    return flow {
        block()
        emit(Unit)
    }
}
