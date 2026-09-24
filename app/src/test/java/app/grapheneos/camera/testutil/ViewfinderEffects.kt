package app.grapheneos.camera.testutil

import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.collectEffects(
    stateHolder: ViewfinderStateHolder,
): MutableList<ViewfinderScreenEffect> {
    val effects = mutableListOf<ViewfinderScreenEffect>()

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        stateHolder.effects.collect { effects += it }
    }

    return effects
}
