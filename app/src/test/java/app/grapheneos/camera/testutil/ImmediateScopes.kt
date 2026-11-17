package app.grapheneos.camera.testutil

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Stands in for `viewModelScope`, whose `Dispatchers.Main.immediate` starts work at once. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.immediateScope(): CoroutineScope {
    return backgroundScope + UnconfinedTestDispatcher(testScheduler)
}
